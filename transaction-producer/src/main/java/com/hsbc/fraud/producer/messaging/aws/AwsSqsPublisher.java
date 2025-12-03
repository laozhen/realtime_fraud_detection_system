package com.hsbc.fraud.producer.messaging.aws;

import com.hsbc.fraud.producer.logging.MetricsLogger;
import com.hsbc.fraud.producer.messaging.MessagePublisher;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AWS SQS implementation of MessagePublisher.
 * Emits CloudWatch metrics via structured logging for monitoring.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cloud.provider", havingValue = "aws")
public class AwsSqsPublisher implements MessagePublisher {
    
    private static final long HIGH_LATENCY_THRESHOLD_MS = 500;
    
    private final SqsTemplate sqsTemplate;
    
    @Value("${cloud.aws.sqs.queue-name}")
    private String queueName;
    
    @Override
    public void publish(String message) {
        long startTime = System.currentTimeMillis();
        
        try {
            sqsTemplate.send(to -> to
                    .queue(queueName)
                    .payload(message));
            
            long publishTime = System.currentTimeMillis() - startTime;
            
            log.debug("Published message to SQS queue: {} ({}ms)", queueName, publishTime);
            
            // Check for high latency and emit metric if needed
            if (publishTime > HIGH_LATENCY_THRESHOLD_MS) {
                MetricsLogger.logHighLatency("sqs-publish", publishTime);
            }
            
        } catch (Exception e) {
            log.error("Failed to publish message to SQS", e);
            MetricsLogger.logProcessingError("SQS_PUBLISH_ERROR", "unknown", e);
            throw new RuntimeException("Failed to publish to SQS", e);
        }
    }
}

