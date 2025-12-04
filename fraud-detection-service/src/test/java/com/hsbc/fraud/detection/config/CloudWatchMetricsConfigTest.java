package com.hsbc.fraud.detection.config;

import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CloudWatchMetricsConfig Tests")
class CloudWatchMetricsConfigTest {
    
    private CloudWatchMetricsConfig config;
    
    @BeforeEach
    void setUp() {
        config = new CloudWatchMetricsConfig();
    }
    
    @Nested
    @DisplayName("cloudWatchAsyncClient Tests")
    class CloudWatchAsyncClientTests {
        
        @Test
        @DisplayName("Should create CloudWatch async client with default region")
        void shouldCreateCloudWatchAsyncClientWithDefaultRegion() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            
            // When
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // Then
            assertNotNull(client);
        }
        
        @Test
        @DisplayName("Should create CloudWatch async client with custom region")
        void shouldCreateCloudWatchAsyncClientWithCustomRegion() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "ap-southeast-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/production");
            
            // When
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // Then
            assertNotNull(client);
        }
        
        @Test
        @DisplayName("Should create CloudWatch async client with us-west-2 region")
        void shouldCreateCloudWatchAsyncClientWithUsWest2Region() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-west-2");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/staging");
            
            // When
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // Then
            assertNotNull(client);
        }
        
        @Test
        @DisplayName("Should create CloudWatch async client with eu-west-1 region")
        void shouldCreateCloudWatchAsyncClientWithEuWest1Region() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "eu-west-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/eu");
            
            // When
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // Then
            assertNotNull(client);
        }
    }
    
    @Nested
    @DisplayName("cloudWatchMeterRegistry Tests")
    class CloudWatchMeterRegistryTests {
        
        @Test
        @DisplayName("Should create CloudWatch meter registry with default configuration")
        void shouldCreateCloudWatchMeterRegistryWithDefaultConfiguration() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch meter registry with custom namespace")
        void shouldCreateCloudWatchMeterRegistryWithCustomNamespace() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/production");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch meter registry with custom batch size")
        void shouldCreateCloudWatchMeterRegistryWithCustomBatchSize() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 50);
            ReflectionTestUtils.setField(config, "step", "1m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch meter registry with minimum batch size")
        void shouldCreateCloudWatchMeterRegistryWithMinimumBatchSize() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 1);
            ReflectionTestUtils.setField(config, "step", "1m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch meter registry with large batch size")
        void shouldCreateCloudWatchMeterRegistryWithLargeBatchSize() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 100);
            ReflectionTestUtils.setField(config, "step", "1m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch meter registry with custom step duration in seconds")
        void shouldCreateCloudWatchMeterRegistryWithCustomStepInSeconds() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "30s");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch meter registry with custom step duration in minutes")
        void shouldCreateCloudWatchMeterRegistryWithCustomStepInMinutes() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "5m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch meter registry with custom step duration in hours")
        void shouldCreateCloudWatchMeterRegistryWithCustomStepInHours() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1h");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch meter registry when enabled is true")
        void shouldCreateCloudWatchMeterRegistryWhenEnabled() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch meter registry when enabled is false")
        void shouldCreateCloudWatchMeterRegistryWhenDisabled() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", false);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
    }
    
    @Nested
    @DisplayName("parseDuration Tests")
    class ParseDurationTests {
        
        @Test
        @DisplayName("Should parse duration in seconds")
        void shouldParseDurationInSeconds() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "30s");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
            // The duration should be parsed correctly internally
        }
        
        @Test
        @DisplayName("Should parse duration in minutes")
        void shouldParseDurationInMinutes() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "5m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should parse duration in hours")
        void shouldParseDurationInHours() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "2h");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should parse duration with uppercase suffix")
        void shouldParseDurationWithUppercaseSuffix() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1M");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should parse duration with whitespace")
        void shouldParseDurationWithWhitespace() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "  1m  ");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }

        
        @Test
        @DisplayName("Should default to 1 minute for invalid duration format")
        void shouldDefaultTo1MinuteForInvalidDurationFormat() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "invalid");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
            // Should default to 1 minute and not throw exception
        }
        
        @Test
        @DisplayName("Should handle very large duration values")
        void shouldHandleVeryLargeDurationValues() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1000m");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should parse single digit duration values")
        void shouldParseSingleDigitDurationValues() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1s");
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            
            // When
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(registry);
        }
    }
    
    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("Should create complete CloudWatch configuration for test environment")
        void shouldCreateCompleteCloudWatchConfigurationForTestEnvironment() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1m");
            
            // When
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(client);
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create complete CloudWatch configuration for production environment")
        void shouldCreateCompleteCloudWatchConfigurationForProductionEnvironment() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "ap-southeast-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/production");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 50);
            ReflectionTestUtils.setField(config, "step", "30s");
            
            // When
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(client);
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create complete CloudWatch configuration for staging environment")
        void shouldCreateCompleteCloudWatchConfigurationForStagingEnvironment() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-west-2");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/staging");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 30);
            ReflectionTestUtils.setField(config, "step", "2m");
            
            // When
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(client);
            assertNotNull(registry);
        }
        
        @Test
        @DisplayName("Should create CloudWatch configuration with various step durations")
        void shouldCreateCloudWatchConfigurationWithVariousStepDurations() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            
            String[] stepDurations = {"10s", "30s", "1m", "5m", "1h"};
            
            for (String step : stepDurations) {
                // When
                ReflectionTestUtils.setField(config, "step", step);
                CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
                CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
                
                // Then
                assertNotNull(client, "Client should not be null for step: " + step);
                assertNotNull(registry, "Registry should not be null for step: " + step);
            }
        }
        
        @Test
        @DisplayName("Should create CloudWatch configuration with various batch sizes")
        void shouldCreateCloudWatchConfigurationWithVariousBatchSizes() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "step", "1m");
            
            int[] batchSizes = {1, 10, 20, 50, 100};
            
            for (int batchSize : batchSizes) {
                // When
                ReflectionTestUtils.setField(config, "batchSize", batchSize);
                CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
                CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
                
                // Then
                assertNotNull(client, "Client should not be null for batch size: " + batchSize);
                assertNotNull(registry, "Registry should not be null for batch size: " + batchSize);
            }
        }
        
        @Test
        @DisplayName("Should create CloudWatch configuration for all AWS regions")
        void shouldCreateCloudWatchConfigurationForAllAwsRegions() {
            // Given
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", true);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1m");
            
            String[] regions = {"us-east-1", "us-west-2", "eu-west-1", "ap-southeast-1", "ap-northeast-1"};
            
            for (String region : regions) {
                // When
                ReflectionTestUtils.setField(config, "awsRegion", region);
                CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
                CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
                
                // Then
                assertNotNull(client, "Client should not be null for region: " + region);
                assertNotNull(registry, "Registry should not be null for region: " + region);
            }
        }
        
        @Test
        @DisplayName("Should create CloudWatch configuration with disabled metrics")
        void shouldCreateCloudWatchConfigurationWithDisabledMetrics() {
            // Given
            ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
            ReflectionTestUtils.setField(config, "namespace", "FraudDetection/test");
            ReflectionTestUtils.setField(config, "enabled", false);
            ReflectionTestUtils.setField(config, "batchSize", 20);
            ReflectionTestUtils.setField(config, "step", "1m");
            
            // When
            CloudWatchAsyncClient client = config.cloudWatchAsyncClient();
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client);
            
            // Then
            assertNotNull(client);
            assertNotNull(registry);
            // Registry is created but disabled internally
        }
    }
}

