package com.hsbc.fraud.detection.config;

import com.hsbc.fraud.detection.disruptor.TransactionEvent;
import com.hsbc.fraud.detection.disruptor.TransactionEventExceptionHandler;
import com.hsbc.fraud.detection.disruptor.TransactionEventFactory;
import com.hsbc.fraud.detection.disruptor.TransactionEventHandler;
import com.hsbc.fraud.detection.service.AlertService;
import com.hsbc.fraud.detection.service.FraudDetectionEngine;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration for LMAX Disruptor ring buffer.
 * 
 * Configures the Disruptor for low-latency, high-throughput transaction processing.
 * Uses BlockingWaitStrategy for balanced CPU usage and latency.
 */
@Slf4j
@Configuration
public class DisruptorConfig {
    
    @Value("${disruptor.ring-buffer-size:8192}")
    private int ringBufferSize;
    
    @Value("${disruptor.thread-name-prefix:fraud-disruptor-}")
    private String threadNamePrefix;
    
    @Value("${disruptor.shutdown-timeout:30}")
    private int shutdownTimeoutSeconds;
    
    @Value("${disruptor.worker-pool-size:4}")
    private int workerPoolSize;
    
    /**
     * Create a thread pool for parallel event processing within EventHandler.
     * This executor will be used by the EventHandler to process events concurrently.
     * 
     * Uses a bounded queue with CallerRunsPolicy to apply backpressure when overwhelmed.
     */
    @Bean
    public ExecutorService eventProcessingExecutor() {
        int queueCapacity = ringBufferSize / 2; // Queue capacity based on ring buffer size
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workerPoolSize,              // core pool size
                workerPoolSize,              // max pool size (fixed)
                60L,                         // keep alive time
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),  // bounded queue
                new DisruptorThreadFactory(threadNamePrefix + "worker-"),
                new ThreadPoolExecutor.CallerRunsPolicy()  // backpressure: run in caller thread when full
        );
        
        log.info("Created event processing executor with {} workers and queue capacity {}",
                workerPoolSize, queueCapacity);
        
        return executor;
    }
    
    /**
     * Create the Disruptor instance.
     * Ring buffer size must be a power of 2.
     */
    @Bean
    public Disruptor<TransactionEvent> disruptor(
            FraudDetectionEngine fraudDetectionEngine,
            AlertService alertService,
            MeterRegistry meterRegistry,
            ExecutorService eventProcessingExecutor) {
        
        // Validate ring buffer size is power of 2
        if (!isPowerOfTwo(ringBufferSize)) {
            log.warn("Ring buffer size {} is not a power of 2, adjusting to nearest power of 2", ringBufferSize);
            ringBufferSize = nextPowerOfTwo(ringBufferSize);
        }
        
        log.info("Initializing Disruptor with ring buffer size: {} and {} processing threads", 
                ringBufferSize, workerPoolSize);
        
        // Create custom thread factory for Disruptor's main event thread
        ThreadFactory threadFactory = new DisruptorThreadFactory(threadNamePrefix);
        
        // Create the Disruptor
        Disruptor<TransactionEvent> disruptor = new Disruptor<>(
                new TransactionEventFactory(),
                ringBufferSize,
                threadFactory,
                ProducerType.MULTI,  // Multiple SQS listener threads may publish
                new BlockingWaitStrategy()  // Balanced CPU/latency trade-off
        );
        
        // Set up event handler with internal thread pool for parallel processing
        TransactionEventHandler eventHandler = new TransactionEventHandler(
                fraudDetectionEngine,
                alertService,
                meterRegistry,
                eventProcessingExecutor
        );
        
        disruptor.handleEventsWith(eventHandler);
        
        // Set exception handler
        disruptor.setDefaultExceptionHandler(
                new TransactionEventExceptionHandler(meterRegistry)
        );
        
        log.info("Disruptor configured successfully with {} processing threads", workerPoolSize);
        return disruptor;
    }
    
    /**
     * Expose shutdown timeout for DisruptorService.
     */
    @Bean
    public int disruptorShutdownTimeout() {
        return shutdownTimeoutSeconds;
    }
    
    /**
     * Custom thread factory for Disruptor worker threads.
     */
    private static class DisruptorThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        
        public DisruptorThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(false);  // Non-daemon to prevent premature shutdown
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
    
    /**
     * Check if a number is a power of 2.
     */
    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    
    /**
     * Get next power of 2 greater than or equal to n.
     */
    private int nextPowerOfTwo(int n) {
        if (n <= 0) {
            return 1;
        }
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }
}


