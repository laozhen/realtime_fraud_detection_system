package com.hsbc.fraud.detection.disruptor;

import com.hsbc.fraud.detection.model.Transaction;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Service that manages the Disruptor lifecycle and provides API for publishing events.
 * 
 * Responsibilities:
 * - Initialize and start the Disruptor
 * - Provide thread-safe publishing of transaction events
 * - Graceful shutdown with configurable timeout
 * - Track publishing metrics
 */
@Slf4j
@Service
public class DisruptorService {
    
    private final Disruptor<TransactionEvent> disruptor;
    private final RingBuffer<TransactionEvent> ringBuffer;
    private final Counter publishedCounter;
    private final Counter publishFailedCounter;
    private final int shutdownTimeoutSeconds;
    
    public DisruptorService(
            Disruptor<TransactionEvent> disruptor,
            MeterRegistry meterRegistry,
            int shutdownTimeoutSeconds) {
        this.disruptor = disruptor;
        this.ringBuffer = disruptor.getRingBuffer();
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        
        // Initialize metrics
        this.publishedCounter = Counter.builder("disruptor.events.published")
                .description("Number of events published to ring buffer")
                .register(meterRegistry);
        
        this.publishFailedCounter = Counter.builder("disruptor.events.publish.failed")
                .description("Number of events that failed to publish to ring buffer")
                .register(meterRegistry);
        
        // Start the Disruptor
        disruptor.start();
        log.info("Disruptor started with ring buffer size: {}", ringBuffer.getBufferSize());
    }
    
    /**
     * Publish a transaction event to the ring buffer.
     * 
     * @param transaction The transaction to process
     * @param acknowledgement The SQS acknowledgment handle
     * @throws IllegalStateException if ring buffer is full
     */
    public void publishEvent(Transaction transaction, Acknowledgement acknowledgement) {
        try {
            long sequence = ringBuffer.next();
            
            try {
                TransactionEvent event = ringBuffer.get(sequence);
                event.setTransaction(transaction);
                event.setAcknowledgement(acknowledgement);
                event.setSequence(sequence);
                event.setPublishTimestamp(System.currentTimeMillis());
                
                log.debug("Published transaction {} to ring buffer at sequence {}", 
                        transaction.getTransactionId(), sequence);
                
                publishedCounter.increment();
                
            } finally {
                ringBuffer.publish(sequence);
            }
            
        } catch (Exception e) {
            publishFailedCounter.increment();
            log.error("Failed to publish transaction {} to ring buffer: {}", 
                    transaction.getTransactionId(), e.getMessage(), e);
            throw new IllegalStateException("Ring buffer is full or unavailable", e);
        }
    }
    
    /**
     * Get current ring buffer utilization percentage.
     * 
     * @return utilization percentage (0-100)
     */
    public double getRingBufferUtilization() {
        long bufferSize = ringBuffer.getBufferSize();
        long remainingCapacity = ringBuffer.remainingCapacity();
        long used = bufferSize - remainingCapacity;
        return (used * 100.0) / bufferSize;
    }
    
    /**
     * Get remaining capacity in the ring buffer.
     * 
     * @return number of available slots
     */
    public long getRemainingCapacity() {
        return ringBuffer.remainingCapacity();
    }
    
    /**
     * Gracefully shutdown the Disruptor.
     * Waits for in-flight events to complete before shutting down.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Disruptor... (timeout: {}s)", shutdownTimeoutSeconds);
        
        try {
            // Wait for all events to be processed
            disruptor.shutdown(shutdownTimeoutSeconds, TimeUnit.SECONDS);
            log.info("Disruptor shutdown completed successfully");
            
        } catch (Exception e) {
            log.error("Error during Disruptor shutdown: {}", e.getMessage(), e);
            // Force shutdown if graceful shutdown fails
            disruptor.halt();
            log.warn("Disruptor halted forcefully");
        }
    }
}

