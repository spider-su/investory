package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.model.Benchmark;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Coordinates benchmark reporting; provider storage and account-value composition live separately.
 */
@Slf4j
@Service
public class BenchmarkService {

  private static final double ACTIVE_ACCOUNT_MIN_VALUE = 50.0;

  private final AccountDailyRepository accountDailyRepository;
  private final AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
  private final AccountRepository accountRepository;
  private final AccountStatisticsRepository accountStatisticsRepository;
  private final BenchmarkAccountValueService accountValueService;
  private final BenchmarkMarketDataService marketDataService;
  private final YearMonth historyStart;

  public BenchmarkService(
      AccountDailyRepository accountDailyRepository,
      AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository,
      AccountRepository accountRepository,
      AccountStatisticsRepository accountStatisticsRepository,
      BenchmarkAccountValueService accountValueService,
      BenchmarkMarketDataService marketDataService,
      @Value("${app.history-start:2025-01-01}") String historyStart) {
    this.accountDailyRepository = accountDailyRepository;
    this.accountMonthlyPerformanceRepository = accountMonthlyPerformanceRepository;
    this.accountRepository = accountRepository;
    this.accountStatisticsRepository = accountStatisticsRepository;
    this.accountValueService = accountValueService;
    this.marketDataService = marketDataService;
    this.historyStart = parseHistoryStart(historyStart);
  }

  @Cacheable(cacheNames = "benchmark", key = "'all'")
  public Benchmark calculate() {
    return calculate(null, null);
  }

  @Cacheable(cacheNames = "benchmark", keyGenerator = "investmentCalculationKeyGenerator")
  public Benchmark calculate(Collection<Long> accountIds) {
    return calculate(null, accountIds);
  }

  @Cacheable(cacheNames = "benchmark", keyGenerator = "investmentCalculationKeyGenerator")
  public Benchmark calculate(Long portfolioId, Collection<Long> accountIds) {
    Benchmark benchmark = new Benchmark();
    try {
      List<AccountDailyEntity> allRows =
          accountDailyRepository.findByDateGreaterThanEqualOrderByDateAscAccountIdAsc(
              historyStart.atDay(1));
      Set<Long> portfolioAccounts =
          accountRepository.findAll().stream()
              .filter(
                  account ->
                      portfolioId == null || Objects.equals(account.getPortfolioId(), portfolioId))
              .map(AccountEntity::getId)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
      allRows =
          allRows.stream()
              .filter(row -> portfolioId == null || portfolioAccounts.contains(row.getAccountId()))
              .toList();
      Set<Long> requestedAccounts =
          com.smartbox.investory.shared.util.CollectionUtils.immutableSetOrEmpty(accountIds);
      boolean filterSubmitted = accountIds != null;
      Set<Long> eligibleAccounts =
          accountValueService.activeAccountIds(allRows, accountStatisticsRepository.findAll());
      Set<Long> allPortfolioAccounts =
          allRows.stream()
              .map(AccountDailyEntity::getAccountId)
              .filter(Objects::nonNull)
              .collect(Collectors.toCollection(TreeSet::new));
      Set<Long> cashOnlyAccounts =
          accountRepository.findAll().stream()
              .filter(AccountEntity::isCashOnly)
              .map(AccountEntity::getId)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
      eligibleAccounts.removeAll(cashOnlyAccounts);
      Set<Long> performanceAccounts = new TreeSet<>(allPortfolioAccounts);
      performanceAccounts.removeAll(cashOnlyAccounts);
      Set<Long> selectedAccounts =
          !filterSubmitted
              ? eligibleAccounts
              : requestedAccounts.stream()
                  .filter(eligibleAccounts::contains)
                  .collect(Collectors.toCollection(TreeSet::new));
      Map<Long, AccountEntity> accountsById = accountRepository.findMapByIdIn(performanceAccounts);
      benchmark.setAccountOptions(
          accountValueService.accountOptions(
              accountsById,
              eligibleAccounts,
              filterSubmitted ? selectedAccounts : eligibleAccounts));
      Set<Long> valueAccounts = filterSubmitted ? selectedAccounts : performanceAccounts;
      benchmark.setAccountValueYears(
          accountValueService.accountValueYears(allRows, valueAccounts, accountsById));
      benchmark.setAccountValuesAvailable(!benchmark.getAccountValueYears().isEmpty());
      benchmark.setSelectedAccountValueYear(
          benchmark.isAccountValuesAvailable()
              ? benchmark.getAccountValueYears().getFirst().year()
              : null);

      List<AccountMonthlyPerformanceEntity> monthlyRows =
          accountMonthlyPerformanceRepository
              .findByMonthGreaterThanEqualOrderByMonthAscAccountIdAsc(historyStart.atDay(1))
              .stream()
              .filter(row -> row.getMonth() != null)
              .filter(row -> eligibleAccounts.contains(row.getAccountId()))
              .filter(row -> !YearMonth.from(row.getMonth()).isBefore(historyStart))
              .toList();
      if (monthlyRows.isEmpty()) return benchmark;
      List<AccountMonthlyPerformanceEntity> selectedRows =
          monthlyRows.stream()
              .filter(row -> selectedAccounts.contains(row.getAccountId()))
              .toList();
      if (selectedRows.isEmpty()) return benchmark;

      YearMonth end =
          monthlyRows.stream()
              .map(AccountMonthlyPerformanceEntity::getMonth)
              .map(YearMonth::from)
              .max(Comparator.naturalOrder())
              .orElse(historyStart);
      if (end.isBefore(historyStart)) end = historyStart;
      List<String> labels = new ArrayList<>();
      for (YearMonth month = historyStart; !month.isAfter(end); month = month.plusMonths(1)) {
        labels.add(month.toString());
      }

      NavigableMap<String, Double> closes =
          marketDataService.monthlyCloses(requiredCloseLabels(labels));
      Double spyBase = benchmarkBaseClose(labels, closes);
      List<Double> portfolioReturnCurve = canonicalReturnCurve(selectedRows, labels);
      List<Double> benchmarkReturnValues =
          spyBase == null || spyBase == 0.0
              ? labels.stream().map(ignored -> (Double) null).toList()
              : benchmarkReturnCurve(labels, closes, spyBase);
      List<Benchmark.AccountSeries> accountSeries =
          monthlyRows.stream()
              .collect(Collectors.groupingBy(AccountMonthlyPerformanceEntity::getAccountId))
              .entrySet()
              .stream()
              .map(entry -> accountSeries(entry.getKey(), entry.getValue(), labels, closes))
              .filter(series -> series.investedCapital() != 0.0)
              .sorted(Comparator.comparing(Benchmark.AccountSeries::id))
              .toList();
      benchmark.setAccountSeries(accountSeries);
      List<Benchmark.AccountSeries> selectedSeries =
          accountSeries.stream().filter(series -> selectedAccounts.contains(series.id())).toList();
      if (selectedSeries.isEmpty()) return benchmark;

      List<Double> portfolioCurve = new ArrayList<>();
      List<Double> benchmarkCurve = new ArrayList<>();
      for (int index = 0; index < labels.size(); index++) {
        int point = index;
        portfolioCurve.add(
            round(selectedSeries.stream().mapToDouble(s -> s.portfolioCurve().get(point)).sum()));
        List<Double> values =
            selectedSeries.stream()
                .map(series -> series.benchmarkCurve().get(point))
                .filter(Objects::nonNull)
                .toList();
        benchmarkCurve.add(
            values.isEmpty()
                ? null
                : round(values.stream().mapToDouble(Double::doubleValue).sum()));
      }
      double investedCapital =
          selectedSeries.stream().mapToDouble(Benchmark.AccountSeries::investedCapital).sum();
      if (investedCapital == 0.0) return benchmark;

      benchmark.setLabels(labels);
      benchmark.setPortfolioCurve(portfolioCurve);
      benchmark.setBenchmarkCurve(benchmarkCurve);
      benchmark.setPortfolioReturnCurve(portfolioReturnCurve);
      benchmark.setBenchmarkReturnCurve(benchmarkReturnValues);
      benchmark.setInvestedCapital(round(investedCapital));
      benchmark.setPortfolioPl(portfolioCurve.getLast());
      benchmark.setBenchmarkPl(nz(benchmarkCurve.getLast()));
      benchmark.setPortfolioReturnPct(last(portfolioReturnCurve));
      benchmark.setBenchmarkReturnPct(last(benchmarkReturnValues));
      benchmark.setPortfolioPerformanceAvailable(true);
      benchmark.setBenchmarkAvailable(benchmarkReturnValues.stream().anyMatch(Objects::nonNull));
      benchmark.setAlpha(
          benchmark.isBenchmarkAvailable()
              ? round(benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct())
              : 0.0);
      benchmark.setAvailable(true);
      return benchmark;
    } catch (DataAccessException failure) {
      log.error(
          "Benchmark calculation failed while reading persisted portfolio or SPY data", failure);
      throw failure;
    }
  }

  private Benchmark.AccountSeries accountSeries(
      Long accountId,
      List<AccountMonthlyPerformanceEntity> rows,
      List<String> labels,
      NavigableMap<String, Double> closes) {
    Map<String, AccountMonthlyPerformanceEntity> monthlyRows =
        rows.stream()
            .filter(row -> row.getMonth() != null)
            .collect(
                Collectors.toMap(
                    row -> YearMonth.from(row.getMonth()).toString(),
                    row -> row,
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    List<Double> portfolioCurve = new ArrayList<>();
    List<Double> benchmarkCurve = new ArrayList<>();
    List<Double> returnCapitalCurve = new ArrayList<>();
    List<Double> returnContributionCurve = new ArrayList<>();
    List<Double> returnPctCurve = new ArrayList<>();
    Optional<String> firstValueLabel =
        labels.stream()
            .filter(
                label ->
                    monthlyRows.containsKey(label)
                        && Math.abs(nz(monthlyRows.get(label).getStartEquity()))
                            > ACTIVE_ACCOUNT_MIN_VALUE)
            .findFirst();
    if (firstValueLabel.isEmpty()) {
      return new Benchmark.AccountSeries(
          accountId,
          0.0,
          0.0,
          0.0,
          portfolioCurve,
          benchmarkCurve,
          returnCapitalCurve,
          returnContributionCurve,
          returnPctCurve);
    }
    String startLabel = firstValueLabel.get();
    double basePortfolioValue = nz(monthlyRows.get(startLabel).getStartEquity());
    Double baseClose =
        benchmarkBaseClose(labels.subList(labels.indexOf(startLabel), labels.size()), closes);
    boolean benchmarkAvailable = baseClose != null && baseClose != 0.0;
    double cumulativeProfit = 0.0;
    boolean started = false;
    for (String label : labels) {
      if (!started && !label.equals(startLabel)) {
        portfolioCurve.add(0.0);
        benchmarkCurve.add(0.0);
        returnCapitalCurve.add(null);
        returnContributionCurve.add(null);
        returnPctCurve.add(null);
        continue;
      }
      started = true;
      AccountMonthlyPerformanceEntity row = monthlyRows.get(label);
      if (row != null) cumulativeProfit += nz(row.getProfit());
      double openingCapital = nz(row == null ? null : row.getStartEquity());
      Double monthlyReturn = row == null ? null : toDouble(row.getReturnPct());
      if (openingCapital == 0.0 || monthlyReturn == null) {
        returnCapitalCurve.add(null);
        returnContributionCurve.add(null);
        returnPctCurve.add(null);
      } else {
        returnCapitalCurve.add(round(openingCapital));
        returnContributionCurve.add(round(openingCapital * monthlyReturn));
        returnPctCurve.add(round(monthlyReturn * 100.0));
      }
      Double close = closes.get(label);
      Double benchmarkProfit =
          benchmarkAvailable && close != null && close != 0.0
              ? basePortfolioValue * (close / baseClose - 1.0)
              : null;
      portfolioCurve.add(round(cumulativeProfit));
      benchmarkCurve.add(benchmarkProfit == null ? null : round(benchmarkProfit));
    }
    return new Benchmark.AccountSeries(
        accountId,
        round(basePortfolioValue),
        portfolioCurve.isEmpty() ? 0.0 : portfolioCurve.getLast(),
        benchmarkCurve.isEmpty() || benchmarkCurve.getLast() == null
            ? 0.0
            : benchmarkCurve.getLast(),
        portfolioCurve,
        benchmarkCurve,
        returnCapitalCurve,
        returnContributionCurve,
        returnPctCurve);
  }

  private static List<String> requiredCloseLabels(List<String> labels) {
    if (labels.isEmpty()) return labels;
    List<String> required = new ArrayList<>();
    required.add(YearMonth.parse(labels.getFirst()).minusMonths(1).toString());
    required.addAll(labels);
    return required;
  }

  private static Double benchmarkBaseClose(
      List<String> labels, NavigableMap<String, Double> closes) {
    if (labels.isEmpty()) return null;
    Double prior = closes.get(YearMonth.parse(labels.getFirst()).minusMonths(1).toString());
    if (prior != null && prior != 0.0) return prior;
    return labels.stream()
        .map(closes::get)
        .filter(close -> close != null && close != 0.0)
        .findFirst()
        .orElse(null);
  }

  private static List<Double> canonicalReturnCurve(
      List<AccountMonthlyPerformanceEntity> rows, List<String> labels) {
    List<Double> curve = new ArrayList<>();
    double factor = 1.0;
    boolean started = false;
    boolean complete = true;
    for (String label : labels) {
      List<AccountMonthlyPerformanceEntity> monthRows =
          rows.stream()
              .filter(row -> label.equals(YearMonth.from(row.getMonth()).toString()))
              .toList();
      double capital = monthRows.stream().mapToDouble(row -> nz(row.getStartEquity())).sum();
      if (monthRows.isEmpty()) {
        if (started) complete = false;
        curve.add(null);
      } else if (monthRows.stream().anyMatch(row -> row.getReturnPct() == null)) {
        if (capital != 0.0) started = true;
        if (started) complete = false;
        curve.add(null);
      } else if (capital == 0.0 || !complete) {
        curve.add(null);
      } else {
        started = true;
        double monthReturn =
            monthRows.stream()
                    .mapToDouble(row -> nz(row.getStartEquity()) * nz(row.getReturnPct()))
                    .sum()
                / capital;
        factor = monthReturn <= -1.0 ? 0.0 : factor * (1.0 + monthReturn);
        curve.add(round((factor - 1.0) * 100.0));
      }
    }
    return curve;
  }

  private static List<Double> benchmarkReturnCurve(
      List<String> labels, NavigableMap<String, Double> closes, double baseClose) {
    return labels.stream()
        .map(
            label -> {
              Double close = closes.get(label);
              return close == null || baseClose == 0.0
                  ? null
                  : round((close / baseClose - 1.0) * 100.0);
            })
        .toList();
  }

  private static double last(List<Double> values) {
    return values.isEmpty() || values.getLast() == null ? 0.0 : values.getLast();
  }

  private static YearMonth parseHistoryStart(String value) {
    return value.length() == 7 ? YearMonth.parse(value) : YearMonth.from(LocalDate.parse(value));
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static double nz(BigDecimal value) {
    return value == null ? 0.0 : value.doubleValue();
  }

  private static Double toDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
