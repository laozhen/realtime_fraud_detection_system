package com.hsbc.fraud.detection.rule;

import com.hsbc.fraud.detection.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SuspiciousAccountRule Tests")
class SuspiciousAccountRuleTest {
    
    private SuspiciousAccountRule rule;
    
    @BeforeEach
    void setUp() {
        List<String> blacklistedAccounts = List.of("ACCT001", "ACCT666", "ACCT999");
        rule = new SuspiciousAccountRule(blacklistedAccounts);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"ACCT001", "ACCT666", "ACCT999"})
    @DisplayName("Should flag blacklisted accounts")
    void shouldFlagBlacklistedAccounts(String accountId) {
        Transaction transaction = createTransaction(accountId);
        
        assertTrue(rule.isFraudulent(transaction));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"ACCT100", "ACCT200", "ACCT500", "ACCT002"})
    @DisplayName("Should not flag normal accounts")
    void shouldNotFlagNormalAccounts(String accountId) {
        Transaction transaction = createTransaction(accountId);
        
        assertFalse(rule.isFraudulent(transaction));
    }
    
    @Test
    @DisplayName("Should not flag transaction with null account ID")
    void shouldNotFlagNullAccountId() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX001")
                .accountId(null)
                .amount(BigDecimal.valueOf(1000))
                .build();
        
        assertFalse(rule.isFraudulent(transaction));
    }
    
    @Test
    @DisplayName("Should be case-sensitive for account IDs")
    void shouldBeCaseSensitive() {
        Transaction transaction = createTransaction("acct001");  // lowercase
        
        assertFalse(rule.isFraudulent(transaction));
    }
    
    @Test
    @DisplayName("Rule name should be correct")
    void shouldReturnCorrectRuleName() {
        assertEquals("SUSPICIOUS_ACCOUNT_RULE", rule.getRuleName());
    }
    
    @Test
    @DisplayName("Should provide meaningful reason")
    void shouldProvideReason() {
        Transaction transaction = createTransaction("ACCT666");
        
        String reason = rule.getReason(transaction);
        
        assertNotNull(reason);
        assertTrue(reason.contains("ACCT666"));
        assertTrue(reason.contains("blacklist"));
    }
    
    @Test
    @DisplayName("Should allow dynamic addition to blacklist")
    void shouldAllowDynamicAddition() {
        String newSuspiciousAccount = "ACCT888";
        Transaction transaction = createTransaction(newSuspiciousAccount);
        
        // Initially not flagged
        assertFalse(rule.isFraudulent(transaction));
        
        // Add to blacklist
        rule.addToBlacklist(newSuspiciousAccount);
        
        // Now should be flagged
        assertTrue(rule.isFraudulent(transaction));
    }
    
    private Transaction createTransaction(String accountId) {
        return Transaction.builder()
                .transactionId("TX001")
                .accountId(accountId)
                .amount(BigDecimal.valueOf(1000))
                .currency("USD")
                .timestamp(Instant.now())
                .build();
    }
}

