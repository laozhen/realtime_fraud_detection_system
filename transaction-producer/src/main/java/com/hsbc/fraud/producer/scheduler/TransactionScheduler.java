package com.hsbc.fraud.producer.scheduler;

import com.hsbc.fraud.producer.model.Transaction;
import com.hsbc.fraud.producer.service.TransactionGenerator;
import com.hsbc.fraud.producer.service.TransactionPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that continuously generates transactions.
 * Can be enabled/disabled via configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "transaction.auto-generate.enabled", havingValue = "true", matchIfMissing = false)
public class TransactionScheduler {
    
    private final TransactionGenerator transactionGenerator;
    private final TransactionPublisherService publisherService;
    
    /**
     * Generates and publishes transactions every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000)
    public void generateTransactions() {
        try {
            Transaction transaction = transactionGenerator.generateTransaction();
            publisherService.publishTransaction(transaction);
            log.debug("Auto-generated transaction: {}", transaction.getTransactionId());
        } catch (Exception e) {
            log.error("Error in scheduled transaction generation", e);
        }
    }
}

