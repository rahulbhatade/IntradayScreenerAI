package com.intraday.screener.compliance;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ComplianceService {

    public String disclaimer() {
        return "This platform is for educational and assisted execution purposes only. Ensure SEBI and broker compliance before live deployment.";
    }

    public String kycStatus(String userId) {
        return "KYC check placeholder for user " + userId + " at " + Instant.now();
    }
}
