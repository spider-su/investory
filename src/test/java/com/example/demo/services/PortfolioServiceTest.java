package com.example.demo.services;

import com.example.demo.data.CashOperationType;
import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.*;
import com.example.demo.services.models.InstrumentPerformance;
import com.example.demo.services.models.Performance;
import com.example.demo.services.models.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock private CurrencyRateService currencyRateService;
    @Mock private ClosedPositionRepository closedPositionRepository;
    @Mock private OpenedPositionRepository openedPositionRepository;
    @Mock private OpenPositionHistoryRepository openPositionHistoryRepository;
    @Mock private CashOperationRepository cashOperationRepository;
    @Mock private AccountSummaryRepository accountSummaryRepository;

    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        // Use real helpers so the test still exercises end-to-end behaviour after the extraction.
        TaxCalculator taxCalculator = new TaxCalculator(currencyRateService);
        CashFlowAggregator cashFlowAggregator = new CashFlowAggregator(currencyRateService);
        portfolioService = new PortfolioService(currencyRateService,
                closedPositionRepository, openedPositionRepository, openPositionHistoryRepository,
                cashOperationRepository, accountSummaryRepository,
                taxCalculator, cashFlowAggregator);
        // Identity FX so tests are arithmetic-only. lenient() because some tests don't trigger FX conversion.
        org.mockito.Mockito.lenient().when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
    }

    @Test
    void calculateTotalProfitLoss_aggregatesRealizedAndUnrealizedAndDividends() {
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("AAPL.US", 100.0, -1.0, 0.0, ZonedDateTime.now().minusDays(3))
        ));
        when(openedPositionRepository.findAll()).thenReturn(List.of(
                opened("MSFT.US", 50.0, 0.0, 0.0)
        ));
        when(cashOperationRepository.findAll()).thenReturn(List.of(
                cash(CashOperationType.DIVIDEND, 25.0, null),
                cash(CashOperationType.DEPOSIT, 1000.0, "wire transfer"),
                cash(CashOperationType.WITHDRAWAL, -200.0, "wire transfer")
        ));
        when(accountSummaryRepository.findAll()).thenReturn(List.of(
                summary("XTB", 5000.0)
        ));

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertEquals(99.0, result.getTotalProfitInBase(), 0.01);     // 100 - 1
        assertEquals(50.0, result.getTotalUnrealizedInBase(), 0.01);
        assertEquals(25.0, result.getDividends(), 0.01);
        assertEquals(174.0, result.getTotal(), 0.01);
        assertEquals(1000.0, result.getDeposits(), 0.01);
        assertEquals(-200.0, result.getWithdrawals(), 0.01);
        assertEquals(800.0, result.getNetDeposits(), 0.01);
        assertEquals(5000.0, result.getBalance(), 0.01);
    }

    @Test
    void calculateTotalProfitLoss_excludesCurrencyConversionDeposits() {
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        when(openedPositionRepository.findAll()).thenReturn(List.of());
        when(cashOperationRepository.findAll()).thenReturn(List.of(
                cash(CashOperationType.DEPOSIT, 500.0, "Currency Conversion EUR -> USD"),
                cash(CashOperationType.DEPOSIT, 1000.0, "Bank deposit")
        ));
        when(accountSummaryRepository.findAll()).thenReturn(List.of());

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        // Currency-conversion row excluded -> only the 1000 USD deposit counts.
        assertEquals(1000.0, result.getDeposits(), 0.01);
    }

    @Test
    void calculateTotalProfitLoss_appliesCapitalGainsTaxOnCurrentYearGains() {
        int year = java.time.Year.now().getValue();
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("AAPL.US", 1000.0, 0.0, 0.0, ZonedDateTime.now().withYear(year))
        ));
        when(openedPositionRepository.findAll()).thenReturn(List.of());
        when(cashOperationRepository.findAll()).thenReturn(List.of());
        when(accountSummaryRepository.findAll()).thenReturn(List.of());

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertEquals(190.0, result.getCapitalGainsTax(), 0.01);     // 19% of 1000
        assertEquals(0.0, result.getLossCarryForward(), 0.01);
    }

    @Test
    void calculateWinRate_returnsZeroWhenNoTrades() {
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        assertEquals(0.0, portfolioService.calculateWinRate());
    }

    @Test
    void calculateWinRate_countsOnlyProfitableTrades() {
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("A", 10.0, 0.0, 0.0, ZonedDateTime.now()),
                closed("B", -5.0, 0.0, 0.0, ZonedDateTime.now()),
                closed("C", 20.0, 0.0, 0.0, ZonedDateTime.now()),
                closed("D", -2.0, 0.0, 0.0, ZonedDateTime.now())
        ));

        assertEquals(50.0, portfolioService.calculateWinRate());
    }

    @Test
    void calculateLargestWinLoss_returnsMaxAndMinProfit() {
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("A", 100.0, 0.0, 0.0, ZonedDateTime.now()),
                closed("B", -50.0, 0.0, 0.0, ZonedDateTime.now()),
                closed("C", 25.0, 0.0, 0.0, ZonedDateTime.now())
        ));

        Map<String, Double> result = portfolioService.calculateLargestWinLoss();
        assertEquals(100.0, result.get("largestWin"));
        assertEquals(-50.0, result.get("largestLoss"));
    }

    @Test
    void calculatePerformancePerInstrument_includesBothOpenAndClosedPositions() {
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("AAPL.US", 100.0, 0.0, 0.0, ZonedDateTime.now())
        ));
        when(openedPositionRepository.findAll()).thenReturn(List.of(
                opened("AAPL.US", 50.0, 0.0, 0.0),
                opened("MSFT.US", 30.0, 0.0, 0.0)
        ));

        List<InstrumentPerformance> performance = portfolioService.calculatePerformancePerInstrument(CurrencyType.USD);

        assertEquals(2, performance.size());
        InstrumentPerformance aapl = performance.stream()
                .filter(p -> "AAPL.US".equals(p.getSymbol())).findFirst().orElseThrow();
        assertEquals(100.0, aapl.getClosedProfit(), 0.01);
        assertEquals(50.0, aapl.getUnrealizedProfit(), 0.01);
        assertEquals(150.0, aapl.getTotal(), 0.01);
    }

    @Test
    void calculateMonthlyPerformance_bucketsByYearAndMonth() {
        int year = java.time.Year.now().getValue();
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("AAPL.US", 100.0, 0.0, 0.0, ZonedDateTime.now().withYear(year).withMonth(3).withDayOfMonth(15)),
                closed("MSFT.US", 50.0, 0.0, 0.0, ZonedDateTime.now().withYear(year - 1).withMonth(7).withDayOfMonth(10))
        ));
        when(openedPositionRepository.findAll()).thenReturn(List.of());

        Performance perf = portfolioService.calculateMonthlyPerformance();
        Map<String, Double> monthly = perf.getCalculateMonthlyPerformance();
        // Past year is bucketed by year only; current year by year-month.
        assertTrue(monthly.containsKey(String.valueOf(year - 1)));
        assertTrue(monthly.keySet().stream().anyMatch(k -> k.startsWith(year + "-")));
    }

    @Test
    void calculateCashFlowOverTime_groupsByCloseTime() {
        ZonedDateTime when = ZonedDateTime.now();
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("A", 10.0, 0.0, 0.0, when),
                closed("B", 5.0, 0.0, 0.0, when)
        ));

        Map<String, Double> cashFlow = portfolioService.calculateCashFlowOverTime(CurrencyType.USD);
        assertEquals(1, cashFlow.size());
        assertEquals(15.0, cashFlow.values().iterator().next());
    }

    private static ClosedPosition closed(String symbol, double profit, double commission, double swap, ZonedDateTime closeTime) {
        ClosedPosition cp = new ClosedPosition();
        cp.setSymbol(symbol);
        cp.setCurrency(CurrencyType.USD);
        cp.setProfit(profit);
        cp.setCommission(commission);
        cp.setSwap(swap);
        cp.setCloseTime(closeTime);
        cp.setVolume(1.0);
        cp.setOpenPrice(100.0);
        cp.setClosePrice(100.0 + profit);
        return cp;
    }

    private static OpenedPosition opened(String symbol, double profit, double commission, double swap) {
        OpenedPosition op = new OpenedPosition();
        op.setSymbol(symbol);
        op.setCurrency(CurrencyType.USD);
        op.setProfit(profit);
        op.setCommission(commission);
        op.setSwap(swap);
        op.setVolume(1.0);
        op.setOpenPrice(100.0);
        op.setMarketPrice(100.0 + profit);
        op.setPurchaseValue(100.0);
        return op;
    }

    private static CashOperation cash(CashOperationType type, double amount, String comment) {
        CashOperation c = new CashOperation();
        c.setType(type);
        c.setAmount(amount);
        c.setCurrency(CurrencyType.USD);
        c.setComment(comment);
        c.setDate(ZonedDateTime.now());
        return c;
    }

    private static AccountSummary summary(String account, double equity) {
        AccountSummary s = new AccountSummary();
        s.setAccount(account);
        s.setCurrency(CurrencyType.USD);
        s.setEquity(equity);
        s.setBalance(equity);
        return s;
    }
}

