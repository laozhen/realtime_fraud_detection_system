package com.hsbc.fraud.detection.rule;

import com.hsbc.fraud.detection.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fraud rule that flags transactions from accounts on a blacklist.
 * Uses Strategy Pattern for flexible rule composition.
 */
@Slf4j
@Component
public class SuspiciousAccountRule implements FraudRule {
    
    private final Set<String> blacklistedAccounts;
    
    public SuspiciousAccountRule(
            @Value("${fraud.rules.suspicious-accounts:ACCT001,ACCT666,ACCT999}") List<String> accounts) {
        this.blacklistedAccounts = new HashSet<>(accounts);
        log.info("SuspiciousAccountRule initialized with {} blacklisted accounts", blacklistedAccounts.size());
    }
    
    @Override
    public boolean isFraudulent(Transaction transaction) {
        if (transaction.getAccountId() == null) {
            return false;
        }
        return blacklistedAccounts.contains(transaction.getAccountId());
    }
    
    @Override
    public String getRuleName() {
        return "SUSPICIOUS_ACCOUNT_RULE";
    }
    
    @Override
    public String getReason(Transaction transaction) {
        return String.format("Account %s is on the suspicious accounts blacklist",
                transaction.getAccountId());
    }
    
    /**
     * Allows dynamic addition of accounts to blacklist.
     */
    public void addToBlacklist(String accountId) {
        blacklistedAccounts.add(accountId);
        log.warn("Added account to blacklist: {}", accountId);
    }
}

