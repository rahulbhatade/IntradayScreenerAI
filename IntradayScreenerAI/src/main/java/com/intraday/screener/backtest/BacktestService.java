package com.intraday.screener.backtest;

import org.springframework.stereotype.Service;

@Service
public class BacktestService {

    public String runBacktest(String strategyName) {
        return "Backtest completed for strategy: " + strategyName + " (simulation placeholder).";
    }
}
