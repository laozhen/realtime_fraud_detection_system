package com.hsbc.fraud.detection.messaging.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fraud.detection.messaging.MessageConsumer;
import com.hsbc.fraud.detection.messaging.TransactionMessageHandler;
import com.hsbc.fraud.detection.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local in-memory implementation of MessageConsumer for development/testing.
 * Can be shared with LocalMessagePublisher via static queue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cloud.provider", havingValue = "local", matchIfMissing = true)
public class LocalMessageConsumer implements MessageConsumer {
    
    private final ObjectMapper objectMapper;
    private final TransactionMessageHandler messageHandler;
    
    // Shared queue with producer for local testing
    private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;
    
    @PostConstruct
    @Override
    public void startListening() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting local message consumer");
            consumerThread = new Thread(this::consumeMessages);
            consumerThread.setName("LocalMessageConsumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
        }
    }
    
    @PreDestroy
    public void stopListening() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        log.info("Stopped local message consumer");
    }
    
    private void consumeMessages() {
        while (running.get()) {
            try {
                String message = messageQueue.take();
                log.debug("Received message from local queue");
                
                Transaction transaction = objectMapper.readValue(message, Transaction.class);
                messageHandler.handleTransaction(transaction);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing local message", e);
            }
        }
    }
    
    public static BlockingQueue<String> getMessageQueue() {
        return messageQueue;
    }
}

