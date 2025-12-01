package com.hsbc.fraud.producer.messaging;

/**
 * Abstraction for publishing messages to different cloud message queues.
 * Enables switching between AWS SQS, GCP Pub/Sub, or local implementations.
 */
public interface MessagePublisher {
    
    /**
     * Publishes a message to the configured message queue/topic.
     * 
     * @param message The message content to publish
     */
    void publish(String message);
}

