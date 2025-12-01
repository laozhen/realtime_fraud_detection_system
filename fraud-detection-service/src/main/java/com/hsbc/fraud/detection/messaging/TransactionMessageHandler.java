package com.hsbc.fraud.detection.messaging;

import com.hsbc.fraud.detection.model.FraudAlert;
import com.hsbc.fraud.detection.model.Transaction;
import com.hsbc.fraud.detection.service.AlertService;
import com.hsbc.fraud.detection.service.FraudDetectionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles transaction messages by orchestrating fraud detection and alerting.
 * Decouples message consumption from business logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionMessageHandler {
    
    private final FraudDetectionEngine fraudDetectionEngine;
    private final AlertService alertService;
    
    /**
     * Process a transaction: analyze for fraud and handle any alerts.
     */
    public void handleTransaction(Transaction transaction) {
        log.debug("Processing transaction: {}", transaction.getTransactionId());
        
        try {
            FraudAlert alert = fraudDetectionEngine.analyzeTransaction(transaction);
            
            if (alert != null) {
                alertService.handleAlert(alert);
            } else {
                log.debug("Transaction {} is legitimate", transaction.getTransactionId());
            }
        } catch (Exception e) {
            log.error("Error handling transaction {}: {}", 
                    transaction.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }
}

