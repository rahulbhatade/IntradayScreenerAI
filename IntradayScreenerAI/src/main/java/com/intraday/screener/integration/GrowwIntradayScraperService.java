package com.intraday.screener.integration;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class GrowwIntradayScraperService {

    private static final String GROWW_INTRADAY_URL = "https://groww.in/stocks/intraday";

    private static final Pattern ROW_PATTERN = Pattern.compile(
            "\\{[^\\{\\}]{0,1400}?\"companyName\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]{0,1200}?\"tradingSymbol\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]{0,1200}?\"ltp\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)"
                    + "[^\\{\\}]{0,1200}?(?:\"dayChangePerc\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)|\"priceChangePercent\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?))"
                    + "[^\\{\\}]{0,1200}?(?:\"week52Performance\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)|\"fiftyTwoWeekPerformance\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?))"
                    + "[^\\{\\}]{0,1200}?(?:\"avgVolDiffWeek\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)|\"weeklyAvgVolumeDiff\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?))"
                    + "[^\\{\\}]{0,1200}?\\}",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<GrowwIntradayStockRow> fetchIntradayRows() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GROWW_INTRADAY_URL))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null) {
                return List.of();
            }
            return parseRows(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private List<GrowwIntradayStockRow> parseRows(String html) {
        Matcher matcher = ROW_PATTERN.matcher(html);
        Map<String, GrowwIntradayStockRow> rowsBySymbol = new LinkedHashMap<>();
        while (matcher.find()) {
            String company = safe(matcher.group(1));
            String symbol = safe(matcher.group(2));
            if (company.isBlank() || symbol.isBlank()) {
                continue;
            }
            rowsBySymbol.put(symbol, new GrowwIntradayStockRow(
                    company,
                    symbol,
                    parseDecimal(matcher.group(3)),
                    parseDecimal(firstNonNull(matcher.group(4), matcher.group(5))),
                    parseDecimal(firstNonNull(matcher.group(6), matcher.group(7))),
                    parseDecimal(firstNonNull(matcher.group(8), matcher.group(9)))
            ));
            if (rowsBySymbol.size() >= 200) {
                break;
            }
        }
        return new ArrayList<>(rowsBySymbol.values());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal parseDecimal(String value) {
        try {
            return value == null ? BigDecimal.ZERO : new BigDecimal(value.trim());
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    public record GrowwIntradayStockRow(
            String companyName,
            String symbol,
            BigDecimal marketPrice,
            BigDecimal dayChangePercentage,
            BigDecimal performance52Week,
            BigDecimal avgVolDiffWeek
    ) {}
}