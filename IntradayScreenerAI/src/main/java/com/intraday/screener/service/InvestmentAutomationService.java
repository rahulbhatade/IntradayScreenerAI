package com.intraday.screener.service;

import com.intraday.screener.alert.AlertService;
import com.intraday.screener.model.InvestmentRequest;
import com.intraday.screener.model.InvestmentResponse;
import com.intraday.screener.model.StockDecision;
import com.intraday.screener.model.StockInput;
import com.intraday.screener.risk.RiskEngine;
import com.intraday.screener.risk.RiskPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class InvestmentAutomationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MIN_CAPITAL = BigDecimal.TEN;

    private final RiskEngine riskEngine;
    private final AlertService alertService;
    private final TradeDecisionMemoryStore tradeDecisionMemoryStore;
    private final Counter apiFailureCounter;
    private final Counter positionExceededCounter;
    private final Counter unusualConditionCounter;

    public InvestmentAutomationService(
            RiskEngine riskEngine,
            AlertService alertService,
            TradeDecisionMemoryStore tradeDecisionMemoryStore,
            MeterRegistry meterRegistry
    ) {
        this.riskEngine = riskEngine;
        this.alertService = alertService;
        this.tradeDecisionMemoryStore = tradeDecisionMemoryStore;
        this.apiFailureCounter = meterRegistry.counter("intraday.api.failures");
        this.positionExceededCounter = meterRegistry.counter("intraday.position.exceeded");
        this.unusualConditionCounter = meterRegistry.counter("intraday.unusual.market");
    }

    public InvestmentResponse evaluate(InvestmentRequest request) {
        try {
            RiskPolicy policy = RiskPolicy.defaults();
            BigDecimal totalCapital = request.stocks().stream()
                    .map(StockInput::investment)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Boolean> correlationFlags = riskEngine.correlationFlags(request.stocks(), totalCapital, policy);

            List<StockDecision> decisions = new ArrayList<>();

            BigDecimal totalInvested = BigDecimal.ZERO;
            BigDecimal totalCurrentValue = BigDecimal.ZERO;
            BigDecimal totalProfit = BigDecimal.ZERO;
            BigDecimal totalWithdrawn = BigDecimal.ZERO;

            for (StockInput stock : request.stocks()) {
                //StockDecision decision = evaluateStock(stock, totalCapital, policy, correlationFlags.get(stock.symbol().toUpperCase()));
                boolean correlationRisk = Boolean.TRUE.equals(correlationFlags.get(stock.symbol().toUpperCase()));
                StockDecision decision = evaluateStock(stock, totalCapital, policy, correlationRisk);
                decisions.add(decision);
                totalInvested = totalInvested.add(decision.initialInvestment());
                totalCurrentValue = totalCurrentValue.add(decision.currentValue());
                totalProfit = totalProfit.add(decision.profit());
                totalWithdrawn = totalWithdrawn.add(decision.withdrawalAmount());

                tradeDecisionMemoryStore.save(
                        decision.symbol(),
                        decision.action(),
                        decision.withdrawalAmount()
                );
            }

            decisions.sort(Comparator.comparing(StockDecision::symbol));

            BigDecimal portfolioDrawdownPct = totalProfit.compareTo(BigDecimal.ZERO) < 0
                    ? totalProfit.abs().multiply(HUNDRED).divide(totalInvested, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            if (portfolioDrawdownPct.compareTo(BigDecimal.valueOf(5)) > 0) {
                alertService.notifyLargeLoss("Portfolio drawdown breached 5%: " + portfolioDrawdownPct + "%");
            }

            return new InvestmentResponse(
                    decisions,
                    scale(totalInvested),
                    scale(totalCurrentValue),
                    scale(totalProfit),
                    scale(totalWithdrawn),
                    buildSummary(totalProfit)
            );
        } catch (Exception ex) {
            apiFailureCounter.increment();
            alertService.notifyApiFailure(ex.getMessage());
            throw ex;
        }
    }

    private StockDecision evaluateStock(StockInput stock, BigDecimal totalCapital, RiskPolicy policy, boolean correlationRisk) {
        BigDecimal investedAmount = stock.investment();
        List<BigDecimal> candles = stock.candleCloses();

        BigDecimal first = candles.get(0);
        BigDecimal latest = candles.get(candles.size() - 1);

        BigDecimal atr = riskEngine.calculateAtr(candles);
        BigDecimal recommendedPosition = riskEngine.recommendedPositionSize(totalCapital, atr, policy);

        if (investedAmount.compareTo(recommendedPosition) > 0) {
            positionExceededCounter.increment();
            alertService.notifyPositionSizeExceeded("Symbol " + stock.symbol() + " exceeds ATR size. Allowed: " + recommendedPosition);
        }

        if (atr.compareTo(first.multiply(BigDecimal.valueOf(0.1))) > 0) {
            unusualConditionCounter.increment();
            alertService.notifyUnusualMarketCondition("High ATR volatility for symbol " + stock.symbol());
        }

        BigDecimal growthRatio = latest.divide(first, 6, RoundingMode.HALF_UP);
        BigDecimal profitAmount = investedAmount.multiply(growthRatio); // invested + profit
        BigDecimal trailingStop = riskEngine.trailingStop(profitAmount, policy);
        BigDecimal pureProfit = profitAmount.subtract(investedAmount);
        BigDecimal profitOverInvestedPct = percent(pureProfit, investedAmount);

        boolean uptrend = isUptrend(candles);
        BigDecimal withdrawal = BigDecimal.ZERO;
        String action;
        String reason;

        if (correlationRisk) {
            action = "HOLD";
            reason = "Sector correlation too high; avoid overexposure.";
        } else if (profitAmount.compareTo(investedAmount) <= 0) {
            action = "HOLD";
            reason = "ProfitAmount is not greater than invested amount yet.";
        } else if (profitOverInvestedPct.compareTo(BigDecimal.valueOf(20)) < 0) {
            action = "HOLD";
            reason = "ProfitAmount has not crossed 20% over invested amount.";
        } else {
            BigDecimal chosenPercent = determineWithdrawPercent(profitOverInvestedPct, uptrend);
            withdrawal = pureProfit.multiply(chosenPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);

            BigDecimal minCapitalFloor = BigDecimal.TEN;
            BigDecimal maxAllowed = profitAmount.subtract(minCapitalFloor).max(BigDecimal.ZERO);
            if (withdrawal.compareTo(maxAllowed) > 0) {
                withdrawal = maxAllowed;
            }

            if (profitAmount.compareTo(trailingStop) <= 0) {
                action = "WITHDRAW";
                reason = "Trailing stop-loss hit before close; exit profit aggressively.";
            } else {
                action = withdrawal.compareTo(BigDecimal.ZERO) > 0 ? "WITHDRAW" : "HOLD";
                reason = uptrend
                        ? "Uptrend candles: withdraw partial (20-80%) profit before close."
                        : "Downtrend candles: withdraw higher share of profit before close.";
            }
        }

        BigDecimal remaining = profitAmount.subtract(withdrawal);

        return new StockDecision(
                stock.symbol().toUpperCase(),
                stock.sector().toUpperCase(),
                scale(investedAmount),
                scale(atr),
                scale(recommendedPosition),
                scale(profitAmount),
                scale(pureProfit),
                scale(profitOverInvestedPct),
                scale(trailingStop),
                scale(withdrawal),
                scale(remaining),
                correlationRisk,
                action,
                reason
        );
    }

    private BigDecimal determineWithdrawPercent(BigDecimal profitPct, boolean uptrend) {
        if (!uptrend) {
            return BigDecimal.valueOf(80);
        }
        if (profitPct.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return BigDecimal.valueOf(70);
        }
        if (profitPct.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return BigDecimal.valueOf(50);
        }
        return BigDecimal.valueOf(20);
    }

    private boolean isUptrend(List<BigDecimal> candles) {
        int positiveMoves = 0;
        for (int i = 1; i < candles.size(); i++) {
            if (candles.get(i).compareTo(candles.get(i - 1)) >= 0) {
                positiveMoves++;
            }
        }
        return positiveMoves >= (candles.size() - 1) / 2;
    }

    private BigDecimal percent(BigDecimal value, BigDecimal base) {
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildSummary(BigDecimal totalProfit) {
        if (totalProfit.compareTo(BigDecimal.ZERO) <= 0) {
            return "No portfolio profit yet. Strategy recommends waiting and monitoring candles.";
        }
        return "Automation completed. ProfitAmount > invested and 20-80% profit withdrawal logic applied with candle trend checks.";
    }
}