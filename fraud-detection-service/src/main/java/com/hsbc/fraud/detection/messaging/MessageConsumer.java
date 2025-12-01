package com.hsbc.fraud.detection.messaging;

/**
 * Abstraction for consuming messages from different cloud message queues.
 * Implementations handle the specifics of AWS SQS, GCP Pub/Sub, or local queues.
 */
public interface MessageConsumer {
    
    /**
     * Start listening for messages. Implementation-specific.
     * For annotation-based consumers (@SqsListener), this may be a no-op.
     */
    void startListening();
}

