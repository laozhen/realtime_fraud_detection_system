package com.hsbc.fraud.detection.logging;

import java.util.UUID;

/**
 * Utility class for generating correlation IDs for distributed tracing.
 * Uses UUID v4 for unique identification across distributed systems.
 */
public class CorrelationIdGenerator {
    
    private CorrelationIdGenerator() {
        // Utility class
    }
    
    /**
     * Generates a new unique correlation ID.
     * 
     * @return A new UUID-based correlation ID
     */
    public static String generate() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Generates a correlation ID with a prefix for easier identification.
     * 
     * @param prefix The prefix to prepend (e.g., "TXN", "FRAUD")
     * @return A prefixed correlation ID
     */
    public static String generateWithPrefix(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString();
    }
}

