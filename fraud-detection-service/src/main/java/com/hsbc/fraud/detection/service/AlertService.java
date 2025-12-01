package com.hsbc.fraud.detection.service;

import com.hsbc.fraud.detection.model.FraudAlert;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Service responsible for handling fraud alerts through various channels.
 * In production, this would integrate with alerting systems, databases, etc.
 */
@Slf4j
@Service
public class AlertService {
    
    /**
     * Processes a fraud alert by logging and potentially notifying external systems.
     * Uses structured logging for easy parsing by CloudWatch/Stackdriver.
     * 
     * @param alert The fraud alert to process
     */
    public void handleAlert(FraudAlert alert) {
        // Set MDC context for structured logging
        MDC.put("alertId", alert.getAlertId());
        MDC.put("transactionId", alert.getTransaction().getTransactionId());
        MDC.put("accountId", alert.getTransaction().getAccountId());
        MDC.put("severity", alert.getSeverity().name());
        
        try {
            // Structured log entry for monitoring systems
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
            MDC.clear();
        }
    }
}

