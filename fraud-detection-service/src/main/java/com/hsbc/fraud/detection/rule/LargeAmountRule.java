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
        if (transaction.getAmount() == null) {
            return false;
        }
        return transaction.getAmount().compareTo(threshold) > 0;
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

