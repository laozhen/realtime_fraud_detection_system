package com.hsbc.fraud.producer.config;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

import java.net.URI;

/**
 * AWS SQS configuration for producer.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "cloud.provider", havingValue = "aws")
public class AwsSqsConfig {
    
    @Bean
    public SqsAsyncClient sqsAsyncClient(
            org.springframework.core.env.Environment env) {
        
        String region = env.getProperty("cloud.aws.region", "us-east-1");
        String endpoint = env.getProperty("cloud.aws.sqs.endpoint");
        String accessKey = env.getProperty("spring.cloud.aws.credentials.access-key");
        String secretKey = env.getProperty("spring.cloud.aws.credentials.secret-key");
        
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder()
                .region(Region.of(region));
        
        // Use custom endpoint for LocalStack
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
            log.info("Using SQS endpoint override: {}", endpoint);
        }
        
        // Configure static credentials for LocalStack or test environments
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
            log.info("Configured static AWS credentials for SQS client");
        }
        
        return builder.build();
    }
    
    @Bean
    public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient) {
        return SqsTemplate.newTemplate(sqsAsyncClient);
    }
}
