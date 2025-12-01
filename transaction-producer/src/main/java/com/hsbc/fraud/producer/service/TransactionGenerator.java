package com.hsbc.fraud.producer.service;

import com.hsbc.fraud.producer.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service that generates random financial transactions for testing.
 * Intentionally creates some fraudulent transactions for demonstration.
 */
@Slf4j
@Service
public class TransactionGenerator {
    
    private static final List<String> NORMAL_ACCOUNTS = List.of(
            "ACCT100", "ACCT200", "ACCT300", "ACCT400", "ACCT500"
    );
    
    private static final List<String> SUSPICIOUS_ACCOUNTS = List.of(
            "ACCT001", "ACCT666", "ACCT999"
    );
    
    private static final List<String> MERCHANTS = List.of(
            "MERCHANT_001", "MERCHANT_002", "MERCHANT_003", "MERCHANT_004"
    );
    
    private static final List<String> CATEGORIES = List.of(
            "RETAIL", "ONLINE", "RESTAURANT", "TRAVEL", "ENTERTAINMENT"
    );
    
    private static final List<String> LOCATIONS = List.of(
            "NEW_YORK", "LONDON", "HONG_KONG", "SINGAPORE", "TOKYO"
    );
    
    private final Random random = new Random();
    
    /**
     * Generates a random transaction.
     * 10% chance of being a suspicious transaction (large amount or suspicious account).
     */
    public Transaction generateTransaction() {
        boolean shouldBeFraudulent = random.nextDouble() < 0.1; // 10% fraud rate
        
        String accountId = shouldBeFraudulent && random.nextBoolean()
                ? getRandomElement(SUSPICIOUS_ACCOUNTS)
                : getRandomElement(NORMAL_ACCOUNTS);
        
        BigDecimal amount = shouldBeFraudulent && random.nextBoolean()
                ? generateLargeAmount() // Exceeds threshold
                : generateNormalAmount();
        
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId(accountId)
                .amount(amount)
                .currency("USD")
                .timestamp(Instant.now())
                .merchantId(getRandomElement(MERCHANTS))
                .merchantCategory(getRandomElement(CATEGORIES))
                .location(getRandomElement(LOCATIONS))
                .type(getRandomElement(Transaction.TransactionType.values()))
                .build();
    }
    
    /**
     * Generates a rapid-fire burst of transactions from the same account.
     * Useful for testing the RapidFireRule.
     */
    public List<Transaction> generateRapidFireBurst(int count) {
        String accountId = getRandomElement(NORMAL_ACCOUNTS);
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> generateTransactionForAccount(accountId))
                .toList();
    }
    
    private Transaction generateTransactionForAccount(String accountId) {
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .accountId(accountId)
                .amount(generateNormalAmount())
                .currency("USD")
                .timestamp(Instant.now())
                .merchantId(getRandomElement(MERCHANTS))
                .merchantCategory(getRandomElement(CATEGORIES))
                .location(getRandomElement(LOCATIONS))
                .type(getRandomElement(Transaction.TransactionType.values()))
                .build();
    }
    
    private BigDecimal generateNormalAmount() {
        double amount = ThreadLocalRandom.current().nextDouble(1.0, 5000.0);
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal generateLargeAmount() {
        double amount = ThreadLocalRandom.current().nextDouble(10001.0, 100000.0);
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }
    
    private <T> T getRandomElement(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }
    
    private <T> T getRandomElement(T[] array) {
        return array[random.nextInt(array.length)];
    }
}

