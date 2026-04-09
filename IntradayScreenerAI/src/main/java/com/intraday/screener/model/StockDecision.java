package com.intraday.screener.model;

import java.math.BigDecimal;

public record StockDecision(
        String symbol,
        String sector,
        BigDecimal initialInvestment,
        BigDecimal atr,
        BigDecimal recommendedPositionSize,
        BigDecimal currentValue,
        BigDecimal profit,
        BigDecimal profitPercentage,
        BigDecimal trailingStopLoss,
        BigDecimal withdrawalAmount,
        BigDecimal remainingCapital,
        boolean correlationRisk,
        String action,
        String reason
) {
}
