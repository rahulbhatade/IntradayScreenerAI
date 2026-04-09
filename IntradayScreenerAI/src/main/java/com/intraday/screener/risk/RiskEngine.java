package com.intraday.screener.risk;

import com.intraday.screener.model.StockInput;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RiskEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal calculateAtr(List<BigDecimal> closes) {
        if (closes.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal sumRange = BigDecimal.ZERO;
        for (int i = 1; i < closes.size(); i++) {
            sumRange = sumRange.add(closes.get(i).subtract(closes.get(i - 1)).abs());
        }
        return sumRange.divide(BigDecimal.valueOf(closes.size() - 1), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal recommendedPositionSize(BigDecimal portfolioCapital, BigDecimal atr, RiskPolicy policy) {
        BigDecimal riskPerTrade = portfolioCapital.multiply(policy.dailyLossLimitPercent())
                .divide(HUNDRED, 4, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);

        if (atr.compareTo(BigDecimal.ZERO) <= 0) {
            return riskPerTrade.max(BigDecimal.TEN);
        }

        BigDecimal size = riskPerTrade.divide(atr.multiply(policy.atrRiskMultiplier()), 2, RoundingMode.HALF_UP);
        return size.max(BigDecimal.TEN);
    }

    public BigDecimal trailingStop(BigDecimal currentValue, RiskPolicy policy) {
        BigDecimal drawdown = currentValue.multiply(policy.trailingStopPercent())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return currentValue.subtract(drawdown);
    }

    public Map<String, Boolean> correlationFlags(List<StockInput> stocks, BigDecimal totalCapital, RiskPolicy policy) {
        Map<String, BigDecimal> sectorExposure = new HashMap<>();
        for (StockInput stock : stocks) {
            sectorExposure.merge(stock.sector().toUpperCase(), stock.investment(), BigDecimal::add);
        }

        Map<String, Boolean> result = new HashMap<>();
        for (StockInput stock : stocks) {
            BigDecimal sectorValue = sectorExposure.get(stock.sector().toUpperCase());
            BigDecimal exposurePct = sectorValue.multiply(HUNDRED).divide(totalCapital, 2, RoundingMode.HALF_UP);
            result.put(stock.symbol().toUpperCase(), exposurePct.compareTo(policy.maxSectorExposurePercent()) > 0);
        }
        return result;
    }
}
