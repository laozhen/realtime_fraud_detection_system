package com.hsbc.fraud.detection.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * Factory for creating TransactionEvent instances.
 * Used by Disruptor to pre-allocate events in the ring buffer.
 * 
 * Pre-allocation eliminates garbage collection overhead during high-throughput processing.
 */
public class TransactionEventFactory implements EventFactory<TransactionEvent> {
    
    @Override
    public TransactionEvent newInstance() {
        return new TransactionEvent();
    }
}

