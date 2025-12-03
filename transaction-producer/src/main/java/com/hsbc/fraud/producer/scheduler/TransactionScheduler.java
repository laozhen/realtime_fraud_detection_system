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

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Scheduled task that continuously generates transactions at 10,000 QPS.
 * Can be enabled/disabled via configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "transaction.auto-generate.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionScheduler {
    
    private final TransactionGenerator transactionGenerator;
    private final TransactionPublisherService publisherService;
    
    // Target: 10,000 QPS
    // Execution frequency: every 10ms (100 executions per second)
    // Transactions per execution: 100 (100 * 1 = 100 QPS)
    private static final int TRANSACTIONS_PER_EXECUTION = 1;
    private static final long EXECUTION_INTERVAL_MS = 10;
    
    private final AtomicLong totalPublished = new AtomicLong(0);
    
    /**
     * Logs when the scheduler is enabled and initialized.
     */
    @PostConstruct
    public void init() {
        log.info("Transaction auto-generation scheduler is ENABLED. Target rate: 10,000 QPS ({} transactions per {}ms execution)", 
                TRANSACTIONS_PER_EXECUTION, EXECUTION_INTERVAL_MS);
    }
    
    /**
     * Generates and publishes transactions at 10,000 QPS evenly distributed.
     * Executes every 10ms, publishing 100 transactions per execution.
     */
    @Scheduled(fixedRate = EXECUTION_INTERVAL_MS)
    public void generateTransactions() {
        try {
            long startTime = System.currentTimeMillis();
            
            // Generate and publish transactions in parallel for better throughput
            IntStream.range(0, TRANSACTIONS_PER_EXECUTION)
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
                log.info("Published {} transactions total. Last batch took {}ms", currentTotal, executionTime);
            }
        } catch (Exception e) {
            log.error("Error in scheduled transaction generation", e);
        }
    }
}

