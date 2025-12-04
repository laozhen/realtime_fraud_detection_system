package com.hsbc.fraud.producer.scheduler;

import com.hsbc.fraud.producer.model.Transaction;
import com.hsbc.fraud.producer.service.TransactionGenerator;
import com.hsbc.fraud.producer.service.TransactionPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Scheduled task that continuously generates transactions at a configurable rate.
 * Can be enabled/disabled via configuration.
 * The transaction rate can be dynamically adjusted via the REST API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "transaction.auto-generate.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionScheduler {
    
    private final TransactionGenerator transactionGenerator;
    private final TransactionPublisherService publisherService;
    
    // Target: configurable QPS
    // Execution frequency: every 10ms (100 executions per second)
    // Transactions per execution: configurable (dynamic)
    // Default: 10 transactions per 10ms = 1,00 QPS
    private final AtomicInteger transactionsPerExecution = new AtomicInteger(1);
    private static final long EXECUTION_INTERVAL_MS = 10;
    
    private final AtomicLong totalPublished = new AtomicLong(0);
    
    /**
     * Logs when the scheduler is enabled and initialized.
     */
    @PostConstruct
    public void init() {
        log.info("Transaction auto-generation scheduler is ENABLED. Initial rate: {} QPS ({} transactions per {}ms execution)", 
                getCurrentQPS(), transactionsPerExecution.get(), EXECUTION_INTERVAL_MS);
    }
    
    /**
     * Get the current transactions per execution.
     */
    public int getTransactionsPerExecution() {
        return transactionsPerExecution.get();
    }
    
    /**
     * Set the transactions per execution (must be between 1 and 1000).
     * This dynamically adjusts the transaction generation rate.
     * 
     * @param count Number of transactions per execution (1-1000)
     * @return true if updated successfully, false if invalid
     */
    public boolean setTransactionsPerExecution(int count) {
        int oldValue = transactionsPerExecution.getAndSet(count);
        log.info("Updated transaction rate from {} QPS to {} QPS ({} -> {} transactions per {}ms)", 
                calculateQPS(oldValue), calculateQPS(count), oldValue, count, EXECUTION_INTERVAL_MS);
        return true;
    }
    
    /**
     * Get the current QPS (queries per second) based on current configuration.
     */
    public int getCurrentQPS() {
        return calculateQPS(transactionsPerExecution.get());
    }
    
    /**
     * Calculate QPS from transactions per execution.
     */
    private int calculateQPS(int transactionsPerExec) {
        return (int) (transactionsPerExec * (1000.0 / EXECUTION_INTERVAL_MS));
    }
    
    /**
     * Get the total number of transactions published.
     */
    public long getTotalPublished() {
        return totalPublished.get();
    }
    
    /**
     * Generates and publishes transactions at a configurable rate.
     * Executes every 10ms, publishing a dynamically configurable number of transactions per execution.
     */
    @Scheduled(fixedRate = EXECUTION_INTERVAL_MS)
    public void generateTransactions() {
        try {
            long startTime = System.currentTimeMillis();
            int batchSize = transactionsPerExecution.get();
            
            // Generate and publish transactions in parallel for better throughput
            IntStream.range(0, batchSize)
                    .parallel()
                    .forEach(i -> {
                        try {
                            Transaction transaction = transactionGenerator.generateTransaction();
                            publisherService.publishTransaction(transaction);
                            totalPublished.incrementAndGet();
                        } catch (Exception e) {
                            log.error("Error publishing transaction in batch", e);
                        }
                    });
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Log periodically (every 1000 transactions) to avoid log spam
            long currentTotal = totalPublished.get();
            if (currentTotal % 1000 == 0) {
                log.info("Published {} transactions total. Last batch took {}ms (current rate: {} QPS)", 
                        currentTotal, executionTime, getCurrentQPS());
            }
        } catch (Exception e) {
            log.error("Error in scheduled transaction generation", e);
        }
    }
}

