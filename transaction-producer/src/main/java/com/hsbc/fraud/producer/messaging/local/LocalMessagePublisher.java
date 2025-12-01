package com.hsbc.fraud.producer.messaging.local;

import com.hsbc.fraud.producer.messaging.MessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Local in-memory implementation of MessagePublisher for development/testing.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cloud.provider", havingValue = "local", matchIfMissing = true)
public class LocalMessagePublisher implements MessagePublisher {
    
    // Shared queue for local testing
    private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    
    @Override
    public void publish(String message) {
        try {
            messageQueue.put(message);
            log.debug("Published message to local queue (size: {})", messageQueue.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to publish to local queue", e);
        }
    }
    
    public static BlockingQueue<String> getMessageQueue() {
        return messageQueue;
    }
}

