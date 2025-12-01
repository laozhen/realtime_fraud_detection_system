package com.hsbc.fraud.detection.rule;

import com.hsbc.fraud.detection.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LargeAmountRule Tests")
class LargeAmountRuleTest {
    
    private LargeAmountRule rule;
    private static final BigDecimal THRESHOLD = new BigDecimal("10000");
    
    @BeforeEach
    void setUp() {
        rule = new LargeAmountRule(THRESHOLD);
    }
    
    @Test
    @DisplayName("Should flag transaction exceeding threshold")
    void shouldFlagLargeTransaction() {
        Transaction transaction = createTransaction("12000.00");
        
        assertTrue(rule.isFraudulent(transaction));
    }
    
    @Test
    @DisplayName("Should not flag transaction below threshold")
    void shouldNotFlagNormalTransaction() {
        Transaction transaction = createTransaction("5000.00");
        
        assertFalse(rule.isFraudulent(transaction));
    }
    
    @Test
    @DisplayName("Should not flag transaction exactly at threshold")
    void shouldNotFlagTransactionAtThreshold() {
        Transaction transaction = createTransaction("10000.00");
        
        assertFalse(rule.isFraudulent(transaction));
    }
    
    @Test
    @DisplayName("Should not flag transaction with null amount")
    void shouldNotFlagNullAmount() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX001")
                .accountId("ACCT100")
                .amount(null)
                .build();
        
        assertFalse(rule.isFraudulent(transaction));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"10000.01", "15000", "50000", "100000", "999999.99"})
    @DisplayName("Should flag all amounts above threshold")
    void shouldFlagVariousLargeAmounts(String amount) {
        Transaction transaction = createTransaction(amount);
        
        assertTrue(rule.isFraudulent(transaction));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"0.01", "100", "1000", "5000", "9999.99"})
    @DisplayName("Should not flag amounts below threshold")
    void shouldNotFlagVariousNormalAmounts(String amount) {
        Transaction transaction = createTransaction(amount);
        
        assertFalse(rule.isFraudulent(transaction));
    }
    
    @Test
    @DisplayName("Rule name should be correct")
    void shouldReturnCorrectRuleName() {
        assertEquals("LARGE_AMOUNT_RULE", rule.getRuleName());
    }
    
    @Test
    @DisplayName("Should provide meaningful reason")
    void shouldProvideReason() {
        Transaction transaction = createTransaction("15000.00");
        
        String reason = rule.getReason(transaction);
        
        assertNotNull(reason);
        assertTrue(reason.contains("15000.00"));
        assertTrue(reason.contains("10000"));
    }
    
    private Transaction createTransaction(String amount) {
        return Transaction.builder()
                .transactionId("TX001")
                .accountId("ACCT100")
                .amount(new BigDecimal(amount))
                .currency("USD")
                .timestamp(Instant.now())
                .build();
    }
}

