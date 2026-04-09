package com.intraday.screener.backtest;

import org.springframework.stereotype.Service;

@Service
public class PaperTradingService {

    public String executePaperTrade(String symbol) {
        return "Paper trade executed for " + symbol + " in non-production mode.";
    }
}
