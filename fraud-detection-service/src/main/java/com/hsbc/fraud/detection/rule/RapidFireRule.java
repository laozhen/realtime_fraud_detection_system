package com.hsbc.fraud.detection.rule;

import com.hsbc.fraud.detection.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fraud rule that detects rapid-fire transactions from the same account.
 * Demonstrates more complex stateful fraud detection logic.
 */
@Slf4j
@Component
public class RapidFireRule implements FraudRule {
    
    private final int maxTransactionsPerMinute;
    private final Map<String, TransactionCounter> accountTransactions = new ConcurrentHashMap<>();
    
    public RapidFireRule(
            @Value("${fraud.rules.rapid-fire.max-per-minute:5}") int maxTransactionsPerMinute) {
        this.maxTransactionsPerMinute = maxTransactionsPerMinute;
        log.info("RapidFireRule initialized with max {} transactions per minute", maxTransactionsPerMinute);
    }
    
    @Override
    public boolean isFraudulent(Transaction transaction) {
        String accountId = transaction.getAccountId();
        Instant now = transaction.getTimestamp() != null ? transaction.getTimestamp() : Instant.now();
        
        TransactionCounter counter = accountTransactions.computeIfAbsent(
                accountId, k -> new TransactionCounter());
        
        // Clean old transactions outside the time window
        counter.cleanOldTransactions(now);
        
        // Increment and check
        int count = counter.increment(now);
        
        return count > maxTransactionsPerMinute;
    }
    
    @Override
    public String getRuleName() {
        return "RAPID_FIRE_RULE";
    }
    
    @Override
    public String getReason(Transaction transaction) {
        return String.format("Account %s exceeded %d transactions per minute limit",
                transaction.getAccountId(), maxTransactionsPerMinute);
    }
    
    private static class TransactionCounter {
        private final Map<Instant, AtomicInteger> timestamps = new ConcurrentHashMap<>();
        
        int increment(Instant timestamp) {
            Instant minute = timestamp.truncatedTo(ChronoUnit.MINUTES);
            timestamps.computeIfAbsent(minute, k -> new AtomicInteger(0)).incrementAndGet();
            return timestamps.values().stream().mapToInt(AtomicInteger::get).sum();
        }
        
        void cleanOldTransactions(Instant now) {
            Instant oneMinuteAgo = now.minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
            timestamps.entrySet().removeIf(entry -> entry.getKey().isBefore(oneMinuteAgo));
        }
    }
}

