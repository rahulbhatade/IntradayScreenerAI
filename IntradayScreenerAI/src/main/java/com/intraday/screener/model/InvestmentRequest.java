package com.intraday.screener.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InvestmentRequest(
        @NotEmpty(message = "Provide at least one stock")
        @Size(max = 3, message = "Daily invest supports maximum 3 intraday stocks")
        List<@Valid StockInput> stocks
) {
}
