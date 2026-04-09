package com.intraday.screener.integration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class MarketDataCacheService {

    private final StringRedisTemplate redisTemplate;

    public MarketDataCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void cacheLastPrice(String symbol, String value) {
        redisTemplate.opsForValue().set("market:last:" + symbol, value, Duration.ofMinutes(5));
    }

    public String getLastPrice(String symbol) {
        return redisTemplate.opsForValue().get("market:last:" + symbol);
    }
}
