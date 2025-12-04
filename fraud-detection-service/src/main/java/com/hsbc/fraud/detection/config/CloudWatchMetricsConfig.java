package com.hsbc.fraud.detection.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.time.Duration;

/**
 * Configuration for publishing metrics directly to AWS CloudWatch.
 * 
 * This is the PRIMARY and ONLY metrics backend for the transaction-producer service.
 * All metrics collected by MetricsCollector are published directly to CloudWatch.
 * 
 * CloudWatch Namespace: FraudDetection/${ENVIRONMENT:test}
 * 
 * Metrics are published:
 * - Every 1 minute (step interval)
 * - In batches of 20 metrics
 * - Using AWS credentials from the environment (IRSA for EKS)
 * 
 * Prerequisites:
 * - AWS credentials configured (IAM role for EKS pods with IRSA)
 * - CloudWatch PutMetricData permissions
 * - AWS region configured
 * - Spring profile: aws (cloud.provider=aws)
 * 
 * Note: Prometheus metrics export is disabled. CloudWatch is the sole metrics backend.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "cloud.provider", havingValue = "aws")
public class CloudWatchMetricsConfig {

    @Value("${cloud.aws.region:us-east-1}")
    private String awsRegion;

    @Value("${management.metrics.export.cloudwatch.namespace:FraudDetection/test}")
    private String namespace;

    @Value("${management.metrics.export.cloudwatch.enabled:true}")
    private boolean enabled;

    @Value("${management.metrics.export.cloudwatch.batch-size:20}")
    private int batchSize;

    @Value("${management.metrics.export.cloudwatch.step:1m}")
    private String step;

    /**
     * Creates CloudWatch async client for metrics publishing.
     * Uses default credentials provider which supports:
     * - IAM roles for EKS pods (IRSA)
     * - EC2 instance profiles
     * - Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
     * - AWS credentials file (~/.aws/credentials)
     */
    @Bean
    public CloudWatchAsyncClient cloudWatchAsyncClient() {
        log.info("Initializing CloudWatch async client for metrics publishing. Region: {}, Namespace: {}", 
                awsRegion, namespace);
        
        return CloudWatchAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Creates CloudWatch meter registry that publishes metrics directly to CloudWatch.
     * This registry is automatically used by Micrometer and the MetricsCollector.
     * 
     * Only active when both:
     * - cloud.provider=aws (class level condition)
     * - management.metrics.export.cloudwatch.enabled=true (bean level condition)
     */
    @Bean
    @ConditionalOnProperty(name = "management.metrics.export.cloudwatch.enabled", havingValue = "true", matchIfMissing = true)
    public CloudWatchMeterRegistry cloudWatchMeterRegistry(CloudWatchAsyncClient cloudWatchAsyncClient) {
        CloudWatchConfig cloudWatchConfig = new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String namespace() {
                return namespace;
            }

            @Override
            public int batchSize() {
                return batchSize;
            }

            @Override
            public Duration step() {
                // Convert "1m" to "PT1M", "30s" to "PT30S", "1h" to "PT1H", etc.
                return parseDuration(step);
            }

            @Override
            public boolean enabled() {
                return enabled;
            }
        };

        CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(
                cloudWatchConfig,
                Clock.SYSTEM,
                cloudWatchAsyncClient
        );

        log.info("CloudWatch metrics registry initialized successfully. " +
                "Namespace: {}, BatchSize: {}, Step: {}, Enabled: {}", 
                namespace, batchSize, step, enabled);
        
        return registry;
    }

    /**
     * Parses duration strings like "1m", "30s", "1h" into Java Duration.
     * Supports: s (seconds), m (minutes), h (hours)
     */
    private Duration parseDuration(String durationStr) {
        String normalized = durationStr.trim().toLowerCase();
        
        if (normalized.endsWith("s")) {
            long seconds = Long.parseLong(normalized.substring(0, normalized.length() - 1));
            return Duration.ofSeconds(seconds);
        } else if (normalized.endsWith("m")) {
            long minutes = Long.parseLong(normalized.substring(0, normalized.length() - 1));
            return Duration.ofMinutes(minutes);
        } else if (normalized.endsWith("h")) {
            long hours = Long.parseLong(normalized.substring(0, normalized.length() - 1));
            return Duration.ofHours(hours);
        } else {
            // Fallback: assume it's already in ISO-8601 format or seconds
            try {
                return Duration.parse(normalized);
            } catch (Exception e) {
                log.warn("Could not parse duration '{}', defaulting to 1 minute", normalized);
                return Duration.ofMinutes(1);
            }
        }
    }
}

