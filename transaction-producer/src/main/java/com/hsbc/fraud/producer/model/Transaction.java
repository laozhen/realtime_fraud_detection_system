package com.hsbc.fraud.producer.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transaction model for the producer service.
 * Kept identical to the fraud-detection-service model for compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    private String transactionId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant timestamp;
    
    private String merchantId;
    private String merchantCategory;
    private String location;
    private TransactionType type;
    
    public enum TransactionType {
        PURCHASE, WITHDRAWAL, TRANSFER, REFUND
    }
}

