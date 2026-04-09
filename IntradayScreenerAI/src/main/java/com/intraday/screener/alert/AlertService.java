package com.intraday.screener.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate = new RestTemplate();

    public AlertService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void notifyLargeLoss(String message) {
        log.warn("ALERT - Large loss: {}", message);
        sendEmail("Large loss alert", message);
    }

    public void notifyApiFailure(String message) {
        log.error("ALERT - API failure: {}", message);
        sendEmail("API failure alert", message);
    }

    public void notifyUnusualMarketCondition(String message) {
        log.warn("ALERT - Unusual market condition: {}", message);
        sendEmail("Unusual market condition alert", message);
    }

    public void notifyPositionSizeExceeded(String message) {
        log.warn("ALERT - Position size exceeded: {}", message);
        sendEmail("Position size exceeded", message);
    }

    public void sendSlackWebhook(String webhookUrl, String text) {
        try {
            restTemplate.postForEntity(webhookUrl, java.util.Map.of("text", text), String.class);
        } catch (RestClientException ex) {
            log.error("Slack webhook failed: {}", ex.getMessage());
        }
    }

    private void sendEmail(String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("risk-team@example.com");
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Email notification failed: {}", ex.getMessage());
        }
    }
}
