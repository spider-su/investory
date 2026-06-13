package com.example.demo.controllers.rest;

import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.OpenPositionHistory;
import com.example.demo.services.HistoryService;
import com.example.demo.services.MarketService;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.models.InstrumentPerformance;
import com.example.demo.services.models.OpenPositionsPerformance;
import com.example.demo.services.models.Performance;
import com.example.demo.services.models.Portfolio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final HistoryService historyService;
    private final MarketService marketService;

    // Endpoint to get the total profit/loss
    @GetMapping("/total-profit-loss")
    public Portfolio getTotalProfitLoss() {
        return portfolioService.calculateTotalProfitLoss();
    }

    // Endpoint to get the monthly performance
    @GetMapping("/monthly-performance")
    public Performance getMonthlyPerformance() {
        return portfolioService.calculateMonthlyPerformance();
    }

    // Endpoint to get the win rate
    @GetMapping("/win-rate")
    public double getWinRate() {
        return portfolioService.calculateWinRate();
    }

    // Endpoint to get the largest win/loss
    @GetMapping("/largest-win-loss")
    public Map<String, Double> getLargestWinLoss() {
        return portfolioService.calculateLargestWinLoss();
    }

    // Endpoint to get performance per instrument
    @GetMapping("/performance-per-instrument")
    public List<InstrumentPerformance> getPerformancePerInstrument(@RequestParam CurrencyType baseCurrency) {
        return portfolioService.calculatePerformancePerInstrument(baseCurrency);
    }

    // Endpoint to get cash flow over time
    @GetMapping("/cash-flow")
    public Map<String, Double> getCashFlowOverTime(@RequestParam CurrencyType baseCurrency) {
        return portfolioService.calculateCashFlowOverTime(baseCurrency);
    }

    @GetMapping("/open-positions-flow")
    public Map<String, OpenPositionsPerformance> getOpenPositionsFlow() {
        return portfolioService.getOpenPositionsFlow();
    }

    @PostMapping("/history")
    public Collection<OpenPositionHistory> getCashFlowOverTime() {
        return historyService.saveHistory();
    }

//    // Endpoint to get dividends received
//    @GetMapping("/dividends-received")
//    public double getDividendsReceived() {
//        return portfolioService.calculateDividendsReceived();
//    }

    @PostMapping("/sync")
    void sync() {
        try {
            marketService.fullPortfolioUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OpenedPositions stock", e);
        }
    }
}
