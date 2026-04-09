package com.intraday.screener.risk;

import java.math.BigDecimal;

public record RiskPolicy(
        BigDecimal dailyLossLimitPercent,
        BigDecimal atrRiskMultiplier,
        BigDecimal maxSectorExposurePercent,
        BigDecimal trailingStopPercent
) {
    public static RiskPolicy defaults() {
        return new RiskPolicy(
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(3)
        );
    }
}
