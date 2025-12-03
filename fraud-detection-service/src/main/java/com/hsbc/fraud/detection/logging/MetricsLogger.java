package com.hsbc.fraud.detection.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metrics logging utility for emitting structured log messages that CloudWatch
 * Log Metric Filters can parse and convert into CloudWatch metrics.
 * 
 * Log Format: [METRIC] EVENT_NAME key1=value1 key2=value2
 * 
 * CloudWatch Metric Filters in Terraform will parse these logs and create
 * corresponding metrics in the FraudDetection namespace.
 * 
 * Usage:
 * <pre>
 *   MetricsLogger.logFraudDetected("HIGH", transactionId, accountId, amount);
 *   MetricsLogger.logTransactionReceived(transactionId, accountId);
 *   MetricsLogger.logHighLatency("fraud-analysis", 1500);
 * </pre>
 */
public class MetricsLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsLogger.class);
    
    // Event constants
    public static final String FRAUD_DETECTED = "FRAUD_DETECTED";
    public static final String TRANSACTION_RECEIVED = "TRANSACTION_RECEIVED";
    public static final String TRANSACTION_PROCESSED = "TRANSACTION_PROCESSED";
    public static final String TRANSACTION_CLEARED = "TRANSACTION_CLEARED";
    public static final String TRANSACTION_SENT = "TRANSACTION_SENT";
    public static final String HIGH_LATENCY = "HIGH_LATENCY";
    public static final String RING_BUFFER_HIGH_UTILIZATION = "RING_BUFFER_HIGH_UTILIZATION";
    public static final String PROCESSING_ERROR = "PROCESSING_ERROR";
    public static final String RULE_VIOLATION = "RULE_VIOLATION";
    
    // Severity levels
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_LOW = "LOW";
    
    // Rule types
    public static final String RULE_LARGE_AMOUNT = "LARGE_AMOUNT";
    public static final String RULE_SUSPICIOUS_ACCOUNT = "SUSPICIOUS_ACCOUNT";
    public static final String RULE_RAPID_FIRE = "RAPID_FIRE";
    
    private MetricsLogger() {
        // Utility class - prevent instantiation
    }
    
    // ========================================================================
    // Fraud Detection Metrics
    // ========================================================================
    
    /**
     * Logs a fraud detection event for CloudWatch metrics.
     * Creates metrics: FraudDetectedCount, FraudSeverity{High|Medium|Low}
     */
    public static void logFraudDetected(String severity, String transactionId, 
                                        String accountId, double amount) {
        logger.warn("[METRIC] {} severity={} transactionId={} accountId={} amount={}",
                FRAUD_DETECTED, severity, transactionId, accountId, amount);
    }
    
    /**
     * Logs a fraud detection event with rule count.
     */
    public static void logFraudDetected(String severity, String transactionId, 
                                        String accountId, double amount, int ruleViolations) {
        logger.warn("[METRIC] {} severity={} transactionId={} accountId={} amount={} ruleViolations={}",
                FRAUD_DETECTED, severity, transactionId, accountId, amount, ruleViolations);
    }
    
    // ========================================================================
    // Transaction Flow Metrics
    // ========================================================================
    
    /**
     * Logs when a transaction is received for processing.
     * Creates metric: TransactionsReceived
     */
    public static void logTransactionReceived(String transactionId, String accountId) {
        logger.info("[METRIC] {} transactionId={} accountId={}",
                TRANSACTION_RECEIVED, transactionId, accountId);
    }
    
    /**
     * Logs when a transaction has been processed (fraud check complete).
     * Creates metric: TransactionsProcessed
     */
    public static void logTransactionProcessed(String transactionId, String accountId, 
                                               long processingTimeMs) {
        logger.info("[METRIC] {} transactionId={} accountId={} processingTimeMs={}",
                TRANSACTION_PROCESSED, transactionId, accountId, processingTimeMs);
    }
    
    /**
     * Logs when a transaction is cleared (no fraud detected).
     * Creates metric: TransactionsCleared
     */
    public static void logTransactionCleared(String transactionId, String accountId) {
        logger.info("[METRIC] {} transactionId={} accountId={}",
                TRANSACTION_CLEARED, transactionId, accountId);
    }
    
    /**
     * Logs when a transaction is sent to the queue (producer side).
     * Creates metric: TransactionsSent
     */
    public static void logTransactionSent(String transactionId, String accountId) {
        logger.info("[METRIC] {} transactionId={} accountId={}",
                TRANSACTION_SENT, transactionId, accountId);
    }
    
    // ========================================================================
    // Performance Metrics
    // ========================================================================
    
    /**
     * Logs high latency event for CloudWatch metrics.
     * Creates metric: HighLatencyCount
     */
    public static void logHighLatency(String operation, long durationMs) {
        logger.warn("[METRIC] {} operation={} durationMs={}",
                HIGH_LATENCY, operation, durationMs);
    }
    
    /**
     * Logs high latency with transaction context.
     */
    public static void logHighLatency(String operation, long durationMs, 
                                      String transactionId) {
        logger.warn("[METRIC] {} operation={} durationMs={} transactionId={}",
                HIGH_LATENCY, operation, durationMs, transactionId);
    }
    
    /**
     * Logs ring buffer high utilization warning.
     * Creates metric: RingBufferHighUtilization
     */
    public static void logRingBufferHighUtilization(double utilization, long remainingCapacity) {
        logger.warn("[METRIC] {} utilization={} remainingCapacity={}",
                RING_BUFFER_HIGH_UTILIZATION, utilization, remainingCapacity);
    }
    
    // ========================================================================
    // Error Metrics
    // ========================================================================
    
    /**
     * Logs a processing error for CloudWatch metrics.
     * Creates metric: ProcessingErrors
     */
    public static void logProcessingError(String errorType, String transactionId, 
                                          String message) {
        logger.error("[METRIC] {} errorType={} transactionId={} message={}",
                PROCESSING_ERROR, errorType, transactionId, message);
    }
    
    /**
     * Logs a processing error with exception.
     */
    public static void logProcessingError(String errorType, String transactionId, 
                                          Throwable error) {
        logger.error("[METRIC] {} errorType={} transactionId={} error={}",
                PROCESSING_ERROR, errorType, transactionId, error.getMessage(), error);
    }
    
    // ========================================================================
    // Rule Violation Metrics
    // ========================================================================
    
    /**
     * Logs a rule violation for CloudWatch metrics.
     * Creates metrics: RuleViolation{LargeAmount|SuspiciousAccount|RapidFire}
     */
    public static void logRuleViolation(String ruleType, String transactionId, 
                                        String accountId) {
        logger.info("[METRIC] {} rule={} transactionId={} accountId={}",
                RULE_VIOLATION, ruleType, transactionId, accountId);
    }
    
    /**
     * Logs large amount rule violation.
     */
    public static void logLargeAmountViolation(String transactionId, String accountId, 
                                               double amount, double threshold) {
        logger.info("[METRIC] {} rule={} transactionId={} accountId={} amount={} threshold={}",
                RULE_VIOLATION, RULE_LARGE_AMOUNT, transactionId, accountId, amount, threshold);
    }
    
    /**
     * Logs suspicious account rule violation.
     */
    public static void logSuspiciousAccountViolation(String transactionId, String accountId) {
        logger.info("[METRIC] {} rule={} transactionId={} accountId={}",
                RULE_VIOLATION, RULE_SUSPICIOUS_ACCOUNT, transactionId, accountId);
    }
    
    /**
     * Logs rapid fire rule violation.
     */
    public static void logRapidFireViolation(String transactionId, String accountId, 
                                             int transactionCount, int maxAllowed) {
        logger.info("[METRIC] {} rule={} transactionId={} accountId={} count={} maxAllowed={}",
                RULE_VIOLATION, RULE_RAPID_FIRE, transactionId, accountId, 
                transactionCount, maxAllowed);
    }
    
    // ========================================================================
    // Helper Methods
    // ========================================================================
    
    /**
     * Determines the severity level based on the number of rule violations.
     */
    public static String determineSeverity(int ruleViolations) {
        if (ruleViolations >= 3) {
            return SEVERITY_HIGH;
        } else if (ruleViolations == 2) {
            return SEVERITY_MEDIUM;
        } else {
            return SEVERITY_LOW;
        }
    }
}

