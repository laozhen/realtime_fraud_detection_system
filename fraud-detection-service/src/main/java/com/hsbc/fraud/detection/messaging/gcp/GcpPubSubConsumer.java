package com.hsbc.fraud.detection.messaging.gcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.subscriber.PubSubSubscriberTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.hsbc.fraud.detection.messaging.MessageConsumer;
import com.hsbc.fraud.detection.messaging.TransactionMessageHandler;
import com.hsbc.fraud.detection.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * GCP Pub/Sub implementation of MessageConsumer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cloud.provider", havingValue = "gcp")
public class GcpPubSubConsumer implements MessageConsumer {
    
    private final PubSubSubscriberTemplate subscriberTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionMessageHandler messageHandler;
    
    @Value("${cloud.gcp.pubsub.subscription-name}")
    private String subscriptionName;
    
    @PostConstruct
    @Override
    public void startListening() {
        log.info("Starting GCP Pub/Sub consumer for subscription: {}", subscriptionName);
        
        subscriberTemplate.subscribeAndConvert(
                subscriptionName,
                message -> {
                    try {
                        String payload = (String) message.getPayload();
                        log.debug("Received message from Pub/Sub");
                        
                        Transaction transaction = objectMapper.readValue(payload, Transaction.class);
                        messageHandler.handleTransaction(transaction);
                        
                        // Acknowledge message
                        ((BasicAcknowledgeablePubsubMessage) message).ack();
                    } catch (Exception e) {
                        log.error("Error processing Pub/Sub message", e);
                        ((BasicAcknowledgeablePubsubMessage) message).nack();
                    }
                },
                String.class
        );
    }
}

