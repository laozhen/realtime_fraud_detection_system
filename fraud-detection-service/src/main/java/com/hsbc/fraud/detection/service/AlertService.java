package com.hsbc.fraud.detection.service;

import com.hsbc.fraud.detection.logging.LoggingContext;
import com.hsbc.fraud.detection.logging.MetricsLogger;
import com.hsbc.fraud.detection.logging.StructuredLogger;
import com.hsbc.fraud.detection.model.FraudAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for handling fraud alerts through various channels.
 * In production, this would integrate with alerting systems, databases, etc.
 * Uses distributed logging with correlation IDs for tracing across services.
 * Emits CloudWatch metrics via structured logging for monitoring.
 */
@Slf4j
@Service
public class AlertService {
    
    private final StructuredLogger structuredLogger = StructuredLogger.getLogger(AlertService.class);
    
    /**
     * Processes a fraud alert by logging and potentially notifying external systems.
     * Uses structured logging for easy parsing by CloudWatch/Stackdriver.
     * Emits CloudWatch metrics via log metric filters.
     * 
     * @param alert The fraud alert to process
     */
    public void handleAlert(FraudAlert alert) {
        // Set MDC context for structured logging
        LoggingContext.put("alertId", alert.getAlertId());
        LoggingContext.put("transactionId", alert.getTransaction().getTransactionId());
        LoggingContext.put("accountId", alert.getTransaction().getAccountId());
        LoggingContext.put("severity", alert.getSeverity().name());
        
        try {
            // Emit CloudWatch metric: Fraud Detected with severity
            MetricsLogger.logFraudDetected(
                alert.getSeverity().name(),
                alert.getTransaction().getTransactionId(),
                alert.getTransaction().getAccountId(),
                alert.getTransaction().getAmount().doubleValue(),
                alert.getViolatedRules().size()
            );
            
            // Emit CloudWatch metrics for each rule violation
            emitRuleViolationMetrics(alert);
            
            // Use structured logger for cloud-native logging
            structuredLogger.logFraudAlert(
                alert.getAlertId(),
                alert.getTransaction().getTransactionId(),
                alert.getTransaction().getAccountId(),
                alert.getSeverity().name(),
                alert.getViolatedRules().size(),
                createAdditionalContext(alert)
            );
            
            // Also log with traditional logger for backward compatibility
            log.error("FRAUD_DETECTED: alertId={}, transactionId={}, accountId={}, amount={}, severity={}, rules={}, message={}",
                    alert.getAlertId(),
                    alert.getTransaction().getTransactionId(),
                    alert.getTransaction().getAccountId(),
                    alert.getTransaction().getAmount(),
                    alert.getSeverity(),
                    alert.getViolatedRules().size(),
                    alert.getMessage());
            
            // In production, you would:
            // - Store in database
            // - Send to monitoring/alerting system (PagerDuty, Opsgenie)
            // - Trigger workflow for manual review
            // - Update real-time dashboard
            
        } finally {
            // Clear only alert-specific context, keep correlation IDs
            LoggingContext.remove("alertId");
            LoggingContext.remove("severity");
        }
    }
    
    /**
     * Emits CloudWatch metrics for each rule violation in the alert.
     */
    private void emitRuleViolationMetrics(FraudAlert alert) {
        String transactionId = alert.getTransaction().getTransactionId();
        String accountId = alert.getTransaction().getAccountId();
        
        for (String rule : alert.getViolatedRules()) {
            switch (rule.toUpperCase()) {
                case "LARGE_AMOUNT":
                    MetricsLogger.logRuleViolation(MetricsLogger.RULE_LARGE_AMOUNT, transactionId, accountId);
                    break;
                case "SUSPICIOUS_ACCOUNT":
                    MetricsLogger.logRuleViolation(MetricsLogger.RULE_SUSPICIOUS_ACCOUNT, transactionId, accountId);
                    break;
                case "RAPID_FIRE":
                    MetricsLogger.logRuleViolation(MetricsLogger.RULE_RAPID_FIRE, transactionId, accountId);
                    break;
                default:
                    MetricsLogger.logRuleViolation(rule, transactionId, accountId);
            }
        }
    }
    
    /**
     * Creates additional context for structured logging.
     */
    private Map<String, Object> createAdditionalContext(FraudAlert alert) {
        Map<String, Object> context = new HashMap<>();
        context.put("amount", alert.getTransaction().getAmount());
        context.put("timestamp", alert.getDetectedAt());
        context.put("message", alert.getMessage());
        context.put("violatedRules", String.join(", ", alert.getViolatedRules()));
        return context;
    }
}

