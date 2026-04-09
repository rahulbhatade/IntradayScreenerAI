package com.intraday.screener.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class TradeDecisionMemoryStore {

    private final List<TradeDecisionSnapshot> decisions = new CopyOnWriteArrayList<>();

    public void save(String symbol, String action, BigDecimal withdrawalAmount) {
        decisions.add(new TradeDecisionSnapshot(symbol, action, withdrawalAmount, Instant.now()));
    }

    public List<TradeDecisionSnapshot> findAll() {
        return List.copyOf(decisions);
    }

    public record TradeDecisionSnapshot(
            String symbol,
            String action,
            BigDecimal withdrawalAmount,
            Instant createdAt
    ) {
    }
}