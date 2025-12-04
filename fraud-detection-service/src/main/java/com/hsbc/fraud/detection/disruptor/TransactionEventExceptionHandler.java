package com.hsbc.fraud.detection.disruptor;

import com.lmax.disruptor.ExceptionHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception handler for the Disruptor ring buffer.
 * Handles exceptions that occur during event processing.
 * 
 * Important: This handler prevents the Disruptor from shutting down on exceptions.
 * Failed messages will not be acknowledged and will be retried by SQS.
 */
@Slf4j
public class TransactionEventExceptionHandler implements ExceptionHandler<TransactionEvent> {
    
    private final Counter exceptionCounter;
    
    public TransactionEventExceptionHandler(MeterRegistry meterRegistry) {
        this.exceptionCounter = Counter.builder("disruptor.exceptions")
                .description("Number of exceptions in Disruptor event processing")
                .register(meterRegistry);
    }
    
    @Override
    public void handleEventException(Throwable ex, long sequence, TransactionEvent event) {
        log.error("Exception processing event at sequence {}: transactionId={}, error={}",
                sequence,
                event.getTransaction() != null ? event.getTransaction().getTransactionId() : "unknown",
                ex.getMessage(),
                ex);
        
        exceptionCounter.increment();
        
        // Note: Message will not be acknowledged, so SQS will retry
        // After max retries, message will move to DLQ automatically
    }
    
    @Override
    public void handleOnStartException(Throwable ex) {
        log.error("Exception during Disruptor startup", ex);
    }
    
    @Override
    public void handleOnShutdownException(Throwable ex) {
        log.error("Exception during Disruptor shutdown", ex);
    }
}


