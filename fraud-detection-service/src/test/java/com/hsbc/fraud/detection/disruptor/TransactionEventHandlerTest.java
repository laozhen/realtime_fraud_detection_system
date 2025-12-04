package com.hsbc.fraud.detection.disruptor;

import com.hsbc.fraud.detection.metrics.MetricsCollector;
import com.hsbc.fraud.detection.model.FraudAlert;
import com.hsbc.fraud.detection.model.Transaction;
import com.hsbc.fraud.detection.service.AlertService;
import com.hsbc.fraud.detection.service.FraudDetectionEngine;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("TransactionEventHandler Tests")
@ExtendWith(MockitoExtension.class)
class TransactionEventHandlerTest {
    
    @Mock
    private FraudDetectionEngine fraudDetectionEngine;
    
    @Mock
    private AlertService alertService;
    
    @Mock
    private MetricsCollector metricsCollector;
    
    @Mock
    private Acknowledgement acknowledgement;
    
    private MeterRegistry meterRegistry;
    private ExecutorService executorService;
    private TransactionEventHandler handler;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        executorService = Executors.newFixedThreadPool(2);
        handler = new TransactionEventHandler(
                fraudDetectionEngine,
                alertService,
                meterRegistry,
                executorService,
                metricsCollector
        );
    }
    
    @Test
    @DisplayName("Should initialize with all required metrics")
    void shouldInitializeWithMetrics() {
        assertNotNull(meterRegistry.find("disruptor.transaction.processing.time").timer());
        assertNotNull(meterRegistry.find("disruptor.transaction.processed.success").counter());
        assertNotNull(meterRegistry.find("disruptor.transaction.processed.failure").counter());
    }
    
    @Test
    @DisplayName("Should process transaction successfully without fraud detection")
    void shouldProcessTransactionSuccessfully() throws Exception {
        // Given
        Transaction transaction = createTransaction("TX001", "ACCT100", BigDecimal.valueOf(100));
        TransactionEvent event = createEvent(transaction, acknowledgement);
        
        when(fraudDetectionEngine.analyzeTransaction(transaction)).thenReturn(null);
        
        // When
        handler.onEvent(event, 1L, false);
        
        // Wait for async processing to complete
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        
        // Then
        verify(fraudDetectionEngine).analyzeTransaction(transaction);
        verify(alertService, never()).handleAlert(any());
        verify(acknowledgement).acknowledge();
        verify(metricsCollector).recordTransactionReceived();
        verify(metricsCollector).recordTransactionCleared("ACCT100");
        verify(metricsCollector).recordTransactionProcessed(anyLong());
        verify(metricsCollector).recordTotalLatency(anyLong());
        
        // Verify success counter incremented
        Counter successCounter = meterRegistry.find("disruptor.transaction.processed.success").counter();
        assertNotNull(successCounter);
        assertEquals(1.0, successCounter.count());
        
        // Verify event was cleared
        assertNull(event.getTransaction());
        assertNull(event.getAcknowledgement());
    }
    
    @Test
    @DisplayName("Should handle fraud detection and create alert")
    void shouldHandleFraudDetection() throws Exception {
        // Given
        Transaction transaction = createTransaction("TX002", "ACCT200", BigDecimal.valueOf(50000));
        TransactionEvent event = createEvent(transaction, acknowledgement);
        
        FraudAlert fraudAlert = createFraudAlert(transaction, FraudAlert.FraudSeverity.HIGH);
        when(fraudDetectionEngine.analyzeTransaction(transaction)).thenReturn(fraudAlert);
        
        // When
        handler.onEvent(event, 2L, false);
        
        // Wait for async processing
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        
        // Then
        verify(fraudDetectionEngine).analyzeTransaction(transaction);
        verify(alertService).handleAlert(fraudAlert);
        verify(metricsCollector, never()).recordTransactionCleared(anyString());
        verify(acknowledgement).acknowledge();
        verify(metricsCollector).recordTransactionReceived();
        
        // Verify success counter incremented
        Counter successCounter = meterRegistry.find("disruptor.transaction.processed.success").counter();
        assertEquals(1.0, successCounter.count());
    }
    
    @Test
    @DisplayName("Should handle processing errors and not acknowledge message")
    void shouldHandleProcessingErrors() throws Exception {
        // Given
        Transaction transaction = createTransaction("TX003", "ACCT300", BigDecimal.valueOf(200));
        TransactionEvent event = createEvent(transaction, acknowledgement);
        
        RuntimeException exception = new RuntimeException("Processing failed");
        when(fraudDetectionEngine.analyzeTransaction(transaction)).thenThrow(exception);
        
        // When
        handler.onEvent(event, 3L, false);
        
        // Wait for async processing
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        
        // Then
        verify(fraudDetectionEngine).analyzeTransaction(transaction);
        verify(alertService, never()).handleAlert(any());
        verify(acknowledgement, never()).acknowledge(); // Message should NOT be acknowledged
        verify(metricsCollector).recordTransactionReceived();
        verify(metricsCollector).recordProcessingError(
                eq(MetricsCollector.ERROR_TYPE_TRANSACTION_PROCESSING),
                eq("TX003"),
                eq(exception)
        );
        
        // Verify failure counter incremented
        Counter failureCounter = meterRegistry.find("disruptor.transaction.processed.failure").counter();
        assertEquals(1.0, failureCounter.count());
    }
    
    @Test
    @DisplayName("Should detect and log high latency")
    void shouldDetectHighLatency() throws Exception {
        // Given
        Transaction transaction = createTransaction("TX004", "ACCT400", BigDecimal.valueOf(150));
        TransactionEvent event = createEvent(transaction, acknowledgement);
        
        // Set publish timestamp to simulate high latency (> 100ms ago)
        event.setPublishTimestamp(System.currentTimeMillis() - 500);
        
        when(fraudDetectionEngine.analyzeTransaction(transaction)).thenAnswer(invocation -> {
            // Simulate processing delay to exceed HIGH_LATENCY_THRESHOLD_MS (100ms)
            Thread.sleep(150);
            return null;
        });
        
        // When
        handler.onEvent(event, 4L, false);
        
        // Wait for async processing
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        
        // Then
        ArgumentCaptor<Long> processingTimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(metricsCollector).recordHighLatency(
                eq(MetricsCollector.OPERATION_TRANSACTION_PROCESSING),
                processingTimeCaptor.capture(),
                eq("TX004")
        );
        
        // Verify processing time was > 100ms
        assertTrue(processingTimeCaptor.getValue() > 100,
                "Processing time should exceed HIGH_LATENCY_THRESHOLD_MS (100ms)");
    }
    
    @Test
    @DisplayName("Should process transaction with null acknowledgement")
    void shouldProcessTransactionWithNullAcknowledgement() throws Exception {
        // Given
        Transaction transaction = createTransaction("TX005", "ACCT500", BigDecimal.valueOf(75));
        TransactionEvent event = createEvent(transaction, null);
        
        when(fraudDetectionEngine.analyzeTransaction(transaction)).thenReturn(null);
        
        // When
        handler.onEvent(event, 5L, false);
        
        // Wait for async processing
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        
        // Then - should not throw exception, just skip acknowledgement
        verify(fraudDetectionEngine).analyzeTransaction(transaction);
        verify(metricsCollector).recordTransactionReceived();
        verify(metricsCollector).recordTransactionCleared("ACCT500");
        
        Counter successCounter = meterRegistry.find("disruptor.transaction.processed.success").counter();
        assertEquals(1.0, successCounter.count());
    }
    
    @Test
    @DisplayName("Should process multiple events concurrently")
    void shouldProcessMultipleEventsConcurrently() throws Exception {
        // Given
        int eventCount = 5;
        CountDownLatch latch = new CountDownLatch(eventCount);
        
        when(fraudDetectionEngine.analyzeTransaction(any())).thenAnswer(invocation -> {
            latch.countDown();
            return null;
        });
        
        // When
        for (int i = 0; i < eventCount; i++) {
            Transaction transaction = createTransaction("TX" + i, "ACCT" + i, BigDecimal.valueOf(100 + i));
            TransactionEvent event = createEvent(transaction, acknowledgement);
            handler.onEvent(event, i, false);
        }
        
        // Wait for all processing to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All events should be processed");
        
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        
        // Then
        verify(fraudDetectionEngine, times(eventCount)).analyzeTransaction(any());
        verify(metricsCollector, times(eventCount)).recordTransactionReceived();
        
        Counter successCounter = meterRegistry.find("disruptor.transaction.processed.success").counter();
        assertEquals((double) eventCount, successCounter.count());
    }
    
    @Test
    @DisplayName("Should record timer metrics for processing duration")
    void shouldRecordTimerMetrics() throws Exception {
        // Given
        Transaction transaction = createTransaction("TX006", "ACCT600", BigDecimal.valueOf(250));
        TransactionEvent event = createEvent(transaction, acknowledgement);
        
        when(fraudDetectionEngine.analyzeTransaction(transaction)).thenReturn(null);
        
        // When
        handler.onEvent(event, 6L, false);
        
        // Wait for async processing
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        
        // Then
        Timer processingTimer = meterRegistry.find("disruptor.transaction.processing.time").timer();
        assertNotNull(processingTimer);
        assertEquals(1, processingTimer.count());
        assertTrue(processingTimer.totalTime(TimeUnit.MILLISECONDS) >= 0);
    }
    
    @Test
    @DisplayName("Should clear event after copying data")
    void shouldClearEventAfterCopyingData() {
        // Given
        Transaction transaction = createTransaction("TX007", "ACCT700", BigDecimal.valueOf(300));
        TransactionEvent event = createEvent(transaction, acknowledgement);
        
        // When
        handler.onEvent(event, 7L, true);
        
        // Then - event should be cleared immediately (synchronously)
        assertNull(event.getTransaction());
        assertNull(event.getAcknowledgement());
        assertEquals(0L, event.getPublishTimestamp());
    }
    
    @Test
    @DisplayName("Should handle alert service failure and not acknowledge message")
    void shouldHandleAlertServiceFailure() throws Exception {
        // Given
        Transaction transaction = createTransaction("TX008", "ACCT800", BigDecimal.valueOf(40000));
        TransactionEvent event = createEvent(transaction, acknowledgement);
        
        FraudAlert fraudAlert = createFraudAlert(transaction, FraudAlert.FraudSeverity.CRITICAL);
        when(fraudDetectionEngine.analyzeTransaction(transaction)).thenReturn(fraudAlert);
        
        RuntimeException alertException = new RuntimeException("Alert service failed");
        doThrow(alertException).when(alertService).handleAlert(fraudAlert);
        
        // When
        handler.onEvent(event, 8L, false);
        
        // Wait for async processing
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        
        // Then
        verify(fraudDetectionEngine).analyzeTransaction(transaction);
        verify(alertService).handleAlert(fraudAlert);
        verify(acknowledgement, never()).acknowledge(); // Should not acknowledge on error
        verify(metricsCollector).recordProcessingError(
                eq(MetricsCollector.ERROR_TYPE_TRANSACTION_PROCESSING),
                eq("TX008"),
                eq(alertException)
        );
        
        Counter failureCounter = meterRegistry.find("disruptor.transaction.processed.failure").counter();
        assertEquals(1.0, failureCounter.count());
    }
    
    // Helper methods
    
    private Transaction createTransaction(String transactionId, String accountId, BigDecimal amount) {
        return Transaction.builder()
                .transactionId(transactionId)
                .accountId(accountId)
                .amount(amount)
                .currency("USD")
                .timestamp(Instant.now())
                .merchantId("MERCHANT001")
                .merchantCategory("RETAIL")
                .location("New York")
                .build();
    }
    
    private TransactionEvent createEvent(Transaction transaction, Acknowledgement ack) {
        TransactionEvent event = new TransactionEvent();
        event.setTransaction(transaction);
        event.setAcknowledgement(ack);
        event.setPublishTimestamp(System.currentTimeMillis());
        return event;
    }
    
    private FraudAlert createFraudAlert(Transaction transaction, FraudAlert.FraudSeverity severity) {
        return FraudAlert.builder()
                .alertId("ALERT-" + transaction.getTransactionId())
                .transaction(transaction)
                .violatedRules(Arrays.asList("LARGE_AMOUNT_RULE: Amount exceeds threshold"))
                .severity(severity)
                .detectedAt(Instant.now())
                .message("FRAUD DETECTED")
                .build();
    }
}

