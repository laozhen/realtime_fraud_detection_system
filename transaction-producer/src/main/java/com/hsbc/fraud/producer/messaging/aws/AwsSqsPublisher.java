package com.hsbc.fraud.producer.messaging.aws;

import com.hsbc.fraud.producer.messaging.MessagePublisher;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AWS SQS implementation of MessagePublisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cloud.provider", havingValue = "aws")
public class AwsSqsPublisher implements MessagePublisher {
    
    private final SqsTemplate sqsTemplate;
    
    @Value("${cloud.aws.sqs.queue-name}")
    private String queueName;
    
    @Override
    public void publish(String message) {
        try {
            sqsTemplate.send(to -> to
                    .queue(queueName)
                    .payload(message));
            
            log.debug("Published message to SQS queue: {}", queueName);
        } catch (Exception e) {
            log.error("Failed to publish message to SQS", e);
            throw new RuntimeException("Failed to publish to SQS", e);
        }
    }
}

