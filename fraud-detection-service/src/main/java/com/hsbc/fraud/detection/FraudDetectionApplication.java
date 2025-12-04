package com.hsbc.fraud.detection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for HSBC Fraud Detection Service.
 * 
 * Features:
 * - LMAX Disruptor for low-latency transaction processing
 * - Manual SQS acknowledgment for zero message loss
 * - Graceful shutdown with Disruptor drain
 * - Prometheus metrics for monitoring
 */
@Slf4j
@SpringBootApplication(exclude = {
    io.awspring.cloud.autoconfigure.metrics.CloudWatchExportAutoConfiguration.class
})
@EnableAsync
public class FraudDetectionApplication {
    
    public static void main(String[] args) {
        log.info("Starting HSBC Fraud Detection Service");
        
        ConfigurableApplicationContext context = SpringApplication.run(FraudDetectionApplication.class, args);
        
        // Register shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, initiating graceful shutdown");
            log.info("Disruptor will drain in-flight messages before terminating");
            // Spring's @PreDestroy in DisruptorService handles the actual shutdown
        }));
        
        log.info("Fraud Detection Service started successfully");
        log.info("Disruptor ring buffer is ready for low-latency transaction processing");
    }
}

