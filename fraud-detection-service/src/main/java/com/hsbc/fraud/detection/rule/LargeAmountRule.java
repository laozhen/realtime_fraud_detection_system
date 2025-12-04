package com.hsbc.fraud.detection.rule;

import com.hsbc.fraud.detection.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fraud rule that flags transactions exceeding a certain threshold amount.
 * Follows the Strategy Pattern for extensibility.
 */
@Slf4j
@Component
public class LargeAmountRule implements FraudRule {
    
    private final BigDecimal threshold;
    
    public LargeAmountRule(
            @Value("${fraud.rules.large-amount.threshold:10000}") BigDecimal threshold) {
        this.threshold = threshold;
        log.info("LargeAmountRule initialized with threshold: {}", threshold);
    }
    
    @Override
    public boolean isFraudulent(Transaction transaction) {
        simulateProcessingLoad();

        if (transaction.getAmount() == null) {
            return false;
        }
        
        // Simulate processing load for testing

        return transaction.getAmount().compareTo(threshold) > 0;
    }
    
    /**
     * Simulates virtual load: 20ms processing time + CPU-intensive work
     */
    private void simulateProcessingLoad() {
        long startTime = System.nanoTime();
        // Add 20ms delay

        // Thread.sleep(1);

        // Add CPU-intensive work (0.01% CPU simulation)
        double result = 0;
        for (int i = 0; i < 1000; i++) {
            result += Math.sqrt(i) * Math.log(i + 1);
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        // Prevent optimization by logging at trace level
        log.info("Processing load simulation completed: {} (duration: {}ms)", result, durationMs);
    }
    
    @Override
    public String getRuleName() {
        return "LARGE_AMOUNT_RULE";
    }
    
    @Override
    public String getReason(Transaction transaction) {
        return String.format("Transaction amount %s %s exceeds threshold of %s",
                transaction.getAmount(),
                transaction.getCurrency(),
                threshold);
    }
}

