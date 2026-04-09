package com.intraday.screener.model;

import java.math.BigDecimal;
import java.util.List;

public record InvestmentResponse(
        List<StockDecision> stockDecisions,
        BigDecimal totalInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalProfit,
        BigDecimal totalWithdrawn,
        String summary
) {
}
