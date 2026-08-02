package com.example.demo.services;

import com.example.demo.clients.market.TwelveDataService;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.NormalizedCashOperationRepository;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountDaily;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformance;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformanceRepository;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.infrastructure.repository.account.AccountStatistics;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.benchmark.BenchmarkMonthlyClose;
import com.example.demo.infrastructure.repository.benchmark.BenchmarkMonthlyCloseRepository;
import com.example.demo.services.models.Benchmark;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BenchmarkService {

    private static final String BENCHMARK_SYMBOL = "SPY";
    private static final int FETCH_MONTHS = 120;
    private static final double ACTIVE_ACCOUNT_MIN_VALUE = 50.0;
    private static final CurrencyType BASE_CURRENCY = CurrencyType.USD;
    private static final Set<String> NET_DEPOSIT_CATEGORIES = Set.of(
            "EXTERNAL_DEPOSIT",
            "EXTERNAL_WITHDRAWAL",
            "INTERNAL_TRANSFER_IN",
            "INTERNAL_TRANSFER_OUT",
            "INTERNAL_BOOKKEEPING",
            "FX_CONVERSION",
            "CORRECTION");

    private final AccountDailyRepository accountDailyRepository;
    private final AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
    private final AccountRepository accountRepository;
    private final AccountStatisticsRepository accountStatisticsRepository;
    private final NormalizedCashOperationRepository normalizedCashOperationRepository;
    private final BenchmarkMonthlyCloseRepository benchmarkMonthlyCloseRepository;
    private final TwelveDataService twelveDataService;

    /**
     * Earliest month included in the comparison curve. Earlier periods had small balances
     * and no consistent strategy, so they're excluded from the benchmark by default.
     * Configurable via {@code app.benchmark.comparison-start} as {@code yyyy-MM}.
     */
    private final YearMonth comparisonStart;

    private LocalDate fetchAttemptedOn;

    public BenchmarkService(AccountDailyRepository accountDailyRepository,
                            AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository,
                            AccountRepository accountRepository,
                            AccountStatisticsRepository accountStatisticsRepository,
                            NormalizedCashOperationRepository normalizedCashOperationRepository,
                            BenchmarkMonthlyCloseRepository benchmarkMonthlyCloseRepository,
                            TwelveDataService twelveDataService,
                            @Value("${app.benchmark.comparison-start:2026-01}") String comparisonStart) {
        this.accountDailyRepository = accountDailyRepository;
        this.accountMonthlyPerformanceRepository = accountMonthlyPerformanceRepository;
        this.accountRepository = accountRepository;
        this.accountStatisticsRepository = accountStatisticsRepository;
        this.normalizedCashOperationRepository = normalizedCashOperationRepository;
        this.benchmarkMonthlyCloseRepository = benchmarkMonthlyCloseRepository;
        this.twelveDataService = twelveDataService;
        this.comparisonStart = YearMonth.parse(comparisonStart);
    }

    public Benchmark calculate() {
        return calculate(null);
    }

    public Benchmark calculate(Collection<Long> accountIds) {
        Benchmark benchmark = new Benchmark();
        try {
            List<AccountDaily> allRows = accountDailyRepository.findAll();
            Set<Long> requestedAccounts = accountIds == null ? Set.of() : new HashSet<>(accountIds);
            boolean filterSubmitted = accountIds != null;
            Set<Long> availableAccounts = activeAccountIds(allRows, accountStatisticsRepository.findAll());
            Set<Long> accountValueAccounts = activeAccountIds(allRows, List.of());
            Set<Long> cashOnlyAccounts = accountRepository.findAll().stream()
                    .filter(Account::isCashOnly)
                    .map(Account::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            availableAccounts.removeAll(cashOnlyAccounts);
            accountValueAccounts.removeAll(cashOnlyAccounts);
            Set<Long> selectedAccounts =
                    !filterSubmitted
                            ? availableAccounts
                            : requestedAccounts.stream()
                                    .filter(availableAccounts::contains)
                                    .collect(Collectors.toCollection(TreeSet::new));
            Map<Long, Account> accountsById = accountRepository.findMapByIdIn(availableAccounts);
            benchmark.setAccountOptions(accountOptions(
                    accountsById,
                    accountValueAccounts,
                    filterSubmitted ? selectedAccounts : accountValueAccounts));
            benchmark.setAccountValueYears(accountValueYears(allRows, accountValueAccounts, accountsById));
            benchmark.setAccountValuesAvailable(!benchmark.getAccountValueYears().isEmpty());
            benchmark.setSelectedAccountValueYear(
                    benchmark.isAccountValuesAvailable()
                            ? benchmark.getAccountValueYears().getFirst().year()
                            : null);

            List<AccountMonthlyPerformance> monthlyRows =
                    accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc().stream()
                            .filter(row -> row.getMonth() != null)
                            .filter(row -> availableAccounts.contains(row.getAccountId()))
                            .filter(row -> !YearMonth.from(row.getMonth()).isBefore(comparisonStart))
                            .toList();
            if (monthlyRows.isEmpty()) {
                return benchmark;
            }

            List<AccountMonthlyPerformance> selectedRows = monthlyRows.stream()
                    .filter(row -> selectedAccounts.contains(row.getAccountId()))
                    .toList();
            if (selectedRows.isEmpty()) {
                return benchmark;
            }

            YearMonth start = comparisonStart;
            YearMonth end =
                    monthlyRows.stream()
                            .map(AccountMonthlyPerformance::getMonth)
                            .map(YearMonth::from)
                            .max(Comparator.naturalOrder())
                            .orElse(start);
            if (end.isBefore(start)) {
                end = start;
            }
            List<String> labels = new ArrayList<>();
            for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
                labels.add(ym.toString());
            }

            List<Double> portfolioCurve = new ArrayList<>();
            List<Double> benchmarkCurve = new ArrayList<>();
            List<String> requiredCloseLabels = requiredCloseLabels(labels);
            NavigableMap<String, Double> closes = monthlyCloses(requiredCloseLabels);
            Double spyBase = exactCloseFor(closes, YearMonth.parse(labels.getFirst()).minusMonths(1).toString());
            if (spyBase == null || spyBase == 0.0) {
                return benchmark; // not enough data to compare
            }

            List<Benchmark.AccountSeries> accountSeries =
                    monthlyRows.stream()
                            .collect(Collectors.groupingBy(AccountMonthlyPerformance::getAccountId))
                            .entrySet()
                            .stream()
                            .map(entry -> accountSeries(entry.getKey(), entry.getValue(), labels, closes))
                            .filter(series -> series.investedCapital() != 0.0)
                            .sorted(Comparator.comparing(Benchmark.AccountSeries::id))
                            .toList();
            benchmark.setAccountSeries(accountSeries);

            List<Benchmark.AccountSeries> selectedSeries =
                    accountSeries.stream().filter(series -> selectedAccounts.contains(series.id())).toList();
            if (selectedSeries.isEmpty()) {
                return benchmark;
            }

            for (int i = 0; i < labels.size(); i++) {
                int index = i;
                portfolioCurve.add(round(selectedSeries.stream().mapToDouble(series -> series.portfolioCurve().get(index)).sum()));
                benchmarkCurve.add(round(selectedSeries.stream().mapToDouble(series -> series.benchmarkCurve().get(index)).sum()));
            }

            double investedCapital = selectedSeries.stream().mapToDouble(Benchmark.AccountSeries::investedCapital).sum();
            double portfolioPl = portfolioCurve.getLast();
            double benchmarkPl = benchmarkCurve.getLast();
            if (investedCapital == 0.0) {
                return benchmark;
            }

            benchmark.setLabels(labels);
            benchmark.setPortfolioCurve(portfolioCurve);
            benchmark.setBenchmarkCurve(benchmarkCurve);
            benchmark.setInvestedCapital(round(investedCapital));
            benchmark.setPortfolioPl(portfolioPl);
            benchmark.setBenchmarkPl(benchmarkPl);
            benchmark.setPortfolioReturnPct(round(portfolioPl / investedCapital * 100.0));
            benchmark.setBenchmarkReturnPct(round(benchmarkPl / investedCapital * 100.0));
            benchmark.setAlpha(round(benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct()));
            benchmark.setAvailable(true);
        } catch (Exception e) {
            log.error("Benchmark calculation failed: {}", e.getMessage(), e);
            benchmark.setAvailable(false);
        }
        return benchmark;
    }

    private Benchmark.AccountSeries accountSeries(
            Long accountId,
            List<AccountMonthlyPerformance> rows,
            List<String> labels,
            NavigableMap<String, Double> closes) {
        Map<String, AccountMonthlyPerformance> monthlyRows = rows.stream()
                .filter(row -> row.getMonth() != null)
                .collect(Collectors.toMap(
                        row -> YearMonth.from(row.getMonth()).toString(),
                        row -> row,
                        (first, ignored) -> first));
        List<Double> portfolioCurve = new ArrayList<>();
        List<Double> benchmarkCurve = new ArrayList<>();
        Optional<String> firstValueLabel =
                labels.stream()
                        .filter(
                                label ->
                                        monthlyRows.containsKey(label)
                                                && Math.abs(nz(monthlyRows.get(label).getStartEquity()))
                                                > ACTIVE_ACCOUNT_MIN_VALUE)
                        .findFirst();
        if (firstValueLabel.isEmpty()) {
            return new Benchmark.AccountSeries(accountId, 0.0, 0.0, 0.0, portfolioCurve, benchmarkCurve);
        }

        String startLabel = firstValueLabel.get();
        double basePortfolioValue = nz(monthlyRows.get(startLabel).getStartEquity());
        Double baseClose = exactCloseFor(closes, YearMonth.parse(startLabel).minusMonths(1).toString());
        if (baseClose == null || baseClose == 0.0) {
            return new Benchmark.AccountSeries(accountId, 0.0, 0.0, 0.0, portfolioCurve, benchmarkCurve);
        }

        double cumulativeProfit = 0.0;
        boolean started = false;
        for (String label : labels) {
            if (!started) {
                if (!label.equals(startLabel)) {
                    portfolioCurve.add(0.0);
                    benchmarkCurve.add(0.0);
                    continue;
                }
                started = true;
            }

            AccountMonthlyPerformance row = monthlyRows.get(label);
            if (row != null) {
                cumulativeProfit += nz(row.getProfit());
            }

            Double close = exactCloseFor(closes, label);
            if (close == null || close == 0.0) {
                return new Benchmark.AccountSeries(accountId, 0.0, 0.0, 0.0, List.of(), List.of());
            }
            double benchmarkPl = basePortfolioValue * (close / baseClose - 1.0);

            portfolioCurve.add(round(cumulativeProfit));
            benchmarkCurve.add(round(benchmarkPl));
        }

        return new Benchmark.AccountSeries(
                accountId,
                round(basePortfolioValue),
                portfolioCurve.isEmpty() ? 0.0 : portfolioCurve.getLast(),
                benchmarkCurve.isEmpty() ? 0.0 : benchmarkCurve.getLast(),
                portfolioCurve,
                benchmarkCurve);
    }

    private List<String> requiredCloseLabels(List<String> labels) {
        if (labels.isEmpty()) {
            return labels;
        }
        List<String> required = new ArrayList<>();
        required.add(YearMonth.parse(labels.getFirst()).minusMonths(1).toString());
        required.addAll(labels);
        return required;
    }

    private Set<Long> activeAccountIds(List<AccountDaily> rows, List<AccountStatistics> statistics) {
        if (!CollectionUtils.isEmpty(statistics)) {
            return statistics.stream()
                    .filter(stat -> stat.getAccountId() != null)
                    .filter(this::hasAccountValueSurface)
                    .map(AccountStatistics::getAccountId)
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        Map<Long, AccountDaily> latestByAccount = new HashMap<>();
        for (AccountDaily row : rows) {
            if (row.getAccountId() == null || row.getDate() == null) {
                continue;
            }
            AccountDaily current = latestByAccount.get(row.getAccountId());
            if (current == null || row.getDate().isAfter(current.getDate())) {
                latestByAccount.put(row.getAccountId(), row);
            }
        }
        return latestByAccount.values().stream()
                .filter(row -> nz(row.getEquity()) > ACTIVE_ACCOUNT_MIN_VALUE)
                .map(AccountDaily::getAccountId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private boolean hasAccountValueSurface(AccountStatistics stat) {
        return Math.abs(nz(stat.getCashBalance()) + nz(stat.getMarketValue())) > ACTIVE_ACCOUNT_MIN_VALUE
                || Math.abs(nz(stat.getNetDeposit())) > ACTIVE_ACCOUNT_MIN_VALUE;
    }

    private List<Benchmark.AccountOption> accountOptions(
            Map<Long, Account> accountsById, Set<Long> visibleAccounts, Set<Long> selectedAccounts) {
        return accountsById.values().stream()
                .filter(account -> visibleAccounts.contains(account.getId()))
                .map(account -> new Benchmark.AccountOption(
                        account.getId(),
                        account.getName(),
                        selectedAccounts.contains(account.getId())))
                .toList();
    }

    private List<Benchmark.AccountValueYear> accountValueYears(
            List<AccountDaily> dailyRows, Set<Long> availableAccounts, Map<Long, Account> accountsById) {
        if (CollectionUtils.isEmpty(dailyRows)
                || CollectionUtils.isEmpty(availableAccounts)
                || CollectionUtils.isEmpty(accountsById)) {
            return List.of();
        }

        Set<Long> allAccountIdsWithHistory = dailyRows.stream()
                .map(AccountDaily::getAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        Map<Long, Map<String, Double>> visibleNetDepositByDay =
                normalizedNetDepositByDay(availableAccounts);
        Map<Long, Map<String, Double>> portfolioNetDepositByDay =
                normalizedNetDepositByDay(allAccountIdsWithHistory);

        NavigableMap<Integer, List<AccountDaily>> rowsByYear = dailyRows.stream()
                .filter(row -> row.getDate() != null)
                .filter(row -> availableAccounts.contains(row.getAccountId()))
                .collect(Collectors.groupingBy(
                        row -> row.getDate().getYear(),
                        TreeMap::new,
                        Collectors.toList()));

        List<Benchmark.AccountValueYear> years = new ArrayList<>();
        rowsByYear.descendingMap().forEach((year, rows) -> {
            List<String> labels = dailyLabels(rows);
            List<Benchmark.AccountValueSeries> accountSeries = accountsById.values().stream()
                    .map(account -> dailyAccountValueSeries(
                            account,
                            rows,
                            labels,
                            visibleNetDepositByDay.getOrDefault(account.getId(), Map.of())))
                    .toList();
            if (!accountSeries.isEmpty()) {
                List<AccountDaily> allRowsForYear = dailyRows.stream()
                        .filter(row -> row.getDate() != null)
                        .filter(row -> row.getDate().getYear() == year)
                        .toList();
                Benchmark.AccountValueSeries totalSeries =
                        portfolioAccountValueSeries(allRowsForYear, labels, portfolioNetDepositByDay);
                years.add(new Benchmark.AccountValueYear(
                        year,
                        labels,
                        accountSeries,
                        totalSeries.profitValues(),
                        totalSeries.profitPctValues()));
            }
        });
        return years;
    }

    private Benchmark.AccountValueSeries dailyAccountValueSeries(
            Account account, List<AccountDaily> rows, List<String> labels, Map<String, Double> netDepositByDay) {
        Map<String, AccountDaily> valuesByDay = rows.stream()
                .filter(row -> account.getId().equals(row.getAccountId()))
                .collect(Collectors.groupingBy(
                        row -> row.getDate().toString(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                dayRows -> dayRows.stream()
                                        .max(Comparator.comparing(AccountDaily::getDate))
                                        .orElse(null))));
        List<Double> profitValues = new ArrayList<>();
        List<Double> profitPctValues = new ArrayList<>();
        Double startingEquity = null;
        double cumulativeNetDeposit = 0.0;
        for (String label : labels) {
            AccountDaily point = valuesByDay.get(label);
            double profit = profitValues.isEmpty() ? 0.0 : profitValues.getLast();
            cumulativeNetDeposit += netDepositByDay.getOrDefault(label, 0.0);
            if (point != null) {
                double equity = nz(point.getEquity());
                if (startingEquity == null && Math.abs(equity - cumulativeNetDeposit) > ACTIVE_ACCOUNT_MIN_VALUE) {
                    startingEquity = equity - cumulativeNetDeposit;
                }
                if (startingEquity != null) {
                    profit = equity - startingEquity - cumulativeNetDeposit;
                }
            }
            double profitPct =
                    startingEquity == null || Math.abs(startingEquity) <= ACTIVE_ACCOUNT_MIN_VALUE
                            ? 0.0
                            : profit / startingEquity * 100.0;
            profitValues.add(round(profit));
            profitPctValues.add(round(profitPct));
        }
        return new Benchmark.AccountValueSeries(account.getId(), account.getName(), profitValues, profitPctValues);
    }

    private Benchmark.AccountValueSeries portfolioAccountValueSeries(
            List<AccountDaily> rows,
            List<String> labels,
            Map<Long, Map<String, Double>> netDepositByDay) {
        Map<String, Double> equityByDay = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getDate().toString(),
                        TreeMap::new,
                        Collectors.summingDouble(row -> nz(row.getEquity()))));

        Map<String, Double> portfolioNetDepositByDay = new HashMap<>();
        netDepositByDay.values().forEach(byDay ->
                byDay.forEach((day, amount) -> portfolioNetDepositByDay.merge(day, amount, Double::sum)));

        List<Double> profitValues = new ArrayList<>();
        List<Double> profitPctValues = new ArrayList<>();
        Double startingEquity = null;
        double cumulativeNetDeposit = 0.0;
        for (String label : labels) {
            double profit = profitValues.isEmpty() ? 0.0 : profitValues.getLast();
            cumulativeNetDeposit += portfolioNetDepositByDay.getOrDefault(label, 0.0);
            Double equity = equityByDay.get(label);
            if (equity != null) {
                if (startingEquity == null && Math.abs(equity - cumulativeNetDeposit) > ACTIVE_ACCOUNT_MIN_VALUE) {
                    startingEquity = equity - cumulativeNetDeposit;
                }
                if (startingEquity != null) {
                    profit = equity - startingEquity - cumulativeNetDeposit;
                }
            }
            double profitPct =
                    startingEquity == null || Math.abs(startingEquity) <= ACTIVE_ACCOUNT_MIN_VALUE
                            ? 0.0
                            : profit / startingEquity * 100.0;
            profitValues.add(round(profit));
            profitPctValues.add(round(profitPct));
        }
        return new Benchmark.AccountValueSeries(0L, "Portfolio total", profitValues, profitPctValues);
    }

    private Map<Long, Map<String, Double>> normalizedNetDepositByDay(Set<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return Map.of();
        }
        return normalizedCashOperationRepository.findAllByAccountIdIn(accountIds).stream()
                .filter(row -> NET_DEPOSIT_CATEGORIES.contains(row.getNormalizedCategory()))
                .collect(Collectors.groupingBy(
                        NormalizedCashOperationRepository.NormalizedCashOperationRow::getAccountId,
                        Collectors.groupingBy(
                                row -> row.getDate().toString(),
                                Collectors.summingDouble(row -> nz(row.getAmountInBaseCurrency())))));
    }

    private List<String> dailyLabels(List<AccountDaily> rows) {
        List<LocalDate> dates = rows.stream()
                .map(AccountDaily::getDate)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (dates.isEmpty()) {
            return List.of();
        }
        LocalDate first = dates.getFirst();
        LocalDate last = dates.getLast();
        List<String> labels = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            labels.add(day.toString());
        }
        return labels;
    }

    private synchronized NavigableMap<String, Double> monthlyCloses(List<String> requiredLabels) {
        NavigableMap<String, Double> cached = loadCachedCloses();
        if (!hasRequiredCloses(cached, requiredLabels) && shouldFetchToday()) {
            NavigableMap<String, Double> fetched = twelveDataService.fetchMonthlyCloses(BENCHMARK_SYMBOL, FETCH_MONTHS);
            fetchAttemptedOn = LocalDate.now();
            if (!CollectionUtils.isEmpty(fetched)) {
                persistFetchedCloses(fetched);
                cached = loadCachedCloses();
            }
        }
        return cached;
    }

    private NavigableMap<String, Double> loadCachedCloses() {
        NavigableMap<String, Double> closes = new TreeMap<>();
        for (BenchmarkMonthlyClose row :
                benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc(BENCHMARK_SYMBOL)) {
            if (row.getMonthDate() != null && row.getClosePrice() != null) {
                closes.put(YearMonth.from(row.getMonthDate()).toString(), row.getClosePrice());
            }
        }
        return closes;
    }

    private boolean hasRequiredCloses(NavigableMap<String, Double> closes, List<String> labels) {
        if (labels.isEmpty() || closes.isEmpty()) {
            return false;
        }
        for (String label : labels) {
            if (!closes.containsKey(label)) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldFetchToday() {
        return !LocalDate.now().equals(fetchAttemptedOn);
    }

    private void persistFetchedCloses(NavigableMap<String, Double> fetched) {
        Map<String, BenchmarkMonthlyClose> existing =
                benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc(BENCHMARK_SYMBOL).stream()
                        .filter(row -> row.getMonthDate() != null)
                        .collect(Collectors.toMap(
                                row -> YearMonth.from(row.getMonthDate()).toString(),
                                row -> row,
                                (first, ignored) -> first,
                                TreeMap::new));

        ZonedDateTime now = ZonedDateTime.now();
        List<BenchmarkMonthlyClose> rows = new ArrayList<>();
        fetched.forEach((month, close) -> {
            if (close == null || close == 0.0) {
                return;
            }
            BenchmarkMonthlyClose row = existing.get(month);
            if (row == null) {
                row = BenchmarkMonthlyClose.builder()
                        .symbol(BENCHMARK_SYMBOL)
                        .monthDate(YearMonth.parse(month).atDay(1))
                        .build();
            }
            row.setClosePrice(close);
            row.setFetchedAt(now);
            rows.add(row);
        });
        if (!rows.isEmpty()) {
            benchmarkMonthlyCloseRepository.saveAll(rows);
        }
    }

    private Double exactCloseFor(NavigableMap<String, Double> closes, String label) {
        return closes.get(label);
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

