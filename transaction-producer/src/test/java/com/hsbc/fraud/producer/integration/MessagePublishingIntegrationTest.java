package com.hsbc.fraud.producer.integration;

import com.hsbc.fraud.producer.model.Transaction;
import com.hsbc.fraud.producer.service.TransactionGenerator;
import com.hsbc.fraud.producer.service.TransactionPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Integration test for transaction publishing using Testcontainers.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Message Publishing Integration Tests")
class MessagePublishingIntegrationTest {
    
    private static final String QUEUE_NAME = "fraud-detection-queue-test";
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SQS);
    
    @Autowired
    private TransactionGenerator transactionGenerator;
    
    @Autowired
    private TransactionPublisherService publisherService;
    
    private static String queueUrl;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("cloud.provider", () -> "aws");
        registry.add("cloud.aws.region", () -> localstack.getRegion());
        registry.add("cloud.aws.sqs.queue-name", () -> QUEUE_NAME);
        registry.add("cloud.aws.sqs.endpoint", localstack::getEndpoint);
        registry.add("spring.cloud.aws.region.static", () -> localstack.getRegion());
        registry.add("spring.cloud.aws.credentials.access-key", () -> localstack.getAccessKey());
        registry.add("spring.cloud.aws.credentials.secret-key", () -> localstack.getSecretKey());
    }
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public SqsAsyncClient sqsAsyncClient() {
            return SqsAsyncClient.builder()
                    .region(Region.of(localstack.getRegion()))
                    .endpointOverride(localstack.getEndpoint())
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    localstack.getAccessKey(),
                                    localstack.getSecretKey())))
                    .build();
        }
    }
    
    @BeforeEach
    void setUp(@Autowired SqsAsyncClient sqsClient) throws Exception {
        // Create queue if it doesn't exist
        if (queueUrl == null) {
            var createQueueResponse = sqsClient.createQueue(CreateQueueRequest.builder()
                    .queueName(QUEUE_NAME)
                    .build()).get();
            queueUrl = createQueueResponse.queueUrl();
        }
    }
    
    @Test
    @DisplayName("Should successfully publish transaction to queue")
    void shouldPublishTransaction(@Autowired SqsAsyncClient sqsClient) {
        Transaction transaction = transactionGenerator.generateTransaction();
        
        assertDoesNotThrow(() -> publisherService.publishTransaction(transaction));
        
        // Verify message arrived in queue
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var response = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributeNames("ApproximateNumberOfMessages")
                            .build()).get();
                    
                    int messageCount = Integer.parseInt(
                            response.attributes().get("ApproximateNumberOfMessages"));
                    
                    assertTrue(messageCount > 0, "Message should be in queue");
                });
    }
    
    @Test
    @DisplayName("Should publish multiple transactions")
    void shouldPublishMultipleTransactions(@Autowired SqsAsyncClient sqsClient) {
        int count = 5;
        
        for (int i = 0; i < count; i++) {
            Transaction transaction = transactionGenerator.generateTransaction();
            assertDoesNotThrow(() -> publisherService.publishTransaction(transaction));
        }
        
        // Verify all messages arrived
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var response = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributeNames("ApproximateNumberOfMessages")
                            .build()).get();
                    
                    int messageCount = Integer.parseInt(
                            response.attributes().get("ApproximateNumberOfMessages"));
                    
                    assertTrue(messageCount >= count, "All messages should be in queue");
                });
    }
}

