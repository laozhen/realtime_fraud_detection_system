package com.hsbc.fraud.detection.service;

import com.hsbc.fraud.detection.logging.LoggingContext;
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
 */
@Slf4j
@Service
public class AlertService {
    
    private final StructuredLogger structuredLogger = StructuredLogger.getLogger(AlertService.class);
    
    /**
     * Processes a fraud alert by logging and potentially notifying external systems.
     * Uses structured logging for easy parsing by CloudWatch/Stackdriver.
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

