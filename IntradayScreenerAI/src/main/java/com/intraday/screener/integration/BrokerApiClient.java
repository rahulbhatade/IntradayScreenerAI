package com.intraday.screener.integration;

import java.math.BigDecimal;

public interface BrokerApiClient {
    String brokerName();
    void placeIntradayOrder(String symbol, BigDecimal quantity);
    void exitPosition(String symbol);
}
