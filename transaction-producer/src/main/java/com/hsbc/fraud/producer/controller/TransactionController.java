package com.hsbc.fraud.producer.controller;

import com.hsbc.fraud.producer.model.Transaction;
import com.hsbc.fraud.producer.service.TransactionGenerator;
import com.hsbc.fraud.producer.service.TransactionPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for triggering transaction generation and publishing.
 * Useful for testing and demonstration purposes.
 */
@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    
    private final TransactionGenerator transactionGenerator;
    private final TransactionPublisherService publisherService;
    
    /**
     * Generate and publish a single random transaction.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateSingleTransaction() {
        Transaction transaction = transactionGenerator.generateTransaction();
        publisherService.publishTransaction(transaction);
        
        log.info("Generated and published transaction: {}", transaction.getTransactionId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("transaction", transaction);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Generate and publish multiple transactions.
     */
    @PostMapping("/generate/batch")
    public ResponseEntity<Map<String, Object>> generateBatchTransactions(
            @RequestParam(defaultValue = "10") int count) {
        
        if (count > 1000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Maximum batch size is 1000"));
        }
        
        int published = 0;
        for (int i = 0; i < count; i++) {
            Transaction transaction = transactionGenerator.generateTransaction();
            publisherService.publishTransaction(transaction);
            published++;
        }
        
        log.info("Generated and published {} transactions", published);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("count", published);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Generate rapid-fire transactions to test the RapidFireRule.
     */
    @PostMapping("/generate/rapid-fire")
    public ResponseEntity<Map<String, Object>> generateRapidFireTransactions(
            @RequestParam(defaultValue = "10") int count) {
        
        List<Transaction> transactions = transactionGenerator.generateRapidFireBurst(count);
        transactions.forEach(publisherService::publishTransaction);
        
        log.info("Generated and published {} rapid-fire transactions", transactions.size());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("count", transactions.size());
        response.put("accountId", transactions.get(0).getAccountId());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}

