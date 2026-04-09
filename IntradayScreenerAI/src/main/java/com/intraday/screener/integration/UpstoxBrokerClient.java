package com.intraday.screener.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class UpstoxBrokerClient implements BrokerApiClient {

    private static final Logger log = LoggerFactory.getLogger(UpstoxBrokerClient.class);

    @Override
    public String brokerName() {
        return "UPSTOX";
    }

    @Override
    public void placeIntradayOrder(String symbol, BigDecimal quantity) {
        log.info("[SIMULATED] Upstox order placed for {} quantity {}", symbol, quantity);
    }

    @Override
    public void exitPosition(String symbol) {
        log.info("[SIMULATED] Upstox exit position for {}", symbol);
    }
}
