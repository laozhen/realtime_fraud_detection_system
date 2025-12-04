package com.hsbc.fraud.producer.controller;

import com.hsbc.fraud.producer.model.Transaction;
import com.hsbc.fraud.producer.scheduler.TransactionScheduler;
import com.hsbc.fraud.producer.service.TransactionGenerator;
import com.hsbc.fraud.producer.service.TransactionPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for triggering transaction generation and publishing.
 * Also provides endpoints to dynamically control the transaction generation rate.
 * Useful for testing and demonstration purposes.
 */
@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    
    private final TransactionGenerator transactionGenerator;
    private final TransactionPublisherService publisherService;
    
    @Autowired(required = false)
    private TransactionScheduler transactionScheduler;
    
    
    /**
     * Get current transaction rate configuration.
     */
    @GetMapping("/rate")
    public ResponseEntity<Map<String, Object>> getTransactionRate() {
        if (transactionScheduler == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "disabled",
                    "message", "Transaction auto-generation scheduler is not enabled"
            ));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "enabled");
        response.put("transactionsPerExecution", transactionScheduler.getTransactionsPerExecution());
        response.put("executionIntervalMs", 10);
        response.put("currentQPS", transactionScheduler.getCurrentQPS());
        response.put("totalPublished", transactionScheduler.getTotalPublished());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update the transaction rate dynamically.
     * 
     * @param transactionsPerExecution Number of transactions to generate per execution (1-1000)
     *                                 Since executions happen every 10ms (100 times/second),
     *                                 this translates to: transactionsPerExecution * 100 = QPS
     *                                 Example: 10 -> 1,000 QPS, 100 -> 10,000 QPS
     */
    @PutMapping("/rate")
    public ResponseEntity<Map<String, Object>> updateTransactionRate(
            @RequestParam int transactionsPerExecution) {
        
        if (transactionScheduler == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", "error",
                            "message", "Transaction auto-generation scheduler is not enabled"
                    ));
        }
        
        boolean success = transactionScheduler.setTransactionsPerExecution(transactionsPerExecution);
        
        if (!success) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", "error",
                            "message", "Invalid value. Must be between 1 and 1000"
                    ));
        }
        
        log.info("Transaction rate updated via API to {} transactions per execution ({} QPS)", 
                transactionsPerExecution, transactionScheduler.getCurrentQPS());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("transactionsPerExecution", transactionScheduler.getTransactionsPerExecution());
        response.put("currentQPS", transactionScheduler.getCurrentQPS());
        response.put("message", "Transaction rate updated successfully");
        
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

