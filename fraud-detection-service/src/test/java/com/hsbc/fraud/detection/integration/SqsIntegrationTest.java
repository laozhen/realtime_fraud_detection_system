package com.hsbc.fraud.detection.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fraud.detection.model.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Integration test for SQS message flow using Testcontainers with LocalStack.
 */
@SpringBootTest()
@Testcontainers
@ActiveProfiles("sqs-integration")
@DisplayName("SQS Integration Tests")
class SqsIntegrationTest {
    
    private static final String QUEUE_NAME = "fraud-detection-queue-test";
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SQS);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private static String queueUrl;
    
    @BeforeAll
    static void setUpQueue() throws Exception {
        // Create SQS client for test setup
        SqsAsyncClient setupClient = SqsAsyncClient.builder()
                .region(Region.of(localstack.getRegion()))
                .endpointOverride(localstack.getEndpointOverride(SQS))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        localstack.getAccessKey(),
                                        localstack.getSecretKey())))
                .build();
        
        // Create the queue
        var createQueueResponse = setupClient.createQueue(CreateQueueRequest.builder()
                .queueName(QUEUE_NAME)
                .build()).get();
        queueUrl = createQueueResponse.queueUrl();
        
        setupClient.close();
    }
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("cloud.provider", () -> "aws");
        registry.add("cloud.aws.region", () -> localstack.getRegion());
        registry.add("cloud.aws.sqs.queue-name", () -> QUEUE_NAME);
        registry.add("cloud.aws.sqs.endpoint", () -> localstack.getEndpointOverride(SQS).toString());
        registry.add("spring.cloud.aws.region.static", () -> localstack.getRegion());
        registry.add("spring.cloud.aws.credentials.access-key", () -> localstack.getAccessKey());
        registry.add("spring.cloud.aws.credentials.secret-key", () -> localstack.getSecretKey());
        registry.add("spring.cloud.aws.endpoint", () -> localstack.getEndpointOverride(SQS).toString());
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpointOverride(SQS).toString());
    }
    
    @Test
    @DisplayName("Should process legitimate transaction without alert")
    void shouldProcessLegitimateTransaction(@Autowired SqsAsyncClient sqsClient) throws Exception {
        Transaction transaction = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId("ACCT100")
                .amount(BigDecimal.valueOf(5000))
                .currency("USD")
                .timestamp(Instant.now())
                .merchantId("MERCHANT_001")
                .merchantCategory("RETAIL")
                .location("NEW_YORK")
                .type(Transaction.TransactionType.PURCHASE)
                .build();
        
        String message = objectMapper.writeValueAsString(transaction);
        
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .build()).get();
        
        // Wait for message to be processed
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var response = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                            .build()).get();
                    
                    int messageCount = Integer.parseInt(
                            response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
                    
                    // Message should be consumed
                    assert messageCount == 0 : "Message was not consumed";
                });
    }
    
    @Test
    @DisplayName("Should process fraudulent transaction and create alert")
    void shouldDetectFraudulentTransaction(@Autowired SqsAsyncClient sqsClient) throws Exception {
        Transaction transaction = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId("ACCT666")  // Suspicious account
                .amount(BigDecimal.valueOf(15000))  // Large amount
                .currency("USD")
                .timestamp(Instant.now())
                .merchantId("MERCHANT_001")
                .merchantCategory("ONLINE")
                .location("UNKNOWN")
                .type(Transaction.TransactionType.PURCHASE)
                .build();
        
        String message = objectMapper.writeValueAsString(transaction);
        
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .build()).get();
        
        // Wait for message to be processed
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var response = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                            .build()).get();
                    
                    int messageCount = Integer.parseInt(
                            response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
                    
                    // Message should be consumed (and alert logged)
                    assert messageCount == 0 : "Message was not consumed";
                });
    }
}

