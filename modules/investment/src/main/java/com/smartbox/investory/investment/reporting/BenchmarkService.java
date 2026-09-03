package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.model.Benchmark;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
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
      @Value("${app.history-start:" + FinancialPolicyDefaults.HISTORY_START_TEXT + "}")
          String historyStart) {
    this.accountDailyRepository = accountDailyRepository;
    this.accountMonthlyPerformanceRepository = accountMonthlyPerformanceRepository;
    this.accountRepository = accountRepository;
    this.accountStatisticsRepository = accountStatisticsRepository;
    this.accountValueService = accountValueService;
    this.marketDataService = marketDataService;
    this.historyStart = parseHistoryStart(historyStart);
  }

  @Cacheable(cacheNames = "benchmark", keyGenerator = "investmentCalculationKeyGenerator")
  public Benchmark calculate(Long portfolioId, Collection<Long> accountIds) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    Benchmark benchmark = new Benchmark();
    try {
      Set<Long> portfolioAccounts =
          accountRepository.findAllByPortfolioId(portfolioId).stream()
              .map(AccountEntity::getId)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
      List<AccountDailyEntity> allRows =
          accountDailyRepository.findByDateGreaterThanEqualAndAccountIdInOrderByDateAscAccountIdAsc(
              historyStart.atDay(1), portfolioAccounts);
      Set<Long> requestedAccounts =
          com.smartbox.investory.shared.util.CollectionUtils.immutableSetOrEmpty(accountIds);
      boolean filterSubmitted = accountIds != null;
      Set<Long> eligibleAccounts =
          accountValueService.activeAccountIds(
              allRows, accountStatisticsRepository.findAllByAccountIdIn(portfolioAccounts));
      Set<Long> allPortfolioAccounts =
          allRows.stream()
              .map(AccountDailyEntity::getAccountId)
              .filter(Objects::nonNull)
              .collect(Collectors.toCollection(TreeSet::new));
      Set<Long> cashOnlyAccounts =
          accountRepository.findAllByPortfolioId(portfolioId).stream()
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
      BenchmarkMonthlyIndex monthlyIndex = BenchmarkMonthlyIndex.create(monthlyRows, labels);
      BenchmarkMonthlyIndex selectedMonthlyIndex =
          BenchmarkMonthlyIndex.create(selectedRows, labels);
      List<Double> portfolioReturnCurve =
          BenchmarkMonthlyReturnCalculator.portfolioReturnCurve(selectedMonthlyIndex);
      List<Double> benchmarkReturnValues =
          spyBase == null || spyBase == 0.0
              ? labels.stream().map(ignored -> (Double) null).toList()
              : benchmarkReturnCurve(labels, closes, spyBase);
      List<Benchmark.AccountSeries> accountSeries =
          monthlyIndex.rowsByAccount().keySet().stream()
              .map(
                  accountId ->
                      BenchmarkAccountSeriesCalculator.calculate(accountId, monthlyIndex, closes))
              .filter(series -> series.investedCapital() != 0.0)
              .sorted(Comparator.comparing(Benchmark.AccountSeries::id))
              .toList();
      benchmark.setAccountSeries(accountSeries);
      List<Benchmark.AccountSeries> selectedSeries =
          accountSeries.stream().filter(series -> selectedAccounts.contains(series.id())).toList();
      if (selectedSeries.isEmpty()) return benchmark;

      BenchmarkCurveAggregator.AggregatedCurves curves =
          BenchmarkCurveAggregator.aggregate(selectedSeries, labels.size());
      List<Double> portfolioCurve = curves.portfolio();
      List<Double> benchmarkCurve = curves.benchmark();
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

  private static List<String> requiredCloseLabels(List<String> labels) {
    if (labels.isEmpty()) return labels;
    List<String> required = new ArrayList<>();
    required.add(YearMonth.parse(labels.getFirst()).minusMonths(1).toString());
    required.addAll(labels);
    return required;
  }

  static Double benchmarkBaseClose(List<String> labels, NavigableMap<String, Double> closes) {
    if (labels.isEmpty()) return null;
    Double prior = closes.get(YearMonth.parse(labels.getFirst()).minusMonths(1).toString());
    if (prior != null && prior != 0.0) return prior;
    return labels.stream()
        .map(closes::get)
        .filter(close -> close != null && close != 0.0)
        .findFirst()
        .orElse(null);
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
