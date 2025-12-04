package com.hsbc.fraud.detection.service;

import com.hsbc.fraud.detection.logging.LoggingContext;
import com.hsbc.fraud.detection.logging.StructuredLogger;
import com.hsbc.fraud.detection.model.FraudAlert;
import com.hsbc.fraud.detection.model.Transaction;
import com.hsbc.fraud.detection.rule.FraudRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core fraud detection engine that orchestrates multiple fraud rules.
 * Follows Open/Closed Principle: open for extension (add new rules), closed for modification.
 * Uses distributed logging for tracing across the system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionEngine {
    
    private final StructuredLogger structuredLogger = StructuredLogger.getLogger(FraudDetectionEngine.class);
    
    private final List<FraudRule> fraudRules;
    
    /**
     * Analyzes a transaction against all configured fraud rules.
     * 
     * @param transaction The transaction to analyze
     * @return FraudAlert if fraud is detected, null otherwise
     */
    public FraudAlert analyzeTransaction(Transaction transaction) {
        long startTime = System.currentTimeMillis();
        
        // Set transaction context for distributed logging
        LoggingContext.setTransactionContext(transaction.getTransactionId(), transaction.getAccountId());
        
        try {
            log.debug("Analyzing transaction: {}", transaction.getTransactionId());
            
            List<String> violatedRules = new ArrayList<>();
            
            for (FraudRule rule : fraudRules) {
                try {
                    if (rule.isFraudulent(transaction)) {
                        String reason = rule.getReason(transaction);
                        violatedRules.add(rule.getRuleName() + ": " + reason);
                        
                        Map<String, Object> ruleContext = new HashMap<>();
                        ruleContext.put("ruleName", rule.getRuleName());
                        ruleContext.put("reason", reason);
                        structuredLogger.warn("Transaction violated fraud rule", ruleContext);
                    }
                } catch (Exception e) {
                    Map<String, Object> errorContext = new HashMap<>();
                    errorContext.put("ruleName", rule.getRuleName());
                    errorContext.put("error", e.getMessage());
                    structuredLogger.error("Error executing fraud rule", e, errorContext);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (!violatedRules.isEmpty()) {
                FraudAlert alert = FraudAlert.builder()
                        .alertId(UUID.randomUUID().toString())
                        .transaction(transaction)
                        .violatedRules(violatedRules)
                        .severity(determineSeverity(violatedRules.size()))
                        .detectedAt(Instant.now())
                        .message("FRAUD DETECTED: " + String.join(", ", violatedRules))
                        .build();
                
                Map<String, Object> alertContext = new HashMap<>();
                alertContext.put("ruleCount", violatedRules.size());
                alertContext.put("durationMs", duration);
                structuredLogger.error("Fraud alert generated", alertContext);
                
                log.error("🚨 FRAUD ALERT: Transaction {} flagged by {} rules",
                        transaction.getTransactionId(), violatedRules.size());
                
                return alert;
            }
            
            // Log performance metrics
            structuredLogger.logPerformance("fraud-analysis", duration, 
                Map.of("result", "clean", "rulesEvaluated", fraudRules.size()));
            
            log.debug("Transaction {} passed all fraud checks", transaction.getTransactionId());
            return null;
            
        } finally {
            // Don't clear transaction context here - let the caller handle it
        }
    }
    
    private FraudAlert.FraudSeverity determineSeverity(int violatedRulesCount) {
        if (violatedRulesCount >= 3) return FraudAlert.FraudSeverity.CRITICAL;
        if (violatedRulesCount == 2) return FraudAlert.FraudSeverity.HIGH;
        if (violatedRulesCount == 1) return FraudAlert.FraudSeverity.MEDIUM;
        return FraudAlert.FraudSeverity.LOW;
    }
}

