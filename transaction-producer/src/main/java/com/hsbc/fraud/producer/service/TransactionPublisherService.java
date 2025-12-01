package com.hsbc.fraud.producer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fraud.producer.model.Transaction;
import com.hsbc.fraud.producer.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service that publishes transactions to the message queue.
 * Uses the MessagePublisher abstraction to support multiple cloud providers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionPublisherService {
    
    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
    
    public void publishTransaction(Transaction transaction) {
        try {
            String message = objectMapper.writeValueAsString(transaction);
            messagePublisher.publish(message);
            
            log.debug("Published transaction: {} from account: {} amount: {}",
                    transaction.getTransactionId(),
                    transaction.getAccountId(),
                    transaction.getAmount());
            
        } catch (Exception e) {
            log.error("Failed to publish transaction: {}", transaction.getTransactionId(), e);
            throw new RuntimeException("Failed to publish transaction", e);
        }
    }
}

