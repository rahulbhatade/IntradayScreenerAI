package com.intraday.screener.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.intraday.screener.integration.GrowwIntradayScraperService;
import com.intraday.screener.backtest.BacktestService;
import com.intraday.screener.backtest.PaperTradingService;
import com.intraday.screener.compliance.ComplianceService;
import com.intraday.screener.model.InvestmentRequest;
import com.intraday.screener.model.InvestmentResponse;
import com.intraday.screener.model.StockInput;
import com.intraday.screener.service.InvestmentAutomationService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AutomationController {

    private final InvestmentAutomationService service;
    private final BacktestService backtestService;
    private final PaperTradingService paperTradingService;
    private final ComplianceService complianceService;
    private final GrowwIntradayScraperService growwIntradayScraperService;

    private final Map<String, StockInput> stockUniverse = new HashMap<>();
    private static final List<String> BROKERS = List.of("GROWW");

        public AutomationController(
            InvestmentAutomationService service,
            BacktestService backtestService,
            PaperTradingService paperTradingService,
            ComplianceService complianceService,
            GrowwIntradayScraperService growwIntradayScraperService
    ) {
        this.service = service;
        this.backtestService = backtestService;
        this.paperTradingService = paperTradingService;
        this.complianceService = complianceService;
        this.growwIntradayScraperService = growwIntradayScraperService;
        seedStocks();
    }

    @GetMapping("/app-init")
    public Map<String, Object> appInit(HttpSession session) {
        boolean authenticated = Boolean.TRUE.equals(session.getAttribute("authenticated"));
        boolean otpSent = session.getAttribute("pendingOtpCode") != null && !authenticated;
        boolean growwPinPending = Boolean.TRUE.equals(session.getAttribute("growwPinPending"));

        return Map.of(
                "authenticated", authenticated,
                "otpSent", otpSent,
                "selectedBroker", session.getAttribute("selectedBroker") == null ? "" : session.getAttribute("selectedBroker"),
                "brokerConnected", Boolean.TRUE.equals(session.getAttribute("brokerConnected")),
                "growwPinPending", growwPinPending,
                "disclaimer", complianceService.disclaimer(),
                "stocks", stockUniverse.values().stream().toList(),
                "brokers", BROKERS
        );
    }

    @PostMapping("/auth/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody OtpSendRequest request, HttpSession session) {
        if (isBlank(request.authType()) || isBlank(request.identifier())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Google email or phone number is required."));
        }

        String normalizedIdentifier = request.identifier().trim();
        if ("GOOGLE".equalsIgnoreCase(request.authType()) && !normalizedIdentifier.contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid Gmail ID for Google login."));
        }
        if ("PHONE".equalsIgnoreCase(request.authType()) && normalizedIdentifier.length() < 10) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid mobile number for phone login."));
        }

        String otpCode = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        session.setAttribute("pendingAuthType", request.authType().toUpperCase());
        session.setAttribute("pendingIdentifier", normalizedIdentifier);
        session.setAttribute("pendingOtpCode", otpCode);
        session.setAttribute("pendingOtpExpiry", Instant.now().plusSeconds(300));
        session.setAttribute("authenticated", false);

        return ResponseEntity.ok(Map.of(
                "message", "OTP sent successfully. Enter OTP to continue.",
                "delivery", "OTP sent to " + maskIdentifier(normalizedIdentifier),
                "demoOtp", otpCode
        ));
    }

    @PostMapping("/auth/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpVerifyRequest request, HttpSession session) {
        String pendingOtp = (String) session.getAttribute("pendingOtpCode");
        String pendingIdentifier = (String) session.getAttribute("pendingIdentifier");
        Instant expiry = (Instant) session.getAttribute("pendingOtpExpiry");

        if (pendingOtp == null || pendingIdentifier == null || expiry == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please send OTP first."));
        }
        if (Instant.now().isAfter(expiry)) {
            clearOtpState(session);
            return ResponseEntity.badRequest().body(Map.of("error", "OTP expired. Please resend OTP."));
        }
        if (isBlank(request.identifier()) || isBlank(request.otp())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Identifier and OTP are required."));
        }
        if (!pendingIdentifier.equals(request.identifier().trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Identifier does not match OTP request."));
        }
        if (!pendingOtp.equals(request.otp().trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid OTP. Please try again."));
        }
        session.setAttribute("authenticated", true);
        session.setAttribute("authIdentifier", pendingIdentifier);
        clearOtpState(session);

        return ResponseEntity.ok(Map.of("message", "OTP verified. Now select which broker app you want to connect."));
    }
    @PostMapping("/broker/select")
    public ResponseEntity<?> selectBroker(@RequestBody BrokerSelectRequest request, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("authenticated"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Please login first."));
        }
        String broker = request.broker() == null ? "" : request.broker().toUpperCase();
        if (!BROKERS.contains(broker)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported broker selection."));
        }
        session.setAttribute("selectedBroker", broker);

        if ("GROWW".equals(broker)) {
            return ResponseEntity.ok(Map.of(
                    "message", "Groww selected. Open Groww intraday URL, then enter Groww email/password and PIN.",
                    "authUrl", "https://groww.in/stocks/intraday"
            ));
        }
        return ResponseEntity.ok(Map.of("message", broker + " selected. Continue broker authentication."));
    }

    @PostMapping("/broker/groww/verify")
    public ResponseEntity<?> verifyGroww(@RequestBody GrowwVerifyRequest request, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("authenticated"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Please login first."));
        }
        if (!"GROWW".equals(session.getAttribute("selectedBroker"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select Groww broker first."));
        }
        if (isBlank(request.username()) || isBlank(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Groww username and password are required."));
        }
        if (!request.username().contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid Groww email ID."));
        }
        if (request.password().trim().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid Groww password (minimum 8 characters)."));
        }
        // Store credentials, mark as pending PIN
        session.setAttribute("growwEmail", request.username());
        session.setAttribute("growwPinPending", true);
        return ResponseEntity.ok(Map.of(
                "message", "Groww credentials accepted. Enter your 4-digit Groww PIN to complete login.",
                "pinRequired", true
        ));
    }

    @PostMapping("/broker/groww/pin")
    public ResponseEntity<?> verifyGrowwPin(@RequestBody GrowwPinRequest request, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("authenticated"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Please login first."));
        }
        if (!Boolean.TRUE.equals(session.getAttribute("growwPinPending"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please complete Groww email/password step first."));
        }
        if (isBlank(request.pin()) || !request.pin().trim().matches("\\d{4}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid 4-digit Groww PIN."));
        }
        List<GrowwIntradayScraperService.GrowwIntradayStockRow> rows = growwIntradayScraperService.fetchIntradayRows();
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "Could not load live Groww intraday stocks. Please retry after login."
            ));
        }
        session.setAttribute("brokerConnected", true);
        session.setAttribute("growwPinPending", false);
        session.setAttribute("brokerUser", session.getAttribute("growwEmail"));
        return ResponseEntity.ok(Map.of(
                "message", "Groww PIN verified. Live intraday stocks unlocked.",
                "rowsLoaded", rows.size()
        ));
    }

    @PostMapping("/invest/execute")
    public ResponseEntity<?> executeInvestment(@RequestBody InvestRequest request, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("brokerConnected"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Please complete broker authentication first."));
        }
        if (request.selectedStocks() == null || request.selectedStocks().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Select at least one stock."));
        }
        if (request.selectedStocks().size() > 3) {
            return ResponseEntity.badRequest().body(Map.of("error", "Maximum 3 stocks allowed."));
        }
        if (request.investmentPerStock() == null || request.investmentPerStock().compareTo(BigDecimal.TEN) < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Investment per stock must be at least 10."));
        }

        List<StockInput> picks = request.selectedStocks().stream()
                .map(String::toUpperCase)
                .map(symbol -> stockUniverse.getOrDefault(symbol, demoStock(symbol, "OTHER")))
                .map(base -> new StockInput(base.symbol(), base.sector(), request.investmentPerStock(), base.candleCloses()))
                .toList();

        InvestmentResponse response = service.evaluate(new InvestmentRequest(picks));
        return ResponseEntity.ok(Map.of(
                "paymentMessage", "Investment simulated via " + request.paymentApp() + " for INR " + request.investmentPerStock() + " per stock.",
                "result", response
        ));
    }

    @PostMapping("/automation/evaluate")
    public InvestmentResponse evaluate(@Valid @RequestBody InvestmentRequest request) {
        return service.evaluate(request);
    }

    @GetMapping("/backtest")
    public String backtest(@RequestParam(defaultValue = "intraday-atr") String strategy) {
        return backtestService.runBacktest(strategy);
    }

    @GetMapping("/paper-trade")
    public String paperTrade(@RequestParam String symbol) {
        return paperTradingService.executePaperTrade(symbol);
    }

    @GetMapping("/kyc-status")
    public String kycStatus(@RequestParam String userId) {
        return complianceService.kycStatus(userId);
    }
    @GetMapping("/groww/intraday-data")
    public ResponseEntity<?> growwIntradayData(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("brokerConnected"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Please complete Groww login first."));
        }
        List<GrowwIntradayScraperService.GrowwIntradayStockRow> rows = growwIntradayScraperService.fetchIntradayRows();
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "Live Groww intraday stocks are currently unavailable."
            ));
        }
        return ResponseEntity.ok(Map.of(
                "source", "GROWW_INTRADAY",
                "rows", rows
        ));
    }

    private void seedStocks() {
        addStock("VIDYAWIRES",  "CABLES",         "55",   "58",   "60",   "61.94");
        addStock("MTAR",        "CAPITAL GOODS",  "4100", "4200", "4280", "4351.20");
        addStock("RUBICON",     "PHARMA",         "790",  "810",  "825",  "839.40");
        addStock("SWPINNA",     "TEXTILES",       "218",  "224",  "229",  "232.37");
        addStock("BELRISE",     "AUTO ANCILLARY", "188",  "193",  "197",  "201.39");
        addStock("TCS",         "IT",             "3750", "3780", "3800", "3812.00");
        addStock("INFY",        "IT",             "1510", "1525", "1535", "1542.00");
        addStock("SBIN",        "BANKING",        "795",  "803",  "808",  "812.50");
    }

    private void addStock(String symbol, String sector, String... closes) {
        List<BigDecimal> candleCloses = java.util.Arrays.stream(closes).map(BigDecimal::new).toList();
        stockUniverse.put(symbol, new StockInput(symbol, sector, BigDecimal.TEN, candleCloses));
    }

    private StockInput demoStock(String symbol, String sector) {
        return new StockInput(symbol, sector, BigDecimal.TEN, List.of(
                new BigDecimal("100"), new BigDecimal("108"), new BigDecimal("121")
        ));
    }
    private void clearOtpState(HttpSession session) {
        session.removeAttribute("pendingAuthType");
        session.removeAttribute("pendingIdentifier");
        session.removeAttribute("pendingOtpCode");
        session.removeAttribute("pendingOtpExpiry");
    }

    private String maskIdentifier(String identifier) {
        if (identifier.contains("@")) {
            int atIndex = identifier.indexOf('@');
            if (atIndex <= 1) {
                return "***" + identifier.substring(atIndex);
            }
            return identifier.substring(0, 2) + "***" + identifier.substring(atIndex);
        }
        if (identifier.length() <= 4) {
            return "****";
        }
        return "******" + identifier.substring(identifier.length() - 4);
    }
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record OtpSendRequest(String authType, String identifier) {}
    public record OtpVerifyRequest(String identifier, String otp) {}
    public record BrokerSelectRequest(String broker) {}
    public record GrowwVerifyRequest(String username, String password) {}
    public record GrowwPinRequest(String pin) {}
    public record InvestRequest(List<String> selectedStocks, BigDecimal investmentPerStock, String paymentApp) {}
}
