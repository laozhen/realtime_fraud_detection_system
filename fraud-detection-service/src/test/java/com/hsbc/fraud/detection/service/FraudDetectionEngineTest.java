package com.hsbc.fraud.detection.service;

import com.hsbc.fraud.detection.model.FraudAlert;
import com.hsbc.fraud.detection.model.Transaction;
import com.hsbc.fraud.detection.rule.FraudRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionEngine Tests")
class FraudDetectionEngineTest {
    
    @Mock
    private FraudRule rule1;
    
    @Mock
    private FraudRule rule2;
    
    @Mock
    private FraudRule rule3;
    
    private FraudDetectionEngine engine;
    
    @BeforeEach
    void setUp() {
        lenient().when(rule1.getRuleName()).thenReturn("RULE_1");
        lenient().when(rule2.getRuleName()).thenReturn("RULE_2");
        lenient().when(rule3.getRuleName()).thenReturn("RULE_3");
        
        List<FraudRule> rules = Arrays.asList(rule1, rule2, rule3);
        engine = new FraudDetectionEngine(rules);
    }
    
    @Test
    @DisplayName("Should return null when no rules violated")
    void shouldReturnNullWhenNoRulesViolated() {
        Transaction transaction = createTransaction();
        
        when(rule1.isFraudulent(transaction)).thenReturn(false);
        when(rule2.isFraudulent(transaction)).thenReturn(false);
        when(rule3.isFraudulent(transaction)).thenReturn(false);
        
        FraudAlert alert = engine.analyzeTransaction(transaction);
        
        assertNull(alert);
        verify(rule1).isFraudulent(transaction);
        verify(rule2).isFraudulent(transaction);
        verify(rule3).isFraudulent(transaction);
    }
    
    @Test
    @DisplayName("Should create alert when one rule violated")
    void shouldCreateAlertWhenOneRuleViolated() {
        Transaction transaction = createTransaction();
        
        when(rule1.isFraudulent(transaction)).thenReturn(true);
        when(rule1.getReason(transaction)).thenReturn("Rule 1 violated");
        when(rule2.isFraudulent(transaction)).thenReturn(false);
        when(rule3.isFraudulent(transaction)).thenReturn(false);
        
        FraudAlert alert = engine.analyzeTransaction(transaction);
        
        assertNotNull(alert);
        assertNotNull(alert.getAlertId());
        assertEquals(transaction, alert.getTransaction());
        assertEquals(1, alert.getViolatedRules().size());
        assertEquals(FraudAlert.FraudSeverity.MEDIUM, alert.getSeverity());
        assertTrue(alert.getMessage().contains("FRAUD DETECTED"));
    }
    
    @Test
    @DisplayName("Should create alert with all violated rules")
    void shouldIncludeAllViolatedRules() {
        Transaction transaction = createTransaction();
        
        when(rule1.isFraudulent(transaction)).thenReturn(true);
        when(rule1.getReason(transaction)).thenReturn("Rule 1 violated");
        when(rule2.isFraudulent(transaction)).thenReturn(true);
        when(rule2.getReason(transaction)).thenReturn("Rule 2 violated");
        when(rule3.isFraudulent(transaction)).thenReturn(false);
        
        FraudAlert alert = engine.analyzeTransaction(transaction);
        
        assertNotNull(alert);
        assertEquals(2, alert.getViolatedRules().size());
        assertEquals(FraudAlert.FraudSeverity.HIGH, alert.getSeverity());
    }
    
    @Test
    @DisplayName("Should set MEDIUM severity for 1 rule violation")
    void shouldSetMediumSeverityForOneViolation() {
        Transaction transaction = createTransaction();
        when(rule1.isFraudulent(transaction)).thenReturn(true);
        when(rule1.getReason(transaction)).thenReturn("Violation");
        when(rule2.isFraudulent(transaction)).thenReturn(false);
        when(rule3.isFraudulent(transaction)).thenReturn(false);
        
        FraudAlert alert = engine.analyzeTransaction(transaction);
        
        assertEquals(FraudAlert.FraudSeverity.MEDIUM, alert.getSeverity());
    }
    
    @Test
    @DisplayName("Should set HIGH severity for 2 rule violations")
    void shouldSetHighSeverityForTwoViolations() {
        Transaction transaction = createTransaction();
        when(rule1.isFraudulent(transaction)).thenReturn(true);
        when(rule1.getReason(transaction)).thenReturn("Violation 1");
        when(rule2.isFraudulent(transaction)).thenReturn(true);
        when(rule2.getReason(transaction)).thenReturn("Violation 2");
        when(rule3.isFraudulent(transaction)).thenReturn(false);
        
        FraudAlert alert = engine.analyzeTransaction(transaction);
        
        assertEquals(FraudAlert.FraudSeverity.HIGH, alert.getSeverity());
    }
    
    @Test
    @DisplayName("Should set CRITICAL severity for 3+ rule violations")
    void shouldSetCriticalSeverityForThreeViolations() {
        Transaction transaction = createTransaction();
        when(rule1.isFraudulent(transaction)).thenReturn(true);
        when(rule1.getReason(transaction)).thenReturn("Violation 1");
        when(rule2.isFraudulent(transaction)).thenReturn(true);
        when(rule2.getReason(transaction)).thenReturn("Violation 2");
        when(rule3.isFraudulent(transaction)).thenReturn(true);
        when(rule3.getReason(transaction)).thenReturn("Violation 3");
        
        FraudAlert alert = engine.analyzeTransaction(transaction);
        
        assertEquals(FraudAlert.FraudSeverity.CRITICAL, alert.getSeverity());
    }
    
    @Test
    @DisplayName("Should handle rule exceptions gracefully")
    void shouldHandleRuleExceptions() {
        Transaction transaction = createTransaction();
        
        when(rule1.isFraudulent(transaction)).thenThrow(new RuntimeException("Rule error"));
        when(rule2.isFraudulent(transaction)).thenReturn(true);
        when(rule2.getReason(transaction)).thenReturn("Rule 2 violated");
        when(rule3.isFraudulent(transaction)).thenReturn(false);
        
        FraudAlert alert = engine.analyzeTransaction(transaction);
        
        // Should still process other rules
        assertNotNull(alert);
        assertEquals(1, alert.getViolatedRules().size());
    }
    
    @Test
    @DisplayName("Should execute all rules even after finding violations")
    void shouldExecuteAllRules() {
        Transaction transaction = createTransaction();
        
        when(rule1.isFraudulent(transaction)).thenReturn(true);
        when(rule1.getReason(transaction)).thenReturn("Violation 1");
        when(rule2.isFraudulent(transaction)).thenReturn(false);
        when(rule3.isFraudulent(transaction)).thenReturn(true);
        when(rule3.getReason(transaction)).thenReturn("Violation 3");
        
        engine.analyzeTransaction(transaction);
        
        // All rules should be checked
        verify(rule1).isFraudulent(transaction);
        verify(rule2).isFraudulent(transaction);
        verify(rule3).isFraudulent(transaction);
    }
    
    private Transaction createTransaction() {
        return Transaction.builder()
                .transactionId("TX001")
                .accountId("ACCT100")
                .amount(BigDecimal.valueOf(5000))
                .currency("USD")
                .timestamp(Instant.now())
                .build();
    }
}

