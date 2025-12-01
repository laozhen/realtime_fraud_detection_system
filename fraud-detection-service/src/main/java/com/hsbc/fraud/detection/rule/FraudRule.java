package com.hsbc.fraud.detection.rule;

import com.hsbc.fraud.detection.model.Transaction;

/**
 * Strategy Pattern: Interface for fraud detection rules.
 * Each implementation represents a specific fraud detection criterion.
 */
public interface FraudRule {
    
    /**
     * Evaluates whether the transaction violates this fraud rule.
     * 
     * @param transaction The transaction to evaluate
     * @return true if the transaction is potentially fraudulent according to this rule
     */
    boolean isFraudulent(Transaction transaction);
    
    /**
     * Returns the name of this fraud rule for reporting purposes.
     * 
     * @return The rule name
     */
    String getRuleName();
    
    /**
     * Returns a description of why the transaction was flagged.
     * 
     * @param transaction The flagged transaction
     * @return Human-readable reason
     */
    String getReason(Transaction transaction);
}

