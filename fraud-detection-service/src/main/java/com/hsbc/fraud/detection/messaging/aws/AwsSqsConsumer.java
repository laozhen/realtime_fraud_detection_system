package com.hsbc.fraud.detection.messaging.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fraud.detection.disruptor.DisruptorService;
import com.hsbc.fraud.detection.messaging.MessageConsumer;
import com.hsbc.fraud.detection.model.Transaction;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * AWS SQS implementation of MessageConsumer.
 * Uses Spring Cloud AWS with manual acknowledgment and Disruptor for low-latency processing.
 * 
 * Flow:
 * 1. Receive message from SQS
 * 2. Parse transaction
 * 3. Publish to Disruptor ring buffer
 * 4. Disruptor processes asynchronously
 * 5. Acknowledgment happens in event handler after successful processing
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cloud.provider", havingValue = "aws")
public class AwsSqsConsumer implements MessageConsumer {
    
    private final ObjectMapper objectMapper;
    private final DisruptorService disruptorService;
    
    @Override
    public void startListening() {
        // No-op: Spring handles listener lifecycle with @SqsListener
        log.info("AWS SQS Consumer initialized with manual acknowledgment and Disruptor processing");
    }
    
    /**
     * Receive messages from SQS with manual acknowledgment.
     * Messages are published to Disruptor for asynchronous processing.
     * 
     * Note: Manual acknowledgment is enabled by injecting the Acknowledgement parameter.
     */
    @SqsListener(value = "${cloud.aws.sqs.queue-name}")
    public void receiveMessage(Message<String> message, Acknowledgement acknowledgement) {
        String payload = message.getPayload();
        log.debug("Received message from SQS, publishing to Disruptor ring buffer");
        
        try {
            // Parse transaction
            Transaction transaction = objectMapper.readValue(payload, Transaction.class);
            
            // Publish to Disruptor ring buffer with acknowledgment handle
            // The event handler will acknowledge after successful processing
            disruptorService.publishEvent(transaction, acknowledgement);
            
            log.debug("Transaction {} published to Disruptor", transaction.getTransactionId());
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse transaction JSON: {}", payload, e);
            // Invalid JSON - acknowledge to remove from queue (don't retry bad data)
            acknowledgement.acknowledge();
            
        } catch (IllegalStateException e) {
            log.error("Ring buffer is full, message will be retried: {}", e.getMessage());
            // Don't acknowledge - let SQS retry after visibility timeout            
        } catch (Exception e) {
            log.error("Unexpected error processing SQS message: {}", payload, e);
            // Don't acknowledge - let SQS retry or move to DLQ after max attempts
        }
    }
}

