package com.hsbc.fraud.detection.disruptor;

import com.hsbc.fraud.detection.logging.LoggingContext;
import com.hsbc.fraud.detection.metrics.MetricsCollector;
import com.hsbc.fraud.detection.model.FraudAlert;
import com.hsbc.fraud.detection.model.Transaction;
import com.hsbc.fraud.detection.service.AlertService;
import com.hsbc.fraud.detection.service.FraudDetectionEngine;
import com.lmax.disruptor.EventHandler;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

/**
 * Event handler that processes transaction events from the Disruptor ring buffer.
 * 
 * In Disruptor 4.0, this handler uses an internal thread pool to process events
 * concurrently, providing parallel processing while maintaining Disruptor's
 * low-latency characteristics.
 * 
 * The thread pool manages concurrency automatically through its bounded queue
 * and worker threads, eliminating the need for explicit semaphore-based control.
 * 
 * Responsibilities:
 * 1. Analyze transaction through fraud detection engine (async in thread pool)
 * 2. Handle fraud alerts via alert service
 * 3. Acknowledge SQS message on success
 * 4. Leave message unacknowledged on failure (for retry)
 * 5. Track metrics for monitoring (Prometheus + CloudWatch via log metrics)
 */
@Slf4j
public class TransactionEventHandler implements EventHandler<TransactionEvent> {
    
    private static final long HIGH_LATENCY_THRESHOLD_MS = 100;
    
    private final FraudDetectionEngine fraudDetectionEngine;
    private final AlertService alertService;
    private final ExecutorService executorService;
    private final MetricsCollector metricsCollector;
    private final Timer processingTimer;
    private final Counter successCounter;
    private final Counter failureCounter;
    
    public TransactionEventHandler(
            FraudDetectionEngine fraudDetectionEngine,
            AlertService alertService,
            MeterRegistry meterRegistry,
            ExecutorService executorService,
            MetricsCollector metricsCollector) {
        this.fraudDetectionEngine = fraudDetectionEngine;
        this.alertService = alertService;
        this.executorService = executorService;
        this.metricsCollector = metricsCollector;
        
        // Initialize metrics
        this.processingTimer = Timer.builder("disruptor.transaction.processing.time")
                .description("Time taken to process a transaction through fraud detection")
                .register(meterRegistry);
        
        this.successCounter = Counter.builder("disruptor.transaction.processed.success")
                .description("Number of transactions processed successfully")
                .register(meterRegistry);
        
        this.failureCounter = Counter.builder("disruptor.transaction.processed.failure")
                .description("Number of transactions that failed processing")
                .register(meterRegistry);
        
        log.info("TransactionEventHandler initialized with thread pool executor");
    }
    
    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) {
        // Copy the transaction data and acknowledgement as the event will be reused
        Transaction transaction = event.getTransaction();
        Acknowledgement acknowledgement = event.getAcknowledgement();
        long publishTimestamp = event.getPublishTimestamp();
        
        // Submit for async processing in thread pool
        // The thread pool's bounded queue provides natural backpressure
        executorService.execute(() -> 
            processTransaction(transaction, acknowledgement, publishTimestamp, sequence)
        );
        
        // Clear event for reuse immediately after copying data
        event.clear();
    }
    
    /**
     * Process a single transaction in the thread pool.
     */
    private void processTransaction(Transaction transaction, 
                                   Acknowledgement acknowledgement,
                                   long publishTimestamp,
                                   long sequence) {
        Timer.Sample sample = Timer.start();
        long eventStartTime = transaction.getTimestamp().toEpochMilli();
        
        String transactionId = transaction.getTransactionId();
        String accountId = transaction.getAccountId();
        
        LoggingContext.setTransactionContext(transactionId, accountId);
        
        try {
            log.debug("Processing transaction {} from ring buffer (sequence: {})", 
                    transactionId, sequence);
            
            // Record Prometheus metric: Transaction Received
            metricsCollector.recordTransactionReceived();
            
            // Analyze transaction for fraud
            FraudAlert alert = fraudDetectionEngine.analyzeTransaction(transaction);
            
            // Handle fraud alert if detected
            if (alert != null) {
                alertService.handleAlert(alert);
                // Note: metricsCollector.recordFraudDetected is called in AlertService
            } else {
                // Record Prometheus metric: Transaction Cleared
                metricsCollector.recordTransactionCleared(accountId);
            }
            
            // Acknowledge SQS message only after successful processing
            if (acknowledgement != null) {
                acknowledgement.acknowledge();
                log.debug("Acknowledged SQS message for transaction {}", transactionId);
            }
            
            successCounter.increment();
            sample.stop(processingTimer);
            
            long latencyMs = System.currentTimeMillis() - publishTimestamp;
            long processingTime = System.currentTimeMillis() - eventStartTime;
            
            // Record Prometheus metric: Transaction Processed with duration
            metricsCollector.recordTransactionProcessed(processingTime);
            metricsCollector.recordTotalLatency(latencyMs);
            if (processingTime > HIGH_LATENCY_THRESHOLD_MS) {
                LoggingContext.put("endToEndLatencyMs", String.valueOf(latencyMs));
                LoggingContext.put("processingTimeMs", String.valueOf(processingTime));
                
                // Record Prometheus metric: High Latency
                metricsCollector.recordHighLatency(MetricsCollector.OPERATION_TRANSACTION_PROCESSING, processingTime, transactionId);
                
                log.warn("High latency detected: {}ms for transaction {}", latencyMs, transactionId);
            }
            
        } catch (Exception e) {
            log.error("Failed to process transaction {} (sequence: {}): {}", 
                    transactionId, sequence, e.getMessage(), e);
            
            // Record Prometheus metric: Processing Error
            metricsCollector.recordProcessingError(MetricsCollector.ERROR_TYPE_TRANSACTION_PROCESSING, transactionId, e);
            
            failureCounter.increment();
            sample.stop(processingTimer);
            
            // DO NOT acknowledge - let SQS retry or move to DLQ
            log.warn("Message will be retried by SQS or moved to DLQ after max attempts");
            
        } finally {
            // Clear transaction-specific context
            LoggingContext.remove("transactionId");
            LoggingContext.remove("accountId");
        }
    }
}

