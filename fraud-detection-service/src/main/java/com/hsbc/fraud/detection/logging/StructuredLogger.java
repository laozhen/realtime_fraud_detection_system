package com.hsbc.fraud.detection.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

/**
 * Structured logging utility for consistent, cloud-native logging format.
 * Provides methods for logging with structured context that can be easily
 * parsed by CloudWatch Insights, Stackdriver, and other log aggregation tools.
 */
public class StructuredLogger {
    
    private final Logger logger;
    
    private StructuredLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }
    
    private StructuredLogger(String name) {
        this.logger = LoggerFactory.getLogger(name);
    }
    
    /**
     * Creates a structured logger for the given class.
     */
    public static StructuredLogger getLogger(Class<?> clazz) {
        return new StructuredLogger(clazz);
    }
    
    /**
     * Creates a structured logger with the given name.
     */
    public static StructuredLogger getLogger(String name) {
        return new StructuredLogger(name);
    }
    
    /**
     * Logs an info message with structured context.
     */
    public void info(String message, Map<String, Object> context) {
        if (logger.isInfoEnabled()) {
            withContext(context, () -> logger.info(message));
        }
    }
    
    /**
     * Logs a warning message with structured context.
     */
    public void warn(String message, Map<String, Object> context) {
        if (logger.isWarnEnabled()) {
            withContext(context, () -> logger.warn(message));
        }
    }
    
    /**
     * Logs an error message with structured context.
     */
    public void error(String message, Map<String, Object> context) {
        if (logger.isErrorEnabled()) {
            withContext(context, () -> logger.error(message));
        }
    }
    
    /**
     * Logs an error message with exception and structured context.
     */
    public void error(String message, Throwable throwable, Map<String, Object> context) {
        if (logger.isErrorEnabled()) {
            withContext(context, () -> logger.error(message, throwable));
        }
    }
    
    /**
     * Logs a debug message with structured context.
     */
    public void debug(String message, Map<String, Object> context) {
        if (logger.isDebugEnabled()) {
            withContext(context, () -> logger.debug(message));
        }
    }
    
    /**
     * Logs a transaction event with standard transaction context.
     */
    public void logTransaction(String event, String transactionId, String accountId, 
                               double amount, Map<String, Object> additionalContext) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", event);
        context.put("transactionId", transactionId);
        context.put("accountId", accountId);
        context.put("amount", amount);
        if (additionalContext != null) {
            context.putAll(additionalContext);
        }
        
        info("Transaction event: " + event, context);
    }
    
    /**
     * Logs a fraud alert with standard fraud context.
     */
    public void logFraudAlert(String alertId, String transactionId, String accountId,
                             String severity, int ruleViolations, Map<String, Object> additionalContext) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", "FRAUD_DETECTED");
        context.put("alertId", alertId);
        context.put("transactionId", transactionId);
        context.put("accountId", accountId);
        context.put("severity", severity);
        context.put("ruleViolations", ruleViolations);
        if (additionalContext != null) {
            context.putAll(additionalContext);
        }
        
        error("Fraud detected", context);
    }
    
    /**
     * Logs a performance metric.
     */
    public void logPerformance(String operation, long durationMs, Map<String, Object> additionalContext) {
        Map<String, Object> context = new HashMap<>();
        context.put("operation", operation);
        context.put("durationMs", durationMs);
        context.put("metric", "performance");
        if (additionalContext != null) {
            context.putAll(additionalContext);
        }
        
        if (durationMs > 1000) {
            warn("Slow operation detected", context);
        } else {
            info("Operation completed", context);
        }
    }
    
    /**
     * Gets the underlying SLF4J logger for direct access when needed.
     */
    public Logger getUnderlyingLogger() {
        return logger;
    }
    
    /**
     * Executes a runnable with the given context added to MDC.
     */
    private void withContext(Map<String, Object> context, Runnable logAction) {
        if (context == null || context.isEmpty()) {
            logAction.run();
            return;
        }
        
        // Save existing MDC values for the keys we're about to set
        Map<String, String> savedContext = new HashMap<>();
        for (String key : context.keySet()) {
            String existingValue = LoggingContext.get(key);
            if (existingValue != null) {
                savedContext.put(key, existingValue);
            }
        }
        
        try {
            // Set new context values
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                if (entry.getValue() != null) {
                    LoggingContext.put(entry.getKey(), entry.getValue().toString());
                }
            }
            
            // Execute the log action
            logAction.run();
            
        } finally {
            // Restore previous context
            for (String key : context.keySet()) {
                if (savedContext.containsKey(key)) {
                    LoggingContext.put(key, savedContext.get(key));
                } else {
                    LoggingContext.remove(key);
                }
            }
        }
    }
    
    /**
     * Builder for creating structured log contexts.
     */
    public static class ContextBuilder {
        private final Map<String, Object> context = new HashMap<>();
        
        public ContextBuilder add(String key, Object value) {
            if (key != null && value != null) {
                context.put(key, value);
            }
            return this;
        }
        
        public Map<String, Object> build() {
            return context;
        }
    }
    
    /**
     * Creates a new context builder for structured logging.
     */
    public static ContextBuilder context() {
        return new ContextBuilder();
    }
}

