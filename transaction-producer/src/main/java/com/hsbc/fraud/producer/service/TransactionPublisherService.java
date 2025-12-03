package com.hsbc.fraud.producer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fraud.producer.logging.MetricsLogger;
import com.hsbc.fraud.producer.model.Transaction;
import com.hsbc.fraud.producer.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service that publishes transactions to the message queue.
 * Uses the MessagePublisher abstraction to support multiple cloud providers.
 * Emits CloudWatch metrics via structured logging for monitoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionPublisherService {
    
    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
    
    public void publishTransaction(Transaction transaction) {
        long startTime = System.currentTimeMillis();
        
        try {
            String message = objectMapper.writeValueAsString(transaction);
            messagePublisher.publish(message);
            
            long publishTime = System.currentTimeMillis() - startTime;
            
            // Emit CloudWatch metric: Transaction Sent
            MetricsLogger.logTransactionSent(
                transaction.getTransactionId(),
                transaction.getAccountId(),
                publishTime
            );
            
            log.debug("Published transaction: {} from account: {} amount: {} ({}ms)",
                    transaction.getTransactionId(),
                    transaction.getAccountId(),
                    transaction.getAmount(),
                    publishTime);
            
        } catch (Exception e) {
            log.error("Failed to publish transaction: {}", transaction.getTransactionId(), e);
            MetricsLogger.logProcessingError("PUBLISH_ERROR", transaction.getTransactionId(), e);
            throw new RuntimeException("Failed to publish transaction", e);
        }
    }
}

