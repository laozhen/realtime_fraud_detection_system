package com.hsbc.fraud.detection.metrics;

import com.hsbc.fraud.detection.disruptor.DisruptorService;
import com.hsbc.fraud.detection.logging.MetricsLogger;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Metrics collector for Disruptor ring buffer.
 * Exposes metrics to Prometheus for monitoring system health and performance.
 * Also emits structured logs for CloudWatch Log Metric Filters.
 * 
 * Key Metrics:
 * - Ring buffer utilization (gauge)
 * - Remaining capacity (gauge)
 * - Events published (counter - tracked in DisruptorService)
 * - Events processed (counter - tracked in TransactionEventHandler)
 * - Processing latency (histogram - tracked in TransactionEventHandler)
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "cloud.provider", havingValue = "aws")
public class DisruptorMetrics {
    
    private static final double HIGH_UTILIZATION_THRESHOLD = 80.0;
    
    private final DisruptorService disruptorService;
    private final MeterRegistry meterRegistry;
    
    public DisruptorMetrics(DisruptorService disruptorService, MeterRegistry meterRegistry) {
        this.disruptorService = disruptorService;
        this.meterRegistry = meterRegistry;
        
        // Register gauges for ring buffer metrics
        registerMetrics();
    }
    
    /**
     * Register Prometheus gauges for ring buffer monitoring.
     */
    private void registerMetrics() {
        Gauge.builder("disruptor.ring_buffer.utilization", 
                disruptorService, DisruptorService::getRingBufferUtilization)
                .description("Ring buffer utilization percentage (0-100)")
                .register(meterRegistry);
        
        Gauge.builder("disruptor.ring_buffer.remaining_capacity", 
                disruptorService, DisruptorService::getRemainingCapacity)
                .description("Number of available slots in ring buffer")
                .register(meterRegistry);
        
        log.info("Disruptor metrics registered with Prometheus");
    }
    
    /**
     * Periodically log ring buffer utilization for monitoring.
     * High utilization may indicate backpressure or need for scaling.
     * Emits CloudWatch metrics via structured logging when utilization is high.
     */
    @Scheduled(fixedRate = 60000)  // Every minute
    public void logRingBufferUtilization() {
        double utilization = disruptorService.getRingBufferUtilization();
        long remainingCapacity = disruptorService.getRemainingCapacity();
        
        if (utilization > HIGH_UTILIZATION_THRESHOLD) {
            // Emit metric for CloudWatch Log Metric Filter
            MetricsLogger.logRingBufferHighUtilization(utilization, remainingCapacity);
            log.warn("High ring buffer utilization: {}% (remaining: {})", 
                    String.format("%.2f", utilization), remainingCapacity);
        } else {
            log.debug("Ring buffer utilization: {}% (remaining: {})", 
                    String.format("%.2f", utilization), remainingCapacity);
        }
    }
}


