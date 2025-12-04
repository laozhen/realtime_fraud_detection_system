package com.hsbc.fraud.detection.logging;

import org.slf4j.MDC;

import java.util.Map;
import java.util.HashMap;

/**
 * Central class for managing logging context across distributed system.
 * Provides thread-safe access to MDC (Mapped Diagnostic Context) for structured logging.
 */
public class LoggingContext {
    
    // Standard MDC keys
    public static final String CORRELATION_ID = "correlationId";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String USER_ID = "userId";
    public static final String TRANSACTION_ID = "transactionId";
    public static final String ACCOUNT_ID = "accountId";
    public static final String SERVICE_NAME = "serviceName";
    public static final String CLOUD_PROVIDER = "cloudProvider";
    public static final String ENVIRONMENT = "environment";
    
    private LoggingContext() {
        // Utility class
    }
    
    /**
     * Sets the correlation ID in MDC for distributed tracing.
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            MDC.put(CORRELATION_ID, correlationId);
        }
    }
    
    /**
     * Gets the current correlation ID from MDC.
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID);
    }
    
    /**
     * Sets the trace ID for distributed tracing.
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID, traceId);
        }
    }
    
    /**
     * Gets the current trace ID.
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }
    
    /**
     * Sets the span ID for distributed tracing.
     */
    public static void setSpanId(String spanId) {
        if (spanId != null) {
            MDC.put(SPAN_ID, spanId);
        }
    }
    
    /**
     * Sets transaction-specific context.
     */
    public static void setTransactionContext(String transactionId, String accountId) {
        if (transactionId != null) {
            MDC.put(TRANSACTION_ID, transactionId);
        }
        if (accountId != null) {
            MDC.put(ACCOUNT_ID, accountId);
        }
    }
    
    /**
     * Sets service context information.
     */
    public static void setServiceContext(String serviceName, String cloudProvider, String environment) {
        if (serviceName != null) {
            MDC.put(SERVICE_NAME, serviceName);
        }
        if (cloudProvider != null) {
            MDC.put(CLOUD_PROVIDER, cloudProvider);
        }
        if (environment != null) {
            MDC.put(ENVIRONMENT, environment);
        }
    }
    
    /**
     * Sets a custom key-value pair in MDC.
     */
    public static void put(String key, String value) {
        if (key != null && value != null) {
            MDC.put(key, value);
        }
    }
    
    /**
     * Gets a value from MDC.
     */
    public static String get(String key) {
        return MDC.get(key);
    }
    
    /**
     * Gets all MDC context as a map (useful for propagation).
     */
    public static Map<String, String> getContext() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return contextMap != null ? contextMap : new HashMap<>();
    }
    
    /**
     * Sets the entire MDC context from a map (useful for propagation).
     */
    public static void setContext(Map<String, String> contextMap) {
        if (contextMap != null && !contextMap.isEmpty()) {
            MDC.setContextMap(contextMap);
        }
    }
    
    /**
     * Clears a specific key from MDC.
     */
    public static void remove(String key) {
        MDC.remove(key);
    }
    
    /**
     * Clears all MDC context.
     * Should be called after request/transaction processing is complete.
     */
    public static void clear() {
        MDC.clear();
    }
    
    /**
     * Executes a runnable with a specific MDC context and clears it afterward.
     */
    public static void executeWithContext(Map<String, String> context, Runnable runnable) {
        Map<String, String> previousContext = getContext();
        try {
            setContext(context);
            runnable.run();
        } finally {
            clear();
            if (previousContext != null && !previousContext.isEmpty()) {
                setContext(previousContext);
            }
        }
    }
}

