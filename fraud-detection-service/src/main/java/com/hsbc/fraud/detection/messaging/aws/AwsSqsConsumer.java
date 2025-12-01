package com.hsbc.fraud.detection.messaging.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fraud.detection.messaging.MessageConsumer;
import com.hsbc.fraud.detection.messaging.TransactionMessageHandler;
import com.hsbc.fraud.detection.model.Transaction;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AWS SQS implementation of MessageConsumer.
 * Uses Spring Cloud AWS annotations for automatic message consumption.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cloud.provider", havingValue = "aws")
public class AwsSqsConsumer implements MessageConsumer {
    
    private final ObjectMapper objectMapper;
    private final TransactionMessageHandler messageHandler;
    
    @Override
    public void startListening() {
        // No-op: Spring handles listener lifecycle with @SqsListener
        log.info("AWS SQS Consumer initialized and listening");
    }
    
    @SqsListener("${cloud.aws.sqs.queue-name}")
    public void receiveMessage(String message) {
        log.debug("Received message from SQS");
        
        try {
            Transaction transaction = objectMapper.readValue(message, Transaction.class);
            messageHandler.handleTransaction(transaction);
        } catch (Exception e) {
            log.error("Error processing SQS message: {}", message, e);
            throw new RuntimeException("Failed to process message", e);
        }
    }
}

