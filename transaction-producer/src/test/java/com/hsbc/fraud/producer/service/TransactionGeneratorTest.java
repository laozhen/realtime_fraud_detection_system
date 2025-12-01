package com.hsbc.fraud.producer.service;

import com.hsbc.fraud.producer.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransactionGenerator Tests")
class TransactionGeneratorTest {
    
    private TransactionGenerator generator;
    
    @BeforeEach
    void setUp() {
        generator = new TransactionGenerator();
    }
    
    @Test
    @DisplayName("Should generate transaction with all required fields")
    void shouldGenerateCompleteTransaction() {
        Transaction transaction = generator.generateTransaction();
        
        assertNotNull(transaction);
        assertNotNull(transaction.getTransactionId());
        assertNotNull(transaction.getAccountId());
        assertNotNull(transaction.getAmount());
        assertNotNull(transaction.getCurrency());
        assertNotNull(transaction.getTimestamp());
        assertNotNull(transaction.getMerchantId());
        assertNotNull(transaction.getMerchantCategory());
        assertNotNull(transaction.getLocation());
        assertNotNull(transaction.getType());
    }
    
    @Test
    @DisplayName("Should generate unique transaction IDs")
    void shouldGenerateUniqueIds() {
        Set<String> ids = new HashSet<>();
        
        for (int i = 0; i < 100; i++) {
            Transaction transaction = generator.generateTransaction();
            ids.add(transaction.getTransactionId());
        }
        
        assertEquals(100, ids.size());
    }
    
    @RepeatedTest(10)
    @DisplayName("Should generate positive amounts")
    void shouldGeneratePositiveAmounts() {
        Transaction transaction = generator.generateTransaction();
        
        assertTrue(transaction.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0);
    }
    
    @Test
    @DisplayName("Should set currency to USD")
    void shouldSetCurrencyToUSD() {
        Transaction transaction = generator.generateTransaction();
        
        assertEquals("USD", transaction.getCurrency());
    }
    
    @Test
    @DisplayName("Should generate rapid-fire burst with same account")
    void shouldGenerateRapidFireBurst() {
        int count = 10;
        List<Transaction> transactions = generator.generateRapidFireBurst(count);
        
        assertEquals(count, transactions.size());
        
        // All should have same account ID
        String firstAccountId = transactions.get(0).getAccountId();
        assertTrue(transactions.stream()
                .allMatch(tx -> tx.getAccountId().equals(firstAccountId)));
    }
    
    @Test
    @DisplayName("Should generate rapid-fire transactions with unique IDs")
    void shouldGenerateRapidFireWithUniqueIds() {
        List<Transaction> transactions = generator.generateRapidFireBurst(10);
        
        Set<String> ids = new HashSet<>();
        transactions.forEach(tx -> ids.add(tx.getTransactionId()));
        
        assertEquals(10, ids.size());
    }
    
    @Test
    @DisplayName("Should occasionally generate fraudulent transactions")
    void shouldGenerateSomeFraudulentTransactions() {
        // Generate many transactions and check distribution
        Set<String> suspiciousAccounts = Set.of("ACCT001", "ACCT666", "ACCT999");
        int suspiciousCount = 0;
        int largeAmountCount = 0;
        int total = 1000;
        
        for (int i = 0; i < total; i++) {
            Transaction tx = generator.generateTransaction();
            if (suspiciousAccounts.contains(tx.getAccountId())) {
                suspiciousCount++;
            }
            if (tx.getAmount().compareTo(java.math.BigDecimal.valueOf(10000)) > 0) {
                largeAmountCount++;
            }
        }
        
        // Should have some suspicious transactions (allowing for randomness)
        int totalFraudulent = suspiciousCount + largeAmountCount;
        assertTrue(totalFraudulent > 0, "Should generate some fraudulent transactions");
        assertTrue(totalFraudulent < total * 0.3, "Fraudulent rate should be reasonable");
    }
}

