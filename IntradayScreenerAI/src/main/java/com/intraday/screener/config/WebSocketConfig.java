package com.intraday.screener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final com.intraday.screener.integration.MarketDataWebSocketHandler marketDataWebSocketHandler;

    public WebSocketConfig(com.intraday.screener.integration.MarketDataWebSocketHandler marketDataWebSocketHandler) {
        this.marketDataWebSocketHandler = marketDataWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketDataWebSocketHandler, "/ws/market-data").setAllowedOrigins("*");
    }
}
