package com.intraday.screener.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataWebSocketHandler.class);

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.info("Received market feed payload: {}", message.getPayload());
    }
}
