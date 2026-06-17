package com.example.demo.services;

import com.example.demo.clients.TwelveDataService;
import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.*;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.models.Benchmark;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
public class BenchmarkService {

    private static final CurrencyType BASE = CurrencyType.USD;
    private static final String BENCHMARK_SYMBOL = "SPY";

    private final ClosedPositionRepository closedPositionRepository;
    private final CashOperationRepository cashOperationRepository;
    private final AccountSummaryRepository accountSummaryRepository;
    private final OpenedPositionRepository openedPositionRepository;
    private final CurrencyRateService currencyRateService;
    private final TwelveDataService twelveDataService;

    /**
     * Earliest month included in the comparison curve. Earlier periods had small balances
     * and no consistent strategy, so they're excluded from the benchmark by default.
     * Configurable via {@code app.benchmark.comparison-start} as {@code yyyy-MM}.
     */
    private final YearMonth comparisonStart;

    /**
     * Daily-refreshed cache of benchmark monthly closes. Instance-scoped (was
     * {@code static}) so multiple Spring contexts and parallel tests don't share state.
     */
    private NavigableMap<String, Double> cachedCloses;
    private LocalDate cachedOn;

    public BenchmarkService(ClosedPositionRepository closedPositionRepository,
                            CashOperationRepository cashOperationRepository,
                            AccountSummaryRepository accountSummaryRepository,
                            OpenedPositionRepository openedPositionRepository,
                            CurrencyRateService currencyRateService,
                            TwelveDataService twelveDataService,
                            @Value("${app.benchmark.comparison-start:2026-01}") String comparisonStart) {
        this.closedPositionRepository = closedPositionRepository;
        this.cashOperationRepository = cashOperationRepository;
        this.accountSummaryRepository = accountSummaryRepository;
        this.openedPositionRepository = openedPositionRepository;
        this.currencyRateService = currencyRateService;
        this.twelveDataService = twelveDataService;
        this.comparisonStart = YearMonth.parse(comparisonStart);
    }

    public Benchmark calculate() {
        Benchmark benchmark = new Benchmark();
        try {
            List<ClosedPosition> closed = closedPositionRepository.findAll();
            if (closed.isEmpty()) {
                return benchmark; // available=false
            }

            // Monthly realized P/L (incl. commission + swap) and dividends, in base USD.
            // Comparison starts at comparisonStart -- earlier amounts were small with no consistent strategy.
            TreeMap<String, Double> monthly = new TreeMap<>();
            for (ClosedPosition p : closed) {
                if (p.getCloseTime() == null) {
                    continue;
                }
                YearMonth month = YearMonth.from(p.getCloseTime());
                if (month.isBefore(comparisonStart)) {
                    continue;
                }
                double value = convert(nz(p.getProfit()) + nz(p.getCommission()) + nz(p.getSwap()), p.getCurrency());
                monthly.merge(month.toString(), value, Double::sum);
            }
            for (CashOperation c : cashOperationRepository.findAll()) {
                if (c.getType() == CashOperationType.DIVIDEND && c.getDate() != null
                        && !YearMonth.from(c.getDate()).isBefore(comparisonStart)) {
                    monthly.merge(YearMonth.from(c.getDate()).toString(),
                            convert(nz(c.getAmount()), c.getCurrency()), Double::sum);
                }
            }
            if (monthly.isEmpty()) {
                return benchmark;
            }

            // Continuous month labels from the comparison start to the current month.
            YearMonth start = comparisonStart;
            YearMonth end = YearMonth.now();
            if (end.isBefore(start)) {
                end = start;
            }
            List<String> labels = new ArrayList<>();
            for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
                labels.add(ym.toString());
            }

            // Portfolio cumulative realized P/L curve.
            List<Double> portfolioCurve = new ArrayList<>();
            double cumulative = 0.0;
            for (String label : labels) {
                cumulative += monthly.getOrDefault(label, 0.0);
                portfolioCurve.add(round(cumulative));
            }

            double investedCapital = investedCapital();

            // Benchmark: same capital tracking SPY, expressed as cumulative USD P/L.
            NavigableMap<String, Double> closes = monthlyCloses();
            Double spyBase = closeFor(closes, labels.get(0));
            if (spyBase == null || spyBase == 0.0 || investedCapital == 0.0) {
                return benchmark; // not enough data to compare
            }
            List<Double> benchmarkCurve = new ArrayList<>();
            double lastClose = spyBase;
            for (String label : labels) {
                Double close = closeFor(closes, label);
                if (close == null) {
                    close = lastClose;
                } else {
                    lastClose = close;
                }
                benchmarkCurve.add(round(investedCapital * (close / spyBase - 1.0)));
            }

            double portfolioPl = portfolioCurve.get(portfolioCurve.size() - 1);
            double benchmarkPl = benchmarkCurve.get(benchmarkCurve.size() - 1);

            benchmark.setLabels(labels);
            benchmark.setPortfolioCurve(portfolioCurve);
            benchmark.setBenchmarkCurve(benchmarkCurve);
            benchmark.setInvestedCapital(round(investedCapital));
            benchmark.setPortfolioPl(portfolioPl);
            benchmark.setBenchmarkPl(benchmarkPl);
            benchmark.setPortfolioReturnPct(round(portfolioPl / investedCapital * 100.0));
            benchmark.setBenchmarkReturnPct(round((lastClose / spyBase - 1.0) * 100.0));
            benchmark.setAlpha(round(benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct()));
            benchmark.setAvailable(true);
        } catch (Exception e) {
            log.error("Benchmark calculation failed: {}", e.getMessage(), e);
            benchmark.setAvailable(false);
        }
        return benchmark;
    }

    private synchronized NavigableMap<String, Double> monthlyCloses() {
        LocalDate today = LocalDate.now();
        if (cachedCloses == null || !today.equals(cachedOn)) {
            NavigableMap<String, Double> fetched = twelveDataService.fetchMonthlyCloses(BENCHMARK_SYMBOL, 120);
            if (!fetched.isEmpty()) {
                cachedCloses = fetched;
                cachedOn = today;
            } else if (cachedCloses == null) {
                cachedCloses = new TreeMap<>();
            }
        }
        return cachedCloses;
    }

    /** Capital base for the benchmark "paper account": current equity, else open-position value. */
    private double investedCapital() {
        double equity = accountSummaryRepository.findAll().stream()
                .mapToDouble(s -> convert(nz(s.getEquity()), s.getCurrency()))
                .sum();
        if (equity != 0.0) {
            return equity;
        }
        return openedPositionRepository.findAll().stream()
                .mapToDouble(p -> convert(nz(p.getPurchaseValue()) + nz(p.getProfit()), p.getCurrency()))
                .sum();
    }

    private Double closeFor(NavigableMap<String, Double> closes, String label) {
        if (closes.isEmpty()) {
            return null;
        }
        if (closes.containsKey(label)) {
            return closes.get(label);
        }
        Map.Entry<String, Double> floor = closes.floorEntry(label); // "yyyy-MM" sorts chronologically
        if (floor != null) {
            return floor.getValue();
        }
        return closes.firstEntry().getValue();
    }

    private double convert(double amount, CurrencyType currency) {
        return currencyRateService.convertToBaseCurrency(amount, BASE, currency != null ? currency : BASE);
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

