package com.hsbc.fraud.detection.disruptor;

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
 * 5. Track metrics for monitoring
 */
@Slf4j
public class TransactionEventHandler implements EventHandler<TransactionEvent> {
    
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
        
        // Import logging utilities at the top of the class when refactoring
        com.hsbc.fraud.detection.logging.LoggingContext.setTransactionContext(
            event.getTransaction().getTransactionId(), 
            event.getTransaction().getAccountId()
        );
        
        try {
            log.debug("Processing transaction {} from ring buffer (sequence: {})", 
                    event.getTransaction().getTransactionId(), sequence);
            
            // Analyze transaction for fraud
            FraudAlert alert = fraudDetectionEngine.analyzeTransaction(event.getTransaction());
            
            // Handle fraud alert if detected
            if (alert != null) {
                alertService.handleAlert(alert);
            }
            
            // Acknowledge SQS message only after successful processing
            if (event.getAcknowledgement() != null) {
                event.getAcknowledgement().acknowledge();
                log.debug("Acknowledged SQS message for transaction {}", 
                        event.getTransaction().getTransactionId());
            }
            
            successCounter.increment();
            sample.stop(processingTimer);
            
            long latencyMs = System.currentTimeMillis() - event.getPublishTimestamp();
            long processingTime = System.currentTimeMillis() - eventStartTime;
            
            if (latencyMs > 100) {
                com.hsbc.fraud.detection.logging.LoggingContext.put("endToEndLatencyMs", String.valueOf(latencyMs));
                com.hsbc.fraud.detection.logging.LoggingContext.put("processingTimeMs", String.valueOf(processingTime));
                log.warn("High latency detected: {}ms for transaction {}", 
                        latencyMs, event.getTransaction().getTransactionId());
            }
            
        } catch (Exception e) {
            log.error("Failed to process transaction {} (sequence: {}): {}", 
                    event.getTransaction().getTransactionId(), sequence, e.getMessage(), e);
            
            failureCounter.increment();
            sample.stop(processingTimer);
            
            // DO NOT acknowledge - let SQS retry or move to DLQ
            log.warn("Message will be retried by SQS or moved to DLQ after max attempts");
            
        } finally {
            // Clear transaction-specific context
            com.hsbc.fraud.detection.logging.LoggingContext.remove("transactionId");
            com.hsbc.fraud.detection.logging.LoggingContext.remove("accountId");
            // Clear event for reuse
            event.clear();
        }
    }
}

