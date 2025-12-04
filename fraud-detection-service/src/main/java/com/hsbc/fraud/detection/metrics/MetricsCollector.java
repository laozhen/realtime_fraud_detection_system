package com.hsbc.fraud.detection.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection utility using Micrometer for Prometheus metrics.
 * 
 * Collects fraud detection service metrics:
 * - Counter metrics: fraud_detected_total, transactions_received_total, 
 *   transactions_processed_total, transactions_cleared_total, 
 *   processing_errors_total, rule_violations_total
 * - Timer/Histogram metrics: transaction_processing_duration_seconds
 * - Gauge metrics: ring_buffer_utilization_percent
 * 
 * These metrics are exposed via /actuator/prometheus endpoint and can be:
 * 1. Scraped by Prometheus
 * 2. Pushed to CloudWatch via CloudWatch agent or Micrometer CloudWatch registry
 * 
 * Usage:
 * <pre>
 *   metricsCollector.recordFraudDetected("HIGH", accountId, amount);
 *   metricsCollector.recordTransactionProcessed(processingTimeMs);
 *   metricsCollector.recordRuleViolation("LARGE_AMOUNT");
 * </pre>
 */
@Slf4j
@Component
@Scope("singleton")
public class MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    // Metric name constants
    private static final String FRAUD_DETECTED = "fraud_detected_total";
    private static final String TRANSACTIONS_RECEIVED = "transactions_received_total";
    private static final String TRANSACTIONS_PROCESSED = "transactions_processed_total";
    private static final String TRANSACTIONS_CLEARED = "transactions_cleared_total";
    private static final String PROCESSING_ERRORS = "processing_errors_total";
    private static final String PROCESSING_DURATION = "transaction_processing_duration_ms";
    private static final String PROCESSING_TOTAL_LATENCY = "transaction_processing_total_latency_ms";

    private static final String RULE_VIOLATIONS = "rule_violations_total";
    private static final String RING_BUFFER_UTILIZATION = "ring_buffer_utilization_percent";
    
    // Severity constants
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_LOW = "LOW";
    private static final String TAG_VALUE_UNKNOWN = "UNKNOWN";
    
    // Error/operation constants
    public static final String ERROR_TYPE_TRANSACTION_PROCESSING = "TRANSACTION_PROCESSING";
    public static final String OPERATION_TRANSACTION_PROCESSING = "transaction-processing";
    
    private static final List<String> DEFAULT_PROCESSING_ERROR_TYPES = List.of(ERROR_TYPE_TRANSACTION_PROCESSING);
    private static final List<String> DEFAULT_HIGH_LATENCY_OPERATIONS = List.of(OPERATION_TRANSACTION_PROCESSING);
    
    // Rule type constants
    public static final String RULE_LARGE_AMOUNT = "LARGE_AMOUNT";
    public static final String RULE_SUSPICIOUS_ACCOUNT = "SUSPICIOUS_ACCOUNT";
    public static final String RULE_RAPID_FIRE = "RAPID_FIRE";
    
    // Gauge value holder for ring buffer utilization
    private final AtomicLong ringBufferUtilization = new AtomicLong(0);
    private final Counter transactionsReceivedCounter;
    private final Counter transactionsProcessedCounter;

    private final Counter transactionsClearedCounter;
    private final Counter ringBufferHighUtilizationCounter;
    private final Timer transactionProcessingTimer;
    private final Timer transactionTotalLatency;
    private final Map<String, Counter> fraudCountersBySeverity = new ConcurrentHashMap<>();
    private final Counter defaultFraudCounter;
    private final Map<String, Counter> ruleViolationCountersByType;
    private final Counter defaultRuleViolationCounter;
    private final Map<String, Counter> processingErrorCountersByType = new ConcurrentHashMap<>();
    private final Counter defaultProcessingErrorCounter;
    private final Map<String, Counter> highLatencyCountersByOperation = new ConcurrentHashMap<>();
    private final Counter defaultHighLatencyCounter;
    
    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Register gauge for ring buffer utilization
        Gauge.builder(RING_BUFFER_UTILIZATION, ringBufferUtilization, AtomicLong::get)
                .description("Ring buffer utilization percentage")
                .register(meterRegistry);

        transactionsReceivedCounter = Counter.builder(TRANSACTIONS_RECEIVED)
                .description("Total number of transactions received")
                .register(meterRegistry);

        transactionsProcessedCounter = Counter.builder(TRANSACTIONS_PROCESSED)
                .description("Total number of transactions processed")
                .register(meterRegistry);

        transactionsClearedCounter = Counter.builder(TRANSACTIONS_CLEARED)
                .description("Total number of transactions cleared (no fraud)")
                .register(meterRegistry);

        ringBufferHighUtilizationCounter = Counter.builder("ring_buffer_high_utilization_total")
                .description("Count of ring buffer high utilization events")
                .register(meterRegistry);

        transactionProcessingTimer = Timer.builder(PROCESSING_DURATION)
                .description("Time taken to process a transaction")
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(1),
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(500),
                        Duration.ofMillis(1000),
                        Duration.ofMillis(2000)
                )
                .register(meterRegistry);

        transactionTotalLatency = Timer.builder(PROCESSING_TOTAL_LATENCY)
                .description("Time taken to process a transaction")
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(1),
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(500),
                        Duration.ofMillis(1000),
                        Duration.ofMillis(2000)
                )
                .register(meterRegistry);


        defaultFraudCounter = Counter.builder(FRAUD_DETECTED)
                .description("Total number of frauds detected")
                .tag("severity", TAG_VALUE_UNKNOWN)
                .register(meterRegistry);

        initializeSeverityCounters();
        defaultRuleViolationCounter = Counter.builder(RULE_VIOLATIONS)
                .description("Total number of rule violations")
                .register(meterRegistry);
        ruleViolationCountersByType = Map.of(
                RULE_LARGE_AMOUNT, createRuleViolationCounter(RULE_LARGE_AMOUNT),
                RULE_SUSPICIOUS_ACCOUNT, createRuleViolationCounter(RULE_SUSPICIOUS_ACCOUNT),
                RULE_RAPID_FIRE, createRuleViolationCounter(RULE_RAPID_FIRE)
        );

        defaultProcessingErrorCounter = Counter.builder(PROCESSING_ERRORS)
                .description("Total number of processing errors")
                .tag("error_type", TAG_VALUE_UNKNOWN)
                .register(meterRegistry);
        initializeProcessingErrorCounters();

        defaultHighLatencyCounter = Counter.builder("high_latency_events_total")
                .description("Count of high latency events (>500ms)")
                .tag("operation", TAG_VALUE_UNKNOWN)
                .register(meterRegistry);
        initializeHighLatencyCounters();
    }
    
    // ========================================================================
    // Fraud Detection Metrics
    // ========================================================================
    
    /**
     * Records a fraud detection event.
     * Creates counter: fraud_detected_total{severity="HIGH|MEDIUM|LOW"}
     * 
     * @param severity Fraud severity level
     * @param accountId Account ID
     * @param amount Transaction amount
     */
    public void recordFraudDetected(String severity, String accountId, double amount) {
        Counter fraudCounter = getSeverityCounter(severity);
        fraudCounter.increment();
        
        log.warn("Fraud detected: severity={} accountId={} amount={}", severity, accountId, amount);
    }
    
    /**
     * Records a fraud detection event without account dimension.
     * 
     * @param severity Fraud severity level
     */
    public void recordFraudDetected(String severity) {
        Counter fraudCounter = getSeverityCounter(severity);
        fraudCounter.increment();
    }
    
    // ========================================================================
    // Transaction Flow Metrics
    // ========================================================================
    
    /**
     * Records that a transaction was received for processing.
     * Creates counter: transactions_received_total
     */
    public void recordTransactionReceived() {
        transactionsReceivedCounter.increment();
    }
    

    /**
     * Records that a transaction was processed (fraud check complete).
     * Creates counter: transactions_processed_total
     * Creates histogram: transaction_processing_duration_seconds
     * 
     * @param processingTimeMs Processing time in milliseconds
     */
    public void recordTransactionProcessed(long processingTimeMs) {
        transactionsProcessedCounter.increment();
        transactionProcessingTimer.record(processingTimeMs, TimeUnit.MILLISECONDS);
    }

    public void recordTotalLatency(long processingTimeMs) {
        transactionsProcessedCounter.increment();
        transactionTotalLatency.record(processingTimeMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Records that a transaction was cleared (no fraud detected).
     * Creates counter: transactions_cleared_total
     */
    public void recordTransactionCleared() {
        transactionsClearedCounter.increment();
    }
    
    /**
     * Records that a transaction was cleared with account context.
     * 
     * @param accountId Account ID for dimensioning
     */
    public void recordTransactionCleared(String accountId) {
        recordTransactionCleared();
    }
    
    // ========================================================================
    // Error Metrics
    // ========================================================================
    
    /**
     * Records a processing error.
     * Creates counter: processing_errors_total{error_type="..."}
     * 
     * @param errorType The type of error
     */
    public void recordProcessingError(String errorType) {
        Counter counter = processingErrorCountersByType.getOrDefault(errorType, defaultProcessingErrorCounter);
        counter.increment();
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
    // Rule Violation Metrics
    // ========================================================================
    
    /**
     * Records a rule violation.
     * Creates counter: rule_violations_total{rule_type="..."}
     * 
     * @param ruleType The type of rule violated
     */
    public void recordRuleViolation(String ruleType) {
        Counter counter = ruleViolationCountersByType.getOrDefault(ruleType, defaultRuleViolationCounter);
        counter.increment();
    }
    
    /**
     * Records a rule violation with account context.
     * 
     * @param ruleType The type of rule violated
     * @param accountId Account ID
     */
    public void recordRuleViolation(String ruleType, String accountId) {
        recordRuleViolation(ruleType);
    }
    
    // ========================================================================
    // Performance Metrics
    // ========================================================================
    
    /**
     * Records high latency event.
     * 
     * @param operation The operation experiencing high latency
     * @param durationMs The duration in milliseconds
     */
    public void recordHighLatency(String operation, long durationMs) {
        Counter counter = highLatencyCountersByOperation.getOrDefault(operation, defaultHighLatencyCounter);
        counter.increment();
        
        log.warn("High latency detected: operation={} durationMs={}", operation, durationMs);
    }
    
    /**
     * Records high latency with transaction context.
     * 
     * @param operation The operation experiencing high latency
     * @param durationMs The duration in milliseconds
     * @param transactionId Transaction ID for context
     */
    public void recordHighLatency(String operation, long durationMs, String transactionId) {
        recordHighLatency(operation, durationMs);
        log.warn("High latency for transaction: operation={} durationMs={} transactionId={}", 
                operation, durationMs, transactionId);
    }
    
    // ========================================================================
    // Ring Buffer Metrics
    // ========================================================================
    
    /**
     * Updates ring buffer utilization gauge.
     * Creates gauge: ring_buffer_utilization_percent
     * 
     * @param utilizationPercent Utilization percentage (0-100)
     */
    public void updateRingBufferUtilization(double utilizationPercent) {
        ringBufferUtilization.set((long) utilizationPercent);
        
        // Log warning if utilization is high
        if (utilizationPercent > 80) {
            log.warn("Ring buffer high utilization: {}%", utilizationPercent);
        }
    }
    
    /**
     * Records ring buffer high utilization event.
     * 
     * @param utilizationPercent Utilization percentage
     * @param remainingCapacity Remaining capacity in the buffer
     */
    public void recordRingBufferHighUtilization(double utilizationPercent, long remainingCapacity) {
        updateRingBufferUtilization(utilizationPercent);
        
        ringBufferHighUtilizationCounter.increment();
        
        log.warn("Ring buffer high utilization: utilization={}% remainingCapacity={}", 
                utilizationPercent, remainingCapacity);
    }

    private void initializeSeverityCounters() {
        fraudCountersBySeverity.put(SEVERITY_HIGH, createSeverityCounter(SEVERITY_HIGH));
        fraudCountersBySeverity.put(SEVERITY_MEDIUM, createSeverityCounter(SEVERITY_MEDIUM));
        fraudCountersBySeverity.put(SEVERITY_LOW, createSeverityCounter(SEVERITY_LOW));
    }

    private Counter getSeverityCounter(String severity) {
        return fraudCountersBySeverity.getOrDefault(severity, defaultFraudCounter);
    }

    private Counter createSeverityCounter(String severity) {
        return Counter.builder(FRAUD_DETECTED)
                .description("Total number of frauds detected")
                .tag("severity", severity)
                .register(meterRegistry);
    }

    private Counter createRuleViolationCounter(String ruleType) {
        return Counter.builder(RULE_VIOLATIONS)
                .description("Total number of rule violations")
                .tag("rule_type", ruleType)
                .register(meterRegistry);
    }
    
    private void initializeProcessingErrorCounters() {
        DEFAULT_PROCESSING_ERROR_TYPES.forEach(type ->
                processingErrorCountersByType.put(type, createProcessingErrorCounter(type)));
    }

    private Counter createProcessingErrorCounter(String errorType) {
        return Counter.builder(PROCESSING_ERRORS)
                .description("Total number of processing errors")
                .tag("error_type", errorType)
                .register(meterRegistry);
    }

    private void initializeHighLatencyCounters() {
        DEFAULT_HIGH_LATENCY_OPERATIONS.forEach(operation ->
                highLatencyCountersByOperation.put(operation, createHighLatencyCounter(operation)));
    }

    private Counter createHighLatencyCounter(String operation) {
        return Counter.builder("high_latency_events_total")
                .description("Count of high latency events (>50ms)")
                .tag("operation", operation)
                .register(meterRegistry);
    }
    
    // ========================================================================
    // Helper Methods
    // ========================================================================
    
    /**
     * Determines the severity level based on the number of rule violations.
     * 
     * @param ruleViolations Number of rule violations
     * @return Severity level (HIGH, MEDIUM, LOW)
     */
    public static String determineSeverity(int ruleViolations) {
        if (ruleViolations >= 3) {
            return SEVERITY_HIGH;
        } else if (ruleViolations == 2) {
            return SEVERITY_MEDIUM;
        } else {
            return SEVERITY_LOW;
        }
    }
}

