package com.hsbc.fraud.producer.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Metrics collection utility using Micrometer for metrics publishing.
 * 
 * Collects:
 * - Counter metrics: transactions_sent_total, processing_errors_total
 * - Timer/Histogram metrics: transaction_publish_duration_seconds
 * - Histogram metrics: sqs_publish_latency_seconds
 * 
 * Metrics Backend:
 * - CloudWatch ONLY - Direct publishing to namespace: FraudDetection/${ENVIRONMENT:test}
 *   - Published every 1 minute (step: 1m)
 *   - Batch size: 20 metrics
 *   - Requires AWS credentials and CloudWatch PutMetricData permissions
 * 
 * CloudWatch metrics can be viewed at:
 * AWS Console > CloudWatch > Metrics > FraudDetection/test (or your ENVIRONMENT value)
 * 
 * Usage:
 * <pre>
 *   metricsCollector.recordTransactionSent(accountId);
 *   metricsCollector.recordPublishDuration(durationMs);
 *   metricsCollector.recordProcessingError("SQS_ERROR");
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    // Metric name constants
    private static final String TRANSACTIONS_SENT = "transactions_sent_total";
    private static final String PROCESSING_ERRORS = "processing_errors_total";
    private static final String PUBLISH_DURATION = "transaction_publish_duration_seconds";
    private static final String SQS_LATENCY = "sqs_publish_latency_seconds";
    
    // ========================================================================
    // Transaction Counters
    // ========================================================================
    
    /**
     * Records that a transaction was successfully sent to the queue.
     * Creates counter: transactions_sent_total{account_id="..."}
     * 
     * @param accountId The account ID for dimensioning
     */
    public void recordTransactionSent(String accountId) {
        Counter.builder(TRANSACTIONS_SENT)
                .description("Total number of transactions sent to the queue")
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Records that a transaction was successfully sent (no dimensions).
     * Creates counter: transactions_sent_total
     */
    public void recordTransactionSent() {
        Counter.builder(TRANSACTIONS_SENT)
                .description("Total number of transactions sent to the queue")
                .register(meterRegistry)
                .increment();
    }
    
    // ========================================================================
    // Duration Timers / Histograms
    // ========================================================================
    
    /**
     * Records the duration of publishing a transaction (end-to-end).
     * Creates histogram: transaction_publish_duration_seconds
     * 
     * This creates a histogram with buckets for latency analysis.
     * 
     * @param durationMs Duration in milliseconds
     */
    public void recordPublishDuration(long durationMs) {
        Timer.builder(PUBLISH_DURATION)
                .description("Time taken to publish a transaction (end-to-end)")
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(10),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(200),
                        Duration.ofMillis(500),
                        Duration.ofMillis(1000),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(5000)
                )
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Records SQS publish latency specifically.
     * Creates histogram: sqs_publish_latency_seconds
     * 
     * @param durationMs Duration in milliseconds
     * @param operation The operation name (e.g., "sqs-publish")
     */
    public void recordSqsLatency(String operation, long durationMs) {
        Timer.builder(SQS_LATENCY)
                .description("SQS publish latency")
                .tag("operation", operation)
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(10),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(200),
                        Duration.ofMillis(500),
                        Duration.ofMillis(1000),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(5000)
                )
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    // ========================================================================
    // Error Counters
    // ========================================================================
    
    /**
     * Records a processing error.
     * Creates counter: processing_errors_total{error_type="..."}
     * 
     * @param errorType The type of error (e.g., "SQS_ERROR", "PUBLISH_ERROR")
     */
    public void recordProcessingError(String errorType) {
        Counter.builder(PROCESSING_ERRORS)
                .description("Total number of processing errors")
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Records a processing error with transaction context.
     * 
     * @param errorType The type of error
     * @param transactionId The transaction ID for logging context
     */
    public void recordProcessingError(String errorType, String transactionId) {
        recordProcessingError(errorType);
        log.error("Processing error: errorType={} transactionId={}", errorType, transactionId);
    }
    
    /**
     * Records a processing error with exception.
     * 
     * @param errorType The type of error
     * @param transactionId The transaction ID for logging context
     * @param error The exception
     */
    public void recordProcessingError(String errorType, String transactionId, Throwable error) {
        recordProcessingError(errorType);
        log.error("Processing error: errorType={} transactionId={} error={}", 
                errorType, transactionId, error.getMessage(), error);
    }
    
    // ========================================================================
    // High Latency Detection (derived from histograms)
    // ========================================================================
    
    /**
     * Records high latency event. This will be captured automatically by
     * histogram buckets, but we can also increment a specific counter.
     * 
     * @param operation The operation experiencing high latency
     * @param durationMs The duration in milliseconds
     */
    public void recordHighLatency(String operation, long durationMs) {
        Counter.builder("high_latency_events_total")
                .description("Count of high latency events (>500ms)")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
        
        log.warn("High latency detected: operation={} durationMs={}", operation, durationMs);
    }
}

