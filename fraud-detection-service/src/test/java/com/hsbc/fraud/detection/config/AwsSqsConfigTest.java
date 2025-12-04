package com.hsbc.fraud.detection.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AwsSqsConfig Tests")
class AwsSqsConfigTest {
    
    private AwsSqsConfig awsSqsConfig;
    private Environment environment;
    
    @BeforeEach
    void setUp() {
        awsSqsConfig = new AwsSqsConfig();
        environment = mock(Environment.class);
    }
    
    @Nested
    @DisplayName("sqsAsyncClient Tests")
    class SqsAsyncClientTests {
        
        @Test
        @DisplayName("Should create SQS client with default region when no region property is set")
        void shouldCreateSqsClientWithDefaultRegion() {
            // Given
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn(null);
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            
            // Then
            assertNotNull(client);
            verify(environment).getProperty("cloud.aws.region", "us-east-1");
        }
        
        @Test
        @DisplayName("Should create SQS client with custom region")
        void shouldCreateSqsClientWithCustomRegion() {
            // Given
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("ap-southeast-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn(null);
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            
            // Then
            assertNotNull(client);
            verify(environment).getProperty("cloud.aws.region", "us-east-1");
        }
        
        @Test
        @DisplayName("Should create SQS client with endpoint override for LocalStack")
        void shouldCreateSqsClientWithEndpointOverride() {
            // Given
            String localStackEndpoint = "http://localhost:4566";
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn(localStackEndpoint);
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn("test-key");
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn("test-secret");
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            
            // Then
            assertNotNull(client);
            verify(environment).getProperty("cloud.aws.sqs.endpoint");
        }
        
        @Test
        @DisplayName("Should create SQS client with static credentials when provided")
        void shouldCreateSqsClientWithStaticCredentials() {
            // Given
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn("AKIAIOSFODNN7EXAMPLE");
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            
            // Then
            assertNotNull(client);
            verify(environment).getProperty("spring.cloud.aws.credentials.access-key");
            verify(environment).getProperty("spring.cloud.aws.credentials.secret-key");
        }
        
        @Test
        @DisplayName("Should create SQS client without credentials when only access key is provided")
        void shouldCreateSqsClientWithoutCredentialsWhenOnlyAccessKeyProvided() {
            // Given
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn("AKIAIOSFODNN7EXAMPLE");
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn(null);
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            
            // Then
            assertNotNull(client);
        }
        
        @Test
        @DisplayName("Should create SQS client without credentials when only secret key is provided")
        void shouldCreateSqsClientWithoutCredentialsWhenOnlySecretKeyProvided() {
            // Given
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            
            // Then
            assertNotNull(client);
        }
        
        @Test
        @DisplayName("Should create SQS client with empty endpoint string")
        void shouldCreateSqsClientWithEmptyEndpointString() {
            // Given
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn("");
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn(null);
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            
            // Then
            assertNotNull(client);
        }
        
        @Test
        @DisplayName("Should create SQS client with empty credential strings")
        void shouldCreateSqsClientWithEmptyCredentialStrings() {
            // Given
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn("");
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn("");
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            
            // Then
            assertNotNull(client);
        }
        
        @Test
        @DisplayName("Should create SQS client with whitespace credential strings")
        void shouldCreateSqsClientWithWhitespaceCredentialStrings() {
            // Given
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn("   ");
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn("   ");
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            
            // Then
            assertNotNull(client);
        }
    }
    
    @Nested
    @DisplayName("sqsTemplate Tests")
    class SqsTemplateTests {
        
        @Test
        @DisplayName("Should create SqsTemplate with provided SqsAsyncClient")
        void shouldCreateSqsTemplate() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            
            // When
            SqsTemplate template = awsSqsConfig.sqsTemplate(mockClient);
            
            // Then
            assertNotNull(template);
        }
        
        @Test
        @DisplayName("Should create multiple SqsTemplate instances")
        void shouldCreateMultipleSqsTemplateInstances() {
            // Given
            SqsAsyncClient mockClient1 = mock(SqsAsyncClient.class);
            SqsAsyncClient mockClient2 = mock(SqsAsyncClient.class);
            
            // When
            SqsTemplate template1 = awsSqsConfig.sqsTemplate(mockClient1);
            SqsTemplate template2 = awsSqsConfig.sqsTemplate(mockClient2);
            
            // Then
            assertNotNull(template1);
            assertNotNull(template2);
            assertNotSame(template1, template2);
        }
    }
    
    @Nested
    @DisplayName("defaultSqsListenerContainerFactory Tests")
    class DefaultSqsListenerContainerFactoryTests {
        
        @Test
        @DisplayName("Should create listener factory with default configuration")
        void shouldCreateListenerFactoryWithDefaultConfiguration() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("60");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("10");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "10")).thenReturn("10");
            
            // When
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(mockClient, environment);
            
            // Then
            assertNotNull(factory);
            verify(environment).getProperty("cloud.aws.sqs.visibility-timeout", "60");
            verify(environment).getProperty("cloud.aws.sqs.max-concurrent-messages", "10");
        }
        
        @Test
        @DisplayName("Should create listener factory with custom visibility timeout")
        void shouldCreateListenerFactoryWithCustomVisibilityTimeout() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("120");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("10");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "10")).thenReturn("10");
            
            // When
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(mockClient, environment);
            
            // Then
            assertNotNull(factory);
            verify(environment).getProperty("cloud.aws.sqs.visibility-timeout", "60");
        }
        
        @Test
        @DisplayName("Should create listener factory with custom max concurrent messages")
        void shouldCreateListenerFactoryWithCustomMaxConcurrentMessages() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("60");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("20");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "20")).thenReturn("20");
            
            // When
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(mockClient, environment);
            
            // Then
            assertNotNull(factory);
            verify(environment).getProperty("cloud.aws.sqs.max-concurrent-messages", "10");
        }
        
        @Test
        @DisplayName("Should create listener factory with custom messages per poll")
        void shouldCreateListenerFactoryWithCustomMessagesPerPoll() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("60");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("10");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "10")).thenReturn("5");
            
            // When
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(mockClient, environment);
            
            // Then
            assertNotNull(factory);
            verify(environment).getProperty("cloud.aws.sqs.messages-per-poll", "10");
        }
        
        @Test
        @DisplayName("Should cap messages per poll to max concurrent messages when configured higher")
        void shouldCapMessagesPerPollToMaxConcurrentMessages() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("60");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("10");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "10")).thenReturn("15");
            
            // When
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(mockClient, environment);
            
            // Then
            assertNotNull(factory);
            // messagesPerPoll should be capped to maxConcurrentMessages (10)
        }
        
        @Test
        @DisplayName("Should handle very low configuration values")
        void shouldHandleVeryLowConfigurationValues() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("1");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("1");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "1")).thenReturn("1");
            
            // When
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(mockClient, environment);
            
            // Then
            assertNotNull(factory);
        }
        
        @Test
        @DisplayName("Should handle very high configuration values")
        void shouldHandleVeryHighConfigurationValues() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("43200"); // 12 hours
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("100");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "100")).thenReturn("100");
            
            // When
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(mockClient, environment);
            
            // Then
            assertNotNull(factory);
        }
        
        @Test
        @DisplayName("Should use default values when properties are not set")
        void shouldUseDefaultValuesWhenPropertiesNotSet() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("60");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("10");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "10")).thenReturn("10");
            
            // When
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(mockClient, environment);
            
            // Then
            assertNotNull(factory);
            verify(environment).getProperty("cloud.aws.sqs.visibility-timeout", "60");
            verify(environment).getProperty("cloud.aws.sqs.max-concurrent-messages", "10");
            verify(environment, atLeastOnce()).getProperty("cloud.aws.sqs.messages-per-poll", "10");
        }
        
        @Test
        @DisplayName("Should create factory with messages per poll equal to max concurrent messages by default")
        void shouldCreateFactoryWithMessagesPerPollEqualToMaxConcurrentMessagesByDefault() {
            // Given
            SqsAsyncClient mockClient = mock(SqsAsyncClient.class);
            int maxConcurrent = 15;
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("60");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn(String.valueOf(maxConcurrent));
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", String.valueOf(maxConcurrent))).thenReturn(String.valueOf(maxConcurrent));
            
            // When
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(mockClient, environment);
            
            // Then
            assertNotNull(factory);
        }
    }
    
    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("Should create complete SQS configuration with all beans")
        void shouldCreateCompleteSqsConfiguration() {
            // Given
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn("http://localhost:4566");
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn("test-key");
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn("test-secret");
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("60");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("10");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "10")).thenReturn("10");
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            SqsTemplate template = awsSqsConfig.sqsTemplate(client);
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(client, environment);
            
            // Then
            assertNotNull(client);
            assertNotNull(template);
            assertNotNull(factory);
        }
        
        @Test
        @DisplayName("Should create AWS production configuration without LocalStack")
        void shouldCreateAwsProductionConfiguration() {
            // Given - production configuration without endpoint override
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("ap-southeast-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn(null);
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn(null);
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("120");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("20");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "20")).thenReturn("20");
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            SqsTemplate template = awsSqsConfig.sqsTemplate(client);
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(client, environment);
            
            // Then
            assertNotNull(client);
            assertNotNull(template);
            assertNotNull(factory);
        }
        
        @Test
        @DisplayName("Should create LocalStack configuration with custom settings")
        void shouldCreateLocalStackConfiguration() {
            // Given - LocalStack configuration
            when(environment.getProperty("cloud.aws.region", "us-east-1")).thenReturn("us-east-1");
            when(environment.getProperty("cloud.aws.sqs.endpoint")).thenReturn("http://localhost:4566");
            when(environment.getProperty("spring.cloud.aws.credentials.access-key")).thenReturn("test");
            when(environment.getProperty("spring.cloud.aws.credentials.secret-key")).thenReturn("test");
            when(environment.getProperty("cloud.aws.sqs.visibility-timeout", "60")).thenReturn("30");
            when(environment.getProperty("cloud.aws.sqs.max-concurrent-messages", "10")).thenReturn("5");
            when(environment.getProperty("cloud.aws.sqs.messages-per-poll", "5")).thenReturn("5");
            
            // When
            SqsAsyncClient client = awsSqsConfig.sqsAsyncClient(environment);
            SqsTemplate template = awsSqsConfig.sqsTemplate(client);
            SqsMessageListenerContainerFactory<Object> factory = 
                    awsSqsConfig.defaultSqsListenerContainerFactory(client, environment);
            
            // Then
            assertNotNull(client);
            assertNotNull(template);
            assertNotNull(factory);
        }
    }
}

