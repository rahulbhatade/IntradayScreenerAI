package com.intraday.screener.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ZerodhaBrokerClient implements BrokerApiClient {

    private static final Logger log = LoggerFactory.getLogger(ZerodhaBrokerClient.class);

    @Override
    public String brokerName() {
        return "ZERODHA";
    }

    @Override
    public void placeIntradayOrder(String symbol, BigDecimal quantity) {
        log.info("[SIMULATED] Zerodha order placed for {} quantity {}", symbol, quantity);
    }

    @Override
    public void exitPosition(String symbol) {
        log.info("[SIMULATED] Zerodha exit position for {}", symbol);
    }
}
