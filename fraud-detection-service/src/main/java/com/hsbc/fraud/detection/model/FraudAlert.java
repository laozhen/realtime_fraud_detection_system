package com.hsbc.fraud.detection.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents a fraud detection alert with details about violated rules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {
    private String alertId;
    private Transaction transaction;
    private List<String> violatedRules;
    private FraudSeverity severity;
    private Instant detectedAt;
    private String message;
    
    public enum FraudSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}

