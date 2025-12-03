package com.hsbc.fraud.detection.disruptor;

import com.hsbc.fraud.detection.logging.LoggingContext;
import com.hsbc.fraud.detection.logging.MetricsLogger;
import com.hsbc.fraud.detection.model.FraudAlert;
import com.hsbc.fraud.detection.service.AlertService;
import com.hsbc.fraud.detection.service.FraudDetectionEngine;
import com.lmax.disruptor.EventHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Event handler that processes transaction events from the Disruptor ring buffer.
 * 
 * Responsibilities:
 * 1. Analyze transaction through fraud detection engine
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
    private final Timer processingTimer;
    private final Counter successCounter;
    private final Counter failureCounter;
    
    public TransactionEventHandler(
            FraudDetectionEngine fraudDetectionEngine,
            AlertService alertService,
            MeterRegistry meterRegistry) {
        this.fraudDetectionEngine = fraudDetectionEngine;
        this.alertService = alertService;
        
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
    }
    
    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) {
        Timer.Sample sample = Timer.start();
        long eventStartTime = System.currentTimeMillis();
        
        String transactionId = event.getTransaction().getTransactionId();
        String accountId = event.getTransaction().getAccountId();
        
        LoggingContext.setTransactionContext(transactionId, accountId);
        
        try {
            log.debug("Processing transaction {} from ring buffer (sequence: {})", 
                    transactionId, sequence);
            
            // Emit CloudWatch metric: Transaction Received
            MetricsLogger.logTransactionReceived(transactionId, accountId);
            
            // Analyze transaction for fraud
            FraudAlert alert = fraudDetectionEngine.analyzeTransaction(event.getTransaction());
            
            // Handle fraud alert if detected
            if (alert != null) {
                alertService.handleAlert(alert);
                // Note: MetricsLogger.logFraudDetected is called in AlertService
            } else {
                // Emit CloudWatch metric: Transaction Cleared
                MetricsLogger.logTransactionCleared(transactionId, accountId);
            }
            
            // Acknowledge SQS message only after successful processing
            if (event.getAcknowledgement() != null) {
                event.getAcknowledgement().acknowledge();
                log.debug("Acknowledged SQS message for transaction {}", transactionId);
            }
            
            successCounter.increment();
            sample.stop(processingTimer);
            
            long latencyMs = System.currentTimeMillis() - event.getPublishTimestamp();
            long processingTime = System.currentTimeMillis() - eventStartTime;
            
            // Emit CloudWatch metric: Transaction Processed
            MetricsLogger.logTransactionProcessed(transactionId, accountId, processingTime);
            
            if (latencyMs > HIGH_LATENCY_THRESHOLD_MS) {
                LoggingContext.put("endToEndLatencyMs", String.valueOf(latencyMs));
                LoggingContext.put("processingTimeMs", String.valueOf(processingTime));
                
                // Emit CloudWatch metric: High Latency
                MetricsLogger.logHighLatency("transaction-processing", latencyMs, transactionId);
                
                log.warn("High latency detected: {}ms for transaction {}", latencyMs, transactionId);
            }
            
        } catch (Exception e) {
            log.error("Failed to process transaction {} (sequence: {}): {}", 
                    transactionId, sequence, e.getMessage(), e);
            
            // Emit CloudWatch metric: Processing Error
            MetricsLogger.logProcessingError("TRANSACTION_PROCESSING", transactionId, e);
            
            failureCounter.increment();
            sample.stop(processingTimer);
            
            // DO NOT acknowledge - let SQS retry or move to DLQ
            log.warn("Message will be retried by SQS or moved to DLQ after max attempts");
            
        } finally {
            // Clear transaction-specific context
            LoggingContext.remove("transactionId");
            LoggingContext.remove("accountId");
            // Clear event for reuse
            event.clear();
        }
    }
}

