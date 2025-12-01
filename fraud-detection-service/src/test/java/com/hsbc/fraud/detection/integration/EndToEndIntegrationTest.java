package com.hsbc.fraud.detection.integration;

import com.hsbc.fraud.detection.model.FraudAlert;
import com.hsbc.fraud.detection.model.Transaction;
import com.hsbc.fraud.detection.service.FraudDetectionEngine;
import org.junit.Ignore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test without external dependencies.
 * Tests the complete fraud detection flow in-memory.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("End-to-End Integration Tests")
class EndToEndIntegrationTest {
    
    @Autowired
    private FraudDetectionEngine fraudDetectionEngine;
    
    @Test
    @DisplayName("Should detect fraud from large amount")
    void shouldDetectLargeAmount() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX001")
                .accountId("ACCT100")
                .amount(BigDecimal.valueOf(50000))
                .currency("USD")
                .timestamp(Instant.now())
                .build();
        
        FraudAlert alert = fraudDetectionEngine.analyzeTransaction(transaction);
        
        assertNotNull(alert);
        assertEquals(FraudAlert.FraudSeverity.MEDIUM, alert.getSeverity());
        assertTrue(alert.getViolatedRules().stream()
                .anyMatch(rule -> rule.contains("LARGE_AMOUNT_RULE")));
    }
    
    @Test
    @DisplayName("Should detect fraud from suspicious account")
    void shouldDetectSuspiciousAccount() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX002")
                .accountId("ACCT666")
                .amount(BigDecimal.valueOf(1000))
                .currency("USD")
                .timestamp(Instant.now())
                .build();
        
        FraudAlert alert = fraudDetectionEngine.analyzeTransaction(transaction);
        
        assertNotNull(alert);
        assertEquals(FraudAlert.FraudSeverity.MEDIUM, alert.getSeverity());
        assertTrue(alert.getViolatedRules().stream()
                .anyMatch(rule -> rule.contains("SUSPICIOUS_ACCOUNT_RULE")));
    }
    
    @Test
    @DisplayName("Should detect multiple fraud violations")
    void shouldDetectMultipleViolations() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX003")
                .accountId("ACCT001")  // Suspicious
                .amount(BigDecimal.valueOf(20000))  // Large amount
                .currency("USD")
                .timestamp(Instant.now())
                .build();
        
        FraudAlert alert = fraudDetectionEngine.analyzeTransaction(transaction);
        
        assertNotNull(alert);
        assertEquals(FraudAlert.FraudSeverity.HIGH, alert.getSeverity());
        assertEquals(2, alert.getViolatedRules().size());
    }
    
    @Test
    @DisplayName("Should not detect fraud for legitimate transaction")
    void shouldNotDetectLegitimateTransaction() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX004")
                .accountId("ACCT101")
                .amount(BigDecimal.valueOf(500))
                .currency("USD")
                .timestamp(Instant.now())
                .build();
        
        FraudAlert alert = fraudDetectionEngine.analyzeTransaction(transaction);
        
        assertNull(alert);
    }


    @Ignore
    @Test
    @DisplayName("Should detect rapid-fire transactions")
    void shouldDetectRapidFire() {
        String accountId = "ACCT100";
        Instant now = Instant.now();
        
        // Send 6 rapid transactions (limit is 5)
        for (int i = 0; i < 6; i++) {
            Transaction transaction = Transaction.builder()
                    .transactionId("TX-RAPID-"+i)
                    .accountId(accountId)
                    .amount(BigDecimal.valueOf(100))
                    .currency("USD")
                    .timestamp(now.plusSeconds(i))
                    .build();
            
            FraudAlert alert = fraudDetectionEngine.analyzeTransaction(transaction);
            
            if (i == 5) {
                // 6th transaction should trigger rapid-fire detection
                assertNotNull(alert, "Should detect rapid-fire on 6th transaction");
                assertTrue(alert.getViolatedRules().stream()
                        .anyMatch(rule -> rule.contains("RAPID_FIRE_RULE")));
            }
        }
    }
}

