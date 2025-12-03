package com.hsbc.fraud.detection.messaging.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hsbc.fraud.detection.disruptor.DisruptorService;
import com.hsbc.fraud.detection.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LocalMessageConsumer.
 * Verifies that messages are correctly parsed and published to Disruptor.
 */
@ActiveProfiles("local")
@DisplayName("LocalMessageConsumer Tests")
@ExtendWith(MockitoExtension.class)
@Isolated("Uses shared static message queue and threading operations")
@Execution(ExecutionMode.SAME_THREAD)
class LocalMessageConsumerTest {
    
    @Mock
    private DisruptorService disruptorService;
    
    private ObjectMapper objectMapper;
    private LocalMessageConsumer consumer;
    private BlockingQueue<String> testQueue;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        testQueue = new LinkedBlockingQueue<>();
        consumer = new LocalMessageConsumer(objectMapper, disruptorService, testQueue);
        
        // Clear the queue before each test
        testQueue.clear();
    }
    
    @AfterEach
    void tearDown() {
        // Stop consumer if still running to avoid thread leaks and test interference
        if (consumer != null && consumer.isRunning()) {
            consumer.stopListening();
            // Wait for consumer to fully stop
            await().atMost(2, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> assertThat(consumer.isRunning()).isFalse());
        }
        // Clear the queue after each test
        testQueue.clear();
    }
    
    @Test
    @DisplayName("Should parse valid JSON and publish to Disruptor")
    void shouldParseValidJsonAndPublishToDisruptor() throws Exception {
        // Given
        Transaction transaction = createTransaction("TXN-001", new BigDecimal("100.00"), "ACCT123");
        String message = objectMapper.writeValueAsString(transaction);
        
        // When
        consumer.processMessage(message);
        
        // Then
        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(disruptorService).publishEvent(txnCaptor.capture(), isNull());
        
        Transaction captured = txnCaptor.getValue();
        assertThat(captured.getTransactionId()).isEqualTo("TXN-001");
        assertThat(captured.getAccountId()).isEqualTo("ACCT123");
        assertThat(captured.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
    
    @Test
    @DisplayName("Should handle invalid JSON gracefully without publishing to Disruptor")
    void shouldHandleInvalidJsonGracefully() {
        // Given
        String invalidJson = "{invalid json}";
        
        // When
        consumer.processMessage(invalidJson);
        
        // Then
        verify(disruptorService, never()).publishEvent(any(), any());
    }
    
    @Test
    @DisplayName("Should handle ring buffer full scenario gracefully")
    void shouldHandleRingBufferFullGracefully() throws Exception {
        // Given
        Transaction transaction = createTransaction("TXN-002", new BigDecimal("500.00"), "ACCT456");
        String message = objectMapper.writeValueAsString(transaction);
        
        doThrow(new IllegalStateException("Ring buffer is full"))
                .when(disruptorService).publishEvent(any(), any());
        
        // When - should not throw
        consumer.processMessage(message);
        
        // Then
        verify(disruptorService).publishEvent(any(), isNull());
    }
    
    @Test
    @DisplayName("Should handle unexpected exception gracefully")
    void shouldHandleUnexpectedExceptionGracefully() throws Exception {
        // Given
        Transaction transaction = createTransaction("TXN-003", new BigDecimal("200.00"), "ACCT789");
        String message = objectMapper.writeValueAsString(transaction);
        
        doThrow(new RuntimeException("Unexpected error"))
                .when(disruptorService).publishEvent(any(), any());
        
        // When - should not throw
        consumer.processMessage(message);
        
        // Then
        verify(disruptorService).publishEvent(any(), isNull());
    }
    
    @Test
    @DisplayName("Should start and stop consumer thread correctly")
    void shouldStartAndStopConsumerThread() {
        // When
        consumer.startListening();
        
        // Then
        assertThat(consumer.isRunning()).isTrue();
        
        // When
        consumer.stopListening();
        
        // Then
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(consumer.isRunning()).isFalse());
    }
    
    @Test
    @DisplayName("Should not start multiple consumer threads")
    void shouldNotStartMultipleConsumerThreads() {
        // When
        consumer.startListening();
        consumer.startListening(); // Second call should be no-op
        
        // Then
        assertThat(consumer.isRunning()).isTrue();
        
        // Cleanup
        consumer.stopListening();
    }
    
    @Test
    @DisplayName("Should consume messages from shared queue and publish to Disruptor")
    void shouldConsumeMessagesFromQueueAndPublishToDisruptor() throws Exception {
        // Given
        Transaction transaction = createTransaction("TXN-004", new BigDecimal("300.00"), "ACCT111");
        String message = objectMapper.writeValueAsString(transaction);
        
        // Clear the queue to avoid interference from other tests
        // Drain any existing messages that might be left over
        testQueue.clear();
        
        // Reset mock to ensure clean state
        reset(disruptorService);
        
        // Start consumer first to ensure it's ready
        consumer.startListening();
        
        // Wait for consumer to be running and give it time to reach the blocking take() call
        await().atMost(2, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(consumer.isRunning()).isTrue());
        
        // Add small delay to ensure consumer thread reaches blocking state
        // This is more reliable than the previous approach
        await().pollDelay(200, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> true);
        
        // When - add message to queue after consumer is waiting
        testQueue.put(message);
        
        // Then - wait for processing
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    verify(disruptorService, atLeastOnce()).publishEvent(any(Transaction.class), isNull());
                });
        
        // Verify transaction details
        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(disruptorService, atLeastOnce()).publishEvent(txnCaptor.capture(), isNull());
        
        Transaction captured = txnCaptor.getValue();
        assertThat(captured.getTransactionId()).isEqualTo("TXN-004");
        assertThat(captured.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(captured.getAccountId()).isEqualTo("ACCT111");
        
        // Cleanup
        consumer.stopListening();
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(consumer.isRunning()).isFalse());
    }
    
    @Test
    @DisplayName("Should pass null acknowledgement to Disruptor for local messages")
    void shouldPassNullAcknowledgementToDisruptor() throws Exception {
        // Given
        Transaction transaction = createTransaction("TXN-005", new BigDecimal("150.00"), "ACCT222");
        String message = objectMapper.writeValueAsString(transaction);
        
        // When
        consumer.processMessage(message);
        
        // Then - verify null acknowledgement is passed
        verify(disruptorService).publishEvent(any(Transaction.class), isNull());
    }
    
    @Test
    @DisplayName("Should handle multiple messages correctly")
    void shouldHandleMultipleMessagesCorrectly() throws Exception {
        // Given
        Transaction txn1 = createTransaction("TXN-A", new BigDecimal("100.00"), "ACCT-A");
        Transaction txn2 = createTransaction("TXN-B", new BigDecimal("200.00"), "ACCT-B");
        Transaction txn3 = createTransaction("TXN-C", new BigDecimal("300.00"), "ACCT-C");
        
        // When
        consumer.processMessage(objectMapper.writeValueAsString(txn1));
        consumer.processMessage(objectMapper.writeValueAsString(txn2));
        consumer.processMessage(objectMapper.writeValueAsString(txn3));
        
        // Then
        verify(disruptorService, times(3)).publishEvent(any(), isNull());
    }
    
    private Transaction createTransaction(String txnId, BigDecimal amount, String accountId) {
        return Transaction.builder()
                .transactionId(txnId)
                .accountId(accountId)
                .amount(amount)
                .currency("USD")
                .timestamp(Instant.now())
                .merchantId("MERCHANT-123")
                .merchantCategory("RETAIL")
                .location("New York, NY")
                .type(Transaction.TransactionType.PURCHASE)
                .build();
    }
}

