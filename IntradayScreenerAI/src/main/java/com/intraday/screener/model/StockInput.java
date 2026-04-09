package com.intraday.screener.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record StockInput(
        @NotBlank(message = "Stock symbol is required")
        String symbol,
        @NotBlank(message = "Sector is required for correlation checks")
        String sector,
        @DecimalMin(value = "10.0", message = "Base investment must be at least 10 rupees")
        BigDecimal investment,
        @NotEmpty(message = "At least one candle close value is required")
        List<BigDecimal> candleCloses
) {
}
