package com.hsbc.fraud.detection.service;

import com.hsbc.fraud.detection.model.FraudAlert;
import com.hsbc.fraud.detection.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertService Tests")
class AlertServiceTest {
    
    private AlertService alertService;
    
    @BeforeEach
    void setUp() {
        alertService = new AlertService();
    }
    
    @Test
    @DisplayName("Should handle alert without exceptions")
    void shouldHandleAlertWithoutExceptions() {
        FraudAlert alert = createAlert(FraudAlert.FraudSeverity.HIGH);
        
        assertDoesNotThrow(() -> alertService.handleAlert(alert));
    }
    
    @Test
    @DisplayName("Should handle alert with all severity levels")
    void shouldHandleAllSeverityLevels() {
        for (FraudAlert.FraudSeverity severity : FraudAlert.FraudSeverity.values()) {
            FraudAlert alert = createAlert(severity);
            assertDoesNotThrow(() -> alertService.handleAlert(alert));
        }
    }
    
    @Test
    @DisplayName("Should handle alert with null fields gracefully")
    void shouldHandleNullFields() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX001")
                .accountId("ACCT100")
                .build();
        
        FraudAlert alert = FraudAlert.builder()
                .alertId("ALERT001")
                .transaction(transaction)
                .violatedRules(Arrays.asList("RULE1"))
                .severity(FraudAlert.FraudSeverity.MEDIUM)
                .detectedAt(Instant.now())
                .message("Test alert")
                .build();
        
        assertDoesNotThrow(() -> alertService.handleAlert(alert));
    }
    
    private FraudAlert createAlert(FraudAlert.FraudSeverity severity) {
        Transaction transaction = Transaction.builder()
                .transactionId("TX001")
                .accountId("ACCT100")
                .amount(BigDecimal.valueOf(15000))
                .currency("USD")
                .timestamp(Instant.now())
                .build();
        
        return FraudAlert.builder()
                .alertId("ALERT001")
                .transaction(transaction)
                .violatedRules(Arrays.asList("LARGE_AMOUNT_RULE: Amount exceeds threshold"))
                .severity(severity)
                .detectedAt(Instant.now())
                .message("FRAUD DETECTED")
                .build();
    }
}

