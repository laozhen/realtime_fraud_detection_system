package com.hsbc.fraud.producer.logging;

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
 *   MetricsLogger.logTransactionSent(transactionId, accountId);
 *   MetricsLogger.logHighLatency("sqs-publish", 500);
 *   MetricsLogger.logProcessingError("SQS_ERROR", transactionId, error);
 * </pre>
 */
public class MetricsLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsLogger.class);
    
    // Event constants
    public static final String TRANSACTION_SENT = "TRANSACTION_SENT";
    public static final String HIGH_LATENCY = "HIGH_LATENCY";
    public static final String PROCESSING_ERROR = "PROCESSING_ERROR";
    
    private MetricsLogger() {
        // Utility class - prevent instantiation
    }
    
    // ========================================================================
    // Transaction Producer Metrics
    // ========================================================================
    
    /**
     * Logs when a transaction is sent to the queue.
     * Creates metric: TransactionsSent
     */
    public static void logTransactionSent(String transactionId, String accountId) {
        logger.info("[METRIC] {} transactionId={} accountId={}",
                TRANSACTION_SENT, transactionId, accountId);
    }
    
    /**
     * Logs when a transaction is sent with timing information.
     */
    public static void logTransactionSent(String transactionId, String accountId, 
                                          long publishTimeMs) {
        logger.info("[METRIC] {} transactionId={} accountId={} publishTimeMs={}",
                TRANSACTION_SENT, transactionId, accountId, publishTimeMs);
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
}

