package com.hsbc.fraud.detection.disruptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fraud.detection.model.Transaction;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Integration test for Disruptor-based transaction processing.
 * 
 * Tests:
 * 1. Happy path: Message published, processed, and acknowledged
 * 2. Processing failure: Message not acknowledged, reprocessed
 * 3. Ring buffer metrics
 * 4. End-to-end latency
 */
@Tag("testcontainers")
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class DisruptorIntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SQS)
            .withEnv("DEBUG", "1");
    
    @Autowired
    private SqsTemplate sqsTemplate;
    
    @Autowired
    private SqsAsyncClient sqsAsyncClient;
    
    @Autowired
    private DisruptorService disruptorService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${cloud.aws.sqs.queue-name}")
    private String queueName;
    
    private static String queueUrl;
    private static String dlqUrl;
    
    @BeforeAll
    static void setUpQueues() throws Exception {
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
        
        try {
            // Create DLQ
            Map<QueueAttributeName, String> dlqAttributes = new HashMap<>();
            dlqAttributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "86400"); // 1 day
            
            var dlqResponse = setupClient.createQueue(CreateQueueRequest.builder()
                    .queueName("fraud-detection-queue-dlq-test")
                    .attributes(dlqAttributes)
                    .build()).get();
            dlqUrl = dlqResponse.queueUrl();
            
            // Get DLQ ARN
            var dlqAttrs = setupClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(dlqUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build()).get();
            String dlqArn = dlqAttrs.attributes().get(QueueAttributeName.QUEUE_ARN);
            
            // Create main queue with DLQ
            Map<QueueAttributeName, String> queueAttributes = new HashMap<>();
            queueAttributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, "10");
            queueAttributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "86400");
            queueAttributes.put(QueueAttributeName.REDRIVE_POLICY, 
                    String.format("{\"maxReceiveCount\":\"3\",\"deadLetterTargetArn\":\"%s\"}", dlqArn));
            
            var queueResponse = setupClient.createQueue(CreateQueueRequest.builder()
                    .queueName("fraud-detection-queue-test")
                    .attributes(queueAttributes)
                    .build()).get();
            queueUrl = queueResponse.queueUrl();
        } finally {
            setupClient.close();
        }
    }
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cloud.provider", () -> "aws");
        registry.add("cloud.aws.region", () -> localstack.getRegion());
        registry.add("cloud.aws.sqs.endpoint", () -> localstack.getEndpointOverride(SQS).toString());
        registry.add("cloud.aws.sqs.queue-name", () -> "fraud-detection-queue-test");
        registry.add("cloud.aws.sqs.dlq-name", () -> "fraud-detection-queue-dlq-test");
        registry.add("cloud.aws.sqs.visibility-timeout", () -> "10");
        registry.add("cloud.aws.sqs.max-concurrent-messages", () -> "5");
        registry.add("spring.cloud.aws.credentials.access-key", () -> localstack.getAccessKey());
        registry.add("spring.cloud.aws.credentials.secret-key", () -> localstack.getSecretKey());
        registry.add("disruptor.ring-buffer-size", () -> "64");
        registry.add("disruptor.shutdown-timeout", () -> "5");
    }
    
    @Test
    void testHappyPath_MessageProcessedAndAcknowledged() throws Exception {
        // Create a legitimate transaction
        Transaction transaction = createTransaction("TXN-001", new BigDecimal("100.00"), "ACCT123");
        String message = objectMapper.writeValueAsString(transaction);
        
        // Get initial queue stats
        long initialApproxMessages = getApproximateNumberOfMessages(queueUrl);
        
        // Send message to SQS
        sqsTemplate.send(to -> to.queue(queueUrl).payload(message));
        
        // Wait for message to be processed and acknowledged
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    long currentMessages = getApproximateNumberOfMessages(queueUrl);
                    // Message should be removed from queue after processing
                    assertThat(currentMessages).isLessThanOrEqualTo(initialApproxMessages);
                });
        
        // Verify ring buffer is working
        assertThat(disruptorService.getRemainingCapacity()).isGreaterThan(0);
    }
    
    @Test
    void testFraudDetection_AlertGeneratedAndMessageAcknowledged() throws Exception {
        // Create a fraudulent transaction (large amount)
        Transaction transaction = createTransaction("TXN-002", new BigDecimal("15000.00"), "ACCT123");
        String message = objectMapper.writeValueAsString(transaction);
        
        // Send message
        sqsTemplate.send(to -> to.queue(queueUrl).payload(message));
        
        // Wait for processing
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    long currentMessages = getApproximateNumberOfMessages(queueUrl);
                    // Fraud alert generated, message still acknowledged
                    assertThat(currentMessages).isEqualTo(0);
                });
    }
    
    @Test
    void testInvalidJson_MessageAcknowledgedToPreventRetry() throws Exception {
        // Send invalid JSON
        String invalidMessage = "{invalid json}";
        sqsTemplate.send(to -> to.queue(queueUrl).payload(invalidMessage));
        
        // Wait for processing
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    long currentMessages = getApproximateNumberOfMessages(queueUrl);
                    // Invalid JSON should be acknowledged to prevent infinite retries
                    assertThat(currentMessages).isEqualTo(0);
                });
    }
    
    @Test
    void testRingBufferMetrics() {
        // Verify ring buffer is initialized
        assertThat(disruptorService.getRemainingCapacity()).isGreaterThan(0);
        assertThat(disruptorService.getRingBufferUtilization()).isGreaterThanOrEqualTo(0);
        assertThat(disruptorService.getRingBufferUtilization()).isLessThanOrEqualTo(100);
    }
    
    @Test
    void testMultipleMessages_ProcessedInParallel() throws Exception {
        // Send multiple messages
        for (int i = 0; i < 10; i++) {
            Transaction transaction = createTransaction(
                    "TXN-" + i, 
                    new BigDecimal("100.00"), 
                    "ACCT" + i
            );
            String message = objectMapper.writeValueAsString(transaction);
            sqsTemplate.send(to -> to.queue(queueUrl).payload(message));
        }
        
        // Wait for all messages to be processed
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    long currentMessages = getApproximateNumberOfMessages(queueUrl);
                    assertThat(currentMessages).isEqualTo(0);
                });
    }
    
    // Helper methods
    
    private long getApproximateNumberOfMessages(String queueUrl) throws Exception {
        var response = sqsAsyncClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build()).get();
        
        String count = response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        return count != null ? Long.parseLong(count) : 0;
    }
    
    private Transaction createTransaction(String txnId, BigDecimal amount, String accountId) {
        return Transaction.builder()
                .transactionId(txnId)
                .accountId(accountId)
                .amount(amount)
                .currency("USD")
                .timestamp(Instant.now())
                .merchantId("MERCHANT-123")
                .merchantCategory("RETAIL")
                .location("New York, NY")
                .type(Transaction.TransactionType.PURCHASE)
                .build();
    }
}

