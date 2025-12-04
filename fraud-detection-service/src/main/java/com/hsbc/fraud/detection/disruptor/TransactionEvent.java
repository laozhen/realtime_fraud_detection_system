package com.hsbc.fraud.detection.disruptor;

import com.hsbc.fraud.detection.model.Transaction;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event object that travels through the Disruptor ring buffer.
 * Contains transaction data and the SQS acknowledgment handle.
 * 
 * Pre-allocated in the ring buffer to avoid garbage collection overhead.
 */
@Data
@NoArgsConstructor
public class TransactionEvent {
    
    /**
     * The transaction to be processed for fraud detection.
     */
    private Transaction transaction;
    
    /**
     * SQS acknowledgment handle for manual message acknowledgment.
     * Must be called after successful processing to remove message from queue.
     */
    private Acknowledgement acknowledgement;
    
    /**
     * Sequence number from the ring buffer for tracking.
     */
    private long sequence;
    
    /**
     * Timestamp when the event was published to the ring buffer.
     */
    private long publishTimestamp;
    
    /**
     * Clear the event data for reuse (called automatically by Disruptor).
     */
    public void clear() {
        this.transaction = null;
        this.acknowledgement = null;
        this.sequence = 0;
        this.publishTimestamp = 0;
    }
}


