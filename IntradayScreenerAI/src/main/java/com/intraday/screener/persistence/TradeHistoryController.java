package com.intraday.screener.persistence;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.intraday.screener.service.TradeDecisionMemoryStore;

@RestController
@RequestMapping("/api/trade-history")
public class TradeHistoryController {

    private final TradeDecisionMemoryStore store;

    public TradeHistoryController(TradeDecisionMemoryStore store) {
        this.store = store;
    }

    @GetMapping
    public List<TradeDecisionMemoryStore.TradeDecisionSnapshot> getHistory() {
        return store.findAll();
    }
}
