package com.hsbc.fraud.detection.disruptor;

import com.hsbc.fraud.detection.model.Transaction;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("DisruptorService Unit Tests")
@ExtendWith(MockitoExtension.class)
class DisruptorServiceTest {

    @Mock
    private Disruptor<TransactionEvent> disruptor;

    @Mock
    private RingBuffer<TransactionEvent> ringBuffer;

    @Mock
    private Acknowledgement acknowledgement;

    private MeterRegistry meterRegistry;
    private DisruptorService disruptorService;
    private static final int SHUTDOWN_TIMEOUT = 30;
    private static final long BUFFER_SIZE = 1024;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        
        // Configure mock behavior
        when(disruptor.getRingBuffer()).thenReturn(ringBuffer);
        when(ringBuffer.getBufferSize()).thenReturn((int)BUFFER_SIZE);
    }

    @Test
    @DisplayName("Should initialize DisruptorService and start disruptor")
    void testConstructorInitialization() {
        // When
        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // Then
        verify(disruptor).start();
        verify(disruptor).getRingBuffer();
        
        // Verify metrics are registered
        assertNotNull(meterRegistry.find("disruptor.events.published").counter());
        assertNotNull(meterRegistry.find("disruptor.events.publish.failed").counter());
    }

    @Test
    @DisplayName("Should successfully publish event to ring buffer")
    void testPublishEventSuccess() {
        // Given
        Transaction transaction = createTestTransaction("TXN001");
        TransactionEvent event = new TransactionEvent();
        long sequence = 100L;

        when(ringBuffer.next()).thenReturn(sequence);
        when(ringBuffer.get(sequence)).thenReturn(event);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        disruptorService.publishEvent(transaction, acknowledgement);

        // Then
        verify(ringBuffer).next();
        verify(ringBuffer).get(sequence);
        verify(ringBuffer).publish(sequence);

        assertEquals(transaction, event.getTransaction());
        assertEquals(acknowledgement, event.getAcknowledgement());
        assertEquals(sequence, event.getSequence());
        assertTrue(event.getPublishTimestamp() > 0);

        // Verify published counter was incremented
        Counter publishedCounter = meterRegistry.find("disruptor.events.published").counter();
        assertNotNull(publishedCounter);
        assertEquals(1.0, publishedCounter.count());
    }

    @Test
    @DisplayName("Should increment published counter for multiple events")
    void testPublishMultipleEventsSuccessfully() {
        // Given
        TransactionEvent event1 = new TransactionEvent();
        TransactionEvent event2 = new TransactionEvent();
        TransactionEvent event3 = new TransactionEvent();

        when(ringBuffer.next()).thenReturn(1L, 2L, 3L);
        when(ringBuffer.get(1L)).thenReturn(event1);
        when(ringBuffer.get(2L)).thenReturn(event2);
        when(ringBuffer.get(3L)).thenReturn(event3);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        disruptorService.publishEvent(createTestTransaction("TXN001"), acknowledgement);
        disruptorService.publishEvent(createTestTransaction("TXN002"), acknowledgement);
        disruptorService.publishEvent(createTestTransaction("TXN003"), acknowledgement);

        // Then
        verify(ringBuffer, times(3)).next();
        verify(ringBuffer, times(3)).publish(anyLong());

        Counter publishedCounter = meterRegistry.find("disruptor.events.published").counter();
        assertNotNull(publishedCounter);
        assertEquals(3.0, publishedCounter.count());
    }

    @Test
    @DisplayName("Should handle ring buffer full exception")
    void testPublishEventWhenRingBufferFull() {
        // Given
        Transaction transaction = createTestTransaction("TXN001");
        
        when(ringBuffer.next()).thenThrow(new RuntimeException("Ring buffer is full"));

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When/Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            disruptorService.publishEvent(transaction, acknowledgement);
        });

        assertTrue(exception.getMessage().contains("Ring buffer is full or unavailable"));
        verify(ringBuffer).next();
        verify(ringBuffer, never()).publish(anyLong());

        // Verify failed counter was incremented
        Counter failedCounter = meterRegistry.find("disruptor.events.publish.failed").counter();
        assertNotNull(failedCounter);
        assertEquals(1.0, failedCounter.count());
    }

    @Test
    @DisplayName("Should handle exception during event population")
    void testPublishEventWithExceptionDuringPopulation() {
        // Given
        Transaction transaction = createTestTransaction("TXN001");
        long sequence = 100L;

        when(ringBuffer.next()).thenReturn(sequence);
        when(ringBuffer.get(sequence)).thenThrow(new RuntimeException("Event population failed"));

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When/Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            disruptorService.publishEvent(transaction, acknowledgement);
        });

        assertTrue(exception.getMessage().contains("Ring buffer is full or unavailable"));
        verify(ringBuffer).next();
        verify(ringBuffer).publish(sequence); // Still publishes in finally block

        // Verify failed counter was incremented
        Counter failedCounter = meterRegistry.find("disruptor.events.publish.failed").counter();
        assertNotNull(failedCounter);
        assertEquals(1.0, failedCounter.count());
    }

    @Test
    @DisplayName("Should always publish sequence even if exception occurs")
    void testPublishEventAlwaysPublishesSequence() {
        // Given
        Transaction transaction = createTestTransaction("TXN001");
        TransactionEvent event = new TransactionEvent();
        long sequence = 100L;

        when(ringBuffer.next()).thenReturn(sequence);
        when(ringBuffer.get(sequence)).thenReturn(event);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        try {
            disruptorService.publishEvent(transaction, acknowledgement);
        } catch (Exception e) {
            // Ignore
        }

        // Then - publish should be called in finally block
        verify(ringBuffer).publish(sequence);
    }

    @Test
    @DisplayName("Should calculate ring buffer utilization correctly when empty")
    void testGetRingBufferUtilizationWhenEmpty() {
        // Given
        when(ringBuffer.remainingCapacity()).thenReturn(BUFFER_SIZE);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        double utilization = disruptorService.getRingBufferUtilization();

        // Then
        assertEquals(0.0, utilization, 0.01);
        verify(ringBuffer, atLeastOnce()).getBufferSize();
        verify(ringBuffer, atLeastOnce()).remainingCapacity();
    }

    @Test
    @DisplayName("Should calculate ring buffer utilization correctly when half full")
    void testGetRingBufferUtilizationWhenHalfFull() {
        // Given
        when(ringBuffer.remainingCapacity()).thenReturn(BUFFER_SIZE / 2);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        double utilization = disruptorService.getRingBufferUtilization();

        // Then
        assertEquals(50.0, utilization, 0.01);
        verify(ringBuffer, atLeastOnce()).getBufferSize();
        verify(ringBuffer, atLeastOnce()).remainingCapacity();
    }

    @Test
    @DisplayName("Should calculate ring buffer utilization correctly when full")
    void testGetRingBufferUtilizationWhenFull() {
        // Given
        when(ringBuffer.remainingCapacity()).thenReturn(0L);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        double utilization = disruptorService.getRingBufferUtilization();

        // Then
        assertEquals(100.0, utilization, 0.01);
        verify(ringBuffer, atLeastOnce()).getBufferSize();
        verify(ringBuffer, atLeastOnce()).remainingCapacity();
    }

    @Test
    @DisplayName("Should calculate ring buffer utilization with various capacity values")
    void testGetRingBufferUtilizationWithVariousValues() {
        // Given
        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // Test 75% utilization
        when(ringBuffer.remainingCapacity()).thenReturn(BUFFER_SIZE / 4);
        assertEquals(75.0, disruptorService.getRingBufferUtilization(), 0.1);

        // Test 25% utilization
        when(ringBuffer.remainingCapacity()).thenReturn(BUFFER_SIZE * 3 / 4);
        assertEquals(25.0, disruptorService.getRingBufferUtilization(), 0.1);

        // Test 90% utilization
        when(ringBuffer.remainingCapacity()).thenReturn((long) (BUFFER_SIZE * 0.1));
        assertEquals(90.0, disruptorService.getRingBufferUtilization(), 0.1);
    }

    @Test
    @DisplayName("Should return correct remaining capacity")
    void testGetRemainingCapacity() {
        // Given
        long remainingCapacity = 512L;
        when(ringBuffer.remainingCapacity()).thenReturn(remainingCapacity);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        long result = disruptorService.getRemainingCapacity();

        // Then
        assertEquals(remainingCapacity, result);
        verify(ringBuffer).remainingCapacity();
    }

    @Test
    @DisplayName("Should return zero when ring buffer is full")
    void testGetRemainingCapacityWhenFull() {
        // Given
        when(ringBuffer.remainingCapacity()).thenReturn(0L);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        long result = disruptorService.getRemainingCapacity();

        // Then
        assertEquals(0L, result);
    }

    @Test
    @DisplayName("Should shutdown gracefully")
    void testShutdownGracefully() throws Exception {
        // Given
        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        disruptorService.shutdown();

        // Then
        verify(disruptor).shutdown(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
        verify(disruptor, never()).halt();
    }

    @Test
    @DisplayName("Should force halt on shutdown exception")
    void testShutdownWithException() throws Exception {
        // Given
        doThrow(new RuntimeException("Shutdown failed")).when(disruptor)
                .shutdown(anyLong(), any(TimeUnit.class));

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        disruptorService.shutdown();

        // Then
        verify(disruptor).shutdown(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
        verify(disruptor).halt();
    }

    @Test
    @DisplayName("Should force halt on timeout during shutdown")
    void testShutdownWithTimeout() throws Exception {
        // Given
        doThrow(new RuntimeException("Shutdown timeout")).when(disruptor)
                .shutdown(anyLong(), any(TimeUnit.class));

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        disruptorService.shutdown();

        // Then
        verify(disruptor).shutdown(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
        verify(disruptor).halt();
    }

    @Test
    @DisplayName("Should handle concurrent publish events correctly")
    void testConcurrentPublishEvents() throws InterruptedException {
        // Given
        int numThreads = 10;
        int eventsPerThread = 10;
        AtomicInteger sequenceCounter = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numThreads);

        when(ringBuffer.next()).thenAnswer(invocation -> 
            (long) sequenceCounter.incrementAndGet());
        when(ringBuffer.get(anyLong())).thenAnswer(invocation -> new TransactionEvent());

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // When - Simulate concurrent publishing
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < eventsPerThread; j++) {
                        Transaction transaction = createTestTransaction("TXN-" + threadId + "-" + j);
                        disruptorService.publishEvent(transaction, acknowledgement);
                    }
                } catch (Exception e) {
                    // Ignore for this test
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        completionLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        Counter publishedCounter = meterRegistry.find("disruptor.events.published").counter();
        assertNotNull(publishedCounter);
        assertEquals(numThreads * eventsPerThread, publishedCounter.count(), 
            "All events should be published successfully");
    }

    @Test
    @DisplayName("Should track both successful and failed publish events")
    void testMixedSuccessAndFailurePublishing() {
        // Given
        TransactionEvent event = new TransactionEvent();
        
        when(ringBuffer.next())
            .thenReturn(1L)  // Success
            .thenThrow(new RuntimeException("Buffer full"))  // Failure
            .thenReturn(2L)  // Success
            .thenThrow(new RuntimeException("Buffer full"))  // Failure
            .thenReturn(3L); // Success

        when(ringBuffer.get(anyLong())).thenReturn(event);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        disruptorService.publishEvent(createTestTransaction("TXN001"), acknowledgement);
        
        try {
            disruptorService.publishEvent(createTestTransaction("TXN002"), acknowledgement);
        } catch (Exception e) {
            // Expected
        }
        
        disruptorService.publishEvent(createTestTransaction("TXN003"), acknowledgement);
        
        try {
            disruptorService.publishEvent(createTestTransaction("TXN004"), acknowledgement);
        } catch (Exception e) {
            // Expected
        }
        
        disruptorService.publishEvent(createTestTransaction("TXN005"), acknowledgement);

        // Then
        Counter publishedCounter = meterRegistry.find("disruptor.events.published").counter();
        Counter failedCounter = meterRegistry.find("disruptor.events.publish.failed").counter();
        
        assertNotNull(publishedCounter);
        assertNotNull(failedCounter);
        assertEquals(3.0, publishedCounter.count(), "Should have 3 successful publishes");
        assertEquals(2.0, failedCounter.count(), "Should have 2 failed publishes");
    }

    @Test
    @DisplayName("Should properly set all event fields when publishing")
    void testEventFieldsAreSetCorrectly() {
        // Given
        Transaction transaction = createTestTransaction("TXN001");
        TransactionEvent event = new TransactionEvent();
        long sequence = 42L;
        long beforeTimestamp = System.currentTimeMillis();

        when(ringBuffer.next()).thenReturn(sequence);
        when(ringBuffer.get(sequence)).thenReturn(event);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        disruptorService.publishEvent(transaction, acknowledgement);
        long afterTimestamp = System.currentTimeMillis();

        // Then
        assertEquals(transaction, event.getTransaction(), "Transaction should be set");
        assertEquals(acknowledgement, event.getAcknowledgement(), "Acknowledgement should be set");
        assertEquals(sequence, event.getSequence(), "Sequence should be set");
        assertTrue(event.getPublishTimestamp() >= beforeTimestamp, 
            "Publish timestamp should be at or after test start");
        assertTrue(event.getPublishTimestamp() <= afterTimestamp, 
            "Publish timestamp should be at or before test end");
    }

    @Test
    @DisplayName("Should handle transaction with null fields gracefully")
    void testPublishEventWithNullTransactionFields() {
        // Given
        Transaction transactionWithNullId = Transaction.builder()
                .transactionId(null)
                .accountId("ACC123")
                .amount(new BigDecimal("100.00"))
                .build();
        TransactionEvent event = new TransactionEvent();
        long sequence = 100L;

        when(ringBuffer.next()).thenReturn(sequence);
        when(ringBuffer.get(sequence)).thenReturn(event);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        disruptorService.publishEvent(transactionWithNullId, acknowledgement);

        // Then
        verify(ringBuffer).publish(sequence);
        assertEquals(transactionWithNullId, event.getTransaction());
        assertEquals(acknowledgement, event.getAcknowledgement());
        
        Counter publishedCounter = meterRegistry.find("disruptor.events.published").counter();
        assertEquals(1.0, publishedCounter.count());
    }

    @Test
    @DisplayName("Should handle null acknowledgement gracefully")
    void testPublishEventWithNullAcknowledgement() {
        // Given
        Transaction transaction = createTestTransaction("TXN001");
        TransactionEvent event = new TransactionEvent();
        long sequence = 100L;

        when(ringBuffer.next()).thenReturn(sequence);
        when(ringBuffer.get(sequence)).thenReturn(event);

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        disruptorService.publishEvent(transaction, null);

        // Then
        verify(ringBuffer).publish(sequence);
        assertEquals(transaction, event.getTransaction());
        assertNull(event.getAcknowledgement());
        
        Counter publishedCounter = meterRegistry.find("disruptor.events.published").counter();
        assertEquals(1.0, publishedCounter.count());
    }

    @Test
    @DisplayName("Should use configured shutdown timeout")
    void testShutdownUsesConfiguredTimeout() throws Exception {
        // Given
        int customTimeout = 60;
        disruptorService = new DisruptorService(disruptor, meterRegistry, customTimeout);

        // When
        disruptorService.shutdown();

        // Then
        verify(disruptor).shutdown(eq((long) customTimeout), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should handle very high ring buffer utilization")
    void testHighRingBufferUtilization() {
        // Given
        when(ringBuffer.remainingCapacity()).thenReturn(1L); // Only 1 slot remaining

        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // When
        double utilization = disruptorService.getRingBufferUtilization();

        // Then
        assertTrue(utilization > 99.0, "Utilization should be over 99%");
        assertTrue(utilization <= 100.0, "Utilization should not exceed 100%");
    }

    @Test
    @DisplayName("Should verify disruptor start is called exactly once")
    void testDisruptorStartCalledOnce() {
        // When
        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // Then
        verify(disruptor, times(1)).start();
    }

    @Test
    @DisplayName("Should register exactly two metrics counters")
    void testMetricsRegistration() {
        // When
        disruptorService = new DisruptorService(disruptor, meterRegistry, SHUTDOWN_TIMEOUT);

        // Then
        Counter publishedCounter = meterRegistry.find("disruptor.events.published").counter();
        Counter failedCounter = meterRegistry.find("disruptor.events.publish.failed").counter();

        assertNotNull(publishedCounter, "Published counter should be registered");
        assertNotNull(failedCounter, "Failed counter should be registered");
        assertEquals("Number of events published to ring buffer", 
            publishedCounter.getId().getDescription());
        assertEquals("Number of events that failed to publish to ring buffer", 
            failedCounter.getId().getDescription());
    }

    // Helper method to create test transactions
    private Transaction createTestTransaction(String transactionId) {
        return Transaction.builder()
                .transactionId(transactionId)
                .accountId("ACC123")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .timestamp(Instant.now())
                .merchantId("MERCHANT001")
                .merchantCategory("RETAIL")
                .location("New York")
                .type(Transaction.TransactionType.PURCHASE)
                .build();
    }
}

