package com.hsbc.fraud.detection.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for exporting Micrometer metrics to CloudWatch.
 * 
 * This enables viewing metrics like:
 * - disruptor.transaction.processed.success
 * - disruptor.transaction.processed.failure
 * - disruptor.transaction.processing.time
 * - disruptor.ring_buffer.utilization
 * 
 * Metrics will appear in CloudWatch under the configured namespace.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "cloud.provider", havingValue = "aws")
public class CloudWatchMetricsConfig {

    @Value("${cloud.aws.region:us-east-1}")
    private String awsRegion;

    @Value("${management.cloudwatch.metrics.export.namespace:FraudDetection/Micrometer}")
    private String namespace;

    @Value("${management.cloudwatch.metrics.export.step:PT1M}")
    private String step;

    @Bean
    public CloudWatchAsyncClient cloudWatchAsyncClient() {
        return CloudWatchAsyncClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public CloudWatchMeterRegistry cloudWatchMeterRegistry(CloudWatchAsyncClient cloudWatchAsyncClient) {
        CloudWatchConfig cloudWatchConfig = new CloudWatchConfig() {
            private final Map<String, String> configuration = Map.of(
                    "cloudwatch.namespace", namespace,
                    "cloudwatch.step", step,
                    "cloudwatch.batchSize", "20"
            );

            @Override
            public String get(String key) {
                return configuration.get(key);
            }

            @Override
            public String namespace() {
                return namespace;
            }

            @Override
            public Duration step() {
                return Duration.parse(step);
            }
        };

        CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(
                cloudWatchConfig,
                Clock.SYSTEM,
                cloudWatchAsyncClient
        );

        log.info("CloudWatch Micrometer Registry initialized with namespace: {}", namespace);
        return registry;
    }
}

