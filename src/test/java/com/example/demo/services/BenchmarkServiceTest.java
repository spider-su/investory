package com.example.demo.services;

import com.example.demo.clients.TwelveDataService;
import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.*;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.models.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceTest {

    @Mock private ClosedPositionRepository closedPositionRepository;
    @Mock private CashOperationRepository cashOperationRepository;
    @Mock private AccountSummaryRepository accountSummaryRepository;
    @Mock private OpenedPositionRepository openedPositionRepository;
    @Mock private CurrencyRateService currencyRateService;
    @Mock private TwelveDataService twelveDataService;

    private BenchmarkService benchmarkService;

    @BeforeEach
    void setUp() {
        benchmarkService = new BenchmarkService(closedPositionRepository, cashOperationRepository,
                accountSummaryRepository, openedPositionRepository, currencyRateService, twelveDataService,
                "2026-01");
        // Identity FX so calculations stay readable. lenient() because the empty-portfolio test skips FX.
        org.mockito.Mockito.lenient().when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
    }

    @Test
    void calculate_returnsUnavailableWhenNoClosedPositions() {
        when(closedPositionRepository.findAll()).thenReturn(List.of());

        Benchmark benchmark = benchmarkService.calculate();

        assertFalse(benchmark.isAvailable());
    }

    @Test
    void calculate_buildsPortfolioAndBenchmarkCurves() {
        ClosedPosition trade = new ClosedPosition();
        trade.setProfit(100.0);
        trade.setCommission(0.0);
        trade.setSwap(0.0);
        trade.setCurrency(CurrencyType.USD);
        trade.setCloseTime(ZonedDateTime.now().withYear(2026).withMonth(2));

        when(closedPositionRepository.findAll()).thenReturn(List.of(trade));
        when(cashOperationRepository.findAll()).thenReturn(List.of(
                cashDividend(20.0, ZonedDateTime.now().withYear(2026).withMonth(3))
        ));
        // Has equity so investedCapital() returns it directly.
        AccountSummary summary = new AccountSummary();
        summary.setAccount("XTB");
        summary.setCurrency(CurrencyType.USD);
        summary.setEquity(10000.0);
        when(accountSummaryRepository.findAll()).thenReturn(List.of(summary));

        TreeMap<String, Double> closes = new TreeMap<>();
        closes.put("2026-01", 500.0);
        closes.put("2026-02", 525.0);
        closes.put("2026-03", 550.0);
        when(twelveDataService.fetchMonthlyCloses(anyString(), anyInt())).thenReturn(closes);

        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAvailable());
        assertNotNull(benchmark.getLabels());
        assertFalse(benchmark.getLabels().isEmpty());
        assertEquals(10000.0, benchmark.getInvestedCapital());
        // Portfolio cumulative P/L by the end should be 100 + 20 = 120
        assertEquals(120.0, benchmark.getPortfolioPl(), 0.01);
        // Alpha is portfolioPct - benchmarkPct
        assertEquals(benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct(),
                benchmark.getAlpha(), 0.01);
    }

    @Test
    void calculate_returnsUnavailableWhenSpyDataIsEmpty() {
        ClosedPosition trade = new ClosedPosition();
        trade.setProfit(100.0);
        trade.setCommission(0.0);
        trade.setSwap(0.0);
        trade.setCurrency(CurrencyType.USD);
        trade.setCloseTime(ZonedDateTime.now().withYear(2026).withMonth(2));

        when(closedPositionRepository.findAll()).thenReturn(List.of(trade));
        when(cashOperationRepository.findAll()).thenReturn(List.of());
        AccountSummary summary = new AccountSummary();
        summary.setEquity(1000.0);
        summary.setCurrency(CurrencyType.USD);
        when(accountSummaryRepository.findAll()).thenReturn(List.of(summary));
        when(twelveDataService.fetchMonthlyCloses(anyString(), anyInt())).thenReturn(new TreeMap<>());

        Benchmark benchmark = benchmarkService.calculate();
        assertFalse(benchmark.isAvailable());
    }

    private static CashOperation cashDividend(double amount, ZonedDateTime date) {
        CashOperation c = new CashOperation();
        c.setType(CashOperationType.DIVIDEND);
        c.setAmount(amount);
        c.setCurrency(CurrencyType.USD);
        c.setDate(date);
        return c;
    }
}

