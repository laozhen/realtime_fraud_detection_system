package com.hsbc.fraud.detection.rule;

import com.hsbc.fraud.detection.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RapidFireRule Tests")
class RapidFireRuleTest {
    
    private RapidFireRule rule;
    private static final int MAX_PER_MINUTE = 5;
    
    @BeforeEach
    void setUp() {
        rule = new RapidFireRule(MAX_PER_MINUTE);
    }
    
    @Test
    @DisplayName("Should not flag first transaction")
    void shouldNotFlagFirstTransaction() {
        Transaction transaction = createTransaction("ACCT100", Instant.now());
        
        assertFalse(rule.isFraudulent(transaction));
    }
    
    @Test
    @DisplayName("Should flag when exceeding limit")
    void shouldFlagWhenExceedingLimit() {
        String accountId = "ACCT100";
        Instant now = Instant.now();
        
        // Send MAX_PER_MINUTE transactions
        IntStream.range(0, MAX_PER_MINUTE).forEach(i -> {
            Transaction tx = createTransaction(accountId, now.plusSeconds(i));
            rule.isFraudulent(tx);
        });
        
        // Next transaction should be flagged
        Transaction lastTransaction = createTransaction(accountId, now.plusSeconds(MAX_PER_MINUTE));
        assertTrue(rule.isFraudulent(lastTransaction));
    }
    
    @Test
    @DisplayName("Should not flag when within limit")
    void shouldNotFlagWithinLimit() {
        String accountId = "ACCT100";
        Instant now = Instant.now();
        
        // Send MAX_PER_MINUTE transactions
        IntStream.range(0, MAX_PER_MINUTE).forEach(i -> {
            Transaction tx = createTransaction(accountId, now.plusSeconds(i));
            assertFalse(rule.isFraudulent(tx));
        });
    }
    
    @Test
    @DisplayName("Should track different accounts separately")
    void shouldTrackAccountsSeparately() {
        Instant now = Instant.now();
        
        // Account 1: Send MAX_PER_MINUTE + 1 transactions
        IntStream.range(0, MAX_PER_MINUTE + 1).forEach(i -> {
            rule.isFraudulent(createTransaction("ACCT100", now.plusSeconds(i)));
        });
        
        // Account 2: First transaction should not be flagged
        Transaction account2Tx = createTransaction("ACCT200", now);
        assertFalse(rule.isFraudulent(account2Tx));
    }
    
    @Test
    @DisplayName("Should reset after one minute")
    void shouldResetAfterOneMinute() {
        String accountId = "ACCT100";
        Instant start = Instant.now();
        
        // Send MAX_PER_MINUTE transactions in first minute
        IntStream.range(0, MAX_PER_MINUTE).forEach(i -> {
            Transaction tx = createTransaction(accountId, start.plusSeconds(i));
            rule.isFraudulent(tx);
        });
        
        // Transaction after 61 seconds should not be flagged
        Transaction laterTransaction = createTransaction(accountId, start.plusSeconds(61));
        assertFalse(rule.isFraudulent(laterTransaction));
    }
    
    @Test
    @DisplayName("Rule name should be correct")
    void shouldReturnCorrectRuleName() {
        assertEquals("RAPID_FIRE_RULE", rule.getRuleName());
    }
    
    @Test
    @DisplayName("Should provide meaningful reason")
    void shouldProvideReason() {
        String accountId = "ACCT100";
        Instant now = Instant.now();
        
        // Exceed limit
        IntStream.range(0, MAX_PER_MINUTE + 1).forEach(i -> {
            rule.isFraudulent(createTransaction(accountId, now.plusSeconds(i)));
        });
        
        Transaction transaction = createTransaction(accountId, now);
        String reason = rule.getReason(transaction);
        
        assertNotNull(reason);
        assertTrue(reason.contains(accountId));
        assertTrue(reason.contains(String.valueOf(MAX_PER_MINUTE)));
    }
    
    @Test
    @DisplayName("Should handle null timestamp")
    void shouldHandleNullTimestamp() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX001")
                .accountId("ACCT100")
                .amount(BigDecimal.valueOf(1000))
                .timestamp(null)
                .build();
        
        assertDoesNotThrow(() -> rule.isFraudulent(transaction));
    }
    
    private Transaction createTransaction(String accountId, Instant timestamp) {
        return Transaction.builder()
                .transactionId("TX-" + timestamp.toEpochMilli())
                .accountId(accountId)
                .amount(BigDecimal.valueOf(1000))
                .currency("USD")
                .timestamp(timestamp)
                .build();
    }
}

