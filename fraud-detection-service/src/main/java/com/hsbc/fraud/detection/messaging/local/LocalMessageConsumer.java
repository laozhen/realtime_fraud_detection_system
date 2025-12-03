package com.hsbc.fraud.detection.messaging.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fraud.detection.disruptor.DisruptorService;
import com.hsbc.fraud.detection.messaging.MessageConsumer;
import com.hsbc.fraud.detection.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Uses Disruptor for low-latency processing, consistent with AwsSqsConsumer.
 * 
 * Flow:
 * 1. Receive message from local queue
 * 2. Parse transaction
 * 3. Publish to Disruptor ring buffer
 * 4. Disruptor processes asynchronously
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cloud.provider", havingValue = "local", matchIfMissing = true)
public class LocalMessageConsumer implements MessageConsumer {
    
    private final ObjectMapper objectMapper;
    private final DisruptorService disruptorService;
    
    // Shared queue with producer for local testing
    private static final BlockingQueue<String> SHARED_MESSAGE_QUEUE = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> messageQueue;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;
    
    @Autowired
    public LocalMessageConsumer(ObjectMapper objectMapper, DisruptorService disruptorService) {
        this(objectMapper, disruptorService, SHARED_MESSAGE_QUEUE);
    }
    
    LocalMessageConsumer(ObjectMapper objectMapper,
                         DisruptorService disruptorService,
                         BlockingQueue<String> messageQueue) {
        this.objectMapper = objectMapper;
        this.disruptorService = disruptorService;
        this.messageQueue = messageQueue;
    }
    
    @PostConstruct
    @Override
    public void startListening() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting local message consumer with Disruptor processing");
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
                log.debug("Received message from local queue, publishing to Disruptor ring buffer");
                
                processMessage(message);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Process a message from the local queue.
     * Mirrors the logic from AwsSqsConsumer for consistency.
     * 
     * @param payload the JSON message payload
     */
    void processMessage(String payload) {
        try {
            // Parse transaction
            Transaction transaction = objectMapper.readValue(payload, Transaction.class);
            
            // Publish to Disruptor ring buffer
            // No acknowledgement needed for local queue (null)
            disruptorService.publishEvent(transaction, null);
            
            log.debug("Transaction {} published to Disruptor", transaction.getTransactionId());
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse transaction JSON: {}", payload, e);
            // Invalid JSON - skip (equivalent to acknowledging bad data in SQS)
            
        } catch (IllegalStateException e) {
            log.error("Ring buffer is full, message will be dropped: {}", e.getMessage());
            // For local testing, we just log and continue
            // In production (SQS), message would be retried
            
        } catch (Exception e) {
            log.error("Unexpected error processing local message: {}", payload, e);
        }
    }
    
    public static BlockingQueue<String> getMessageQueue() {
        return SHARED_MESSAGE_QUEUE;
    }
    
    /**
     * Check if the consumer is currently running.
     * 
     * @return true if the consumer thread is active
     */
    public boolean isRunning() {
        return running.get();
    }
}

