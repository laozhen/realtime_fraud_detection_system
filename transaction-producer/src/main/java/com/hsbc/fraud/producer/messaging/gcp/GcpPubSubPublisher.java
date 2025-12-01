package com.hsbc.fraud.producer.messaging.gcp;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.hsbc.fraud.producer.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * GCP Pub/Sub implementation of MessagePublisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cloud.provider", havingValue = "gcp")
public class GcpPubSubPublisher implements MessagePublisher {
    
    private final PubSubTemplate pubSubTemplate;
    
    @Value("${cloud.gcp.pubsub.topic-name}")
    private String topicName;
    
    @Override
    public void publish(String message) {
        try {
            pubSubTemplate.publish(topicName, message);
            log.debug("Published message to Pub/Sub topic: {}", topicName);
        } catch (Exception e) {
            log.error("Failed to publish message to Pub/Sub", e);
            throw new RuntimeException("Failed to publish to Pub/Sub", e);
        }
    }
}

