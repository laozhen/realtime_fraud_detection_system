package com.hsbc.fraud.detection.service;

import com.hsbc.fraud.detection.model.FraudAlert;
import com.hsbc.fraud.detection.model.Transaction;
import com.hsbc.fraud.detection.rule.FraudRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core fraud detection engine that orchestrates multiple fraud rules.
 * Follows Open/Closed Principle: open for extension (add new rules), closed for modification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionEngine {
    
    private final List<FraudRule> fraudRules;
    
    /**
     * Analyzes a transaction against all configured fraud rules.
     * 
     * @param transaction The transaction to analyze
     * @return FraudAlert if fraud is detected, null otherwise
     */
    public FraudAlert analyzeTransaction(Transaction transaction) {
        log.debug("Analyzing transaction: {}", transaction.getTransactionId());
        
        List<String> violatedRules = new ArrayList<>();
        
        for (FraudRule rule : fraudRules) {
            try {
                if (rule.isFraudulent(transaction)) {
                    String reason = rule.getReason(transaction);
                    violatedRules.add(rule.getRuleName() + ": " + reason);
                    log.warn("Transaction {} violated rule: {}", 
                            transaction.getTransactionId(), rule.getRuleName());
                }
            } catch (Exception e) {
                log.error("Error executing fraud rule {}: {}", rule.getRuleName(), e.getMessage(), e);
            }
        }
        
        if (!violatedRules.isEmpty()) {
            FraudAlert alert = FraudAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .transaction(transaction)
                    .violatedRules(violatedRules)
                    .severity(determineSeverity(violatedRules.size()))
                    .detectedAt(Instant.now())
                    .message("FRAUD DETECTED: " + String.join(", ", violatedRules))
                    .build();
            
            log.error("🚨 FRAUD ALERT: Transaction {} flagged by {} rules",
                    transaction.getTransactionId(), violatedRules.size());
            
            return alert;
        }
        
        log.debug("Transaction {} passed all fraud checks", transaction.getTransactionId());
        return null;
    }
    
    private FraudAlert.FraudSeverity determineSeverity(int violatedRulesCount) {
        if (violatedRulesCount >= 3) return FraudAlert.FraudSeverity.CRITICAL;
        if (violatedRulesCount == 2) return FraudAlert.FraudSeverity.HIGH;
        if (violatedRulesCount == 1) return FraudAlert.FraudSeverity.MEDIUM;
        return FraudAlert.FraudSeverity.LOW;
    }
}

