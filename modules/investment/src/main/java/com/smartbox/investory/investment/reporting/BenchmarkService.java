package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.accounting.model.Benchmark;
import com.smartbox.investory.investment.infrastructure.market.client.TwelveDataService;
import com.smartbox.investory.investment.infrastructure.persistence.NormalizedCashOperationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseEntity;
import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseRepository;
import com.smartbox.investory.investment.market.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
public class BenchmarkService {

  private static final String BENCHMARK_SYMBOL = "SPY";
  private static final int FETCH_MONTHS = 120;
  private static final double ACTIVE_ACCOUNT_MIN_VALUE = 50.0;
  private static final CurrencyType BASE_CURRENCY = CurrencyType.USD;
  private static final Set<String> NET_DEPOSIT_CATEGORIES =
      Set.of(
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
  private final CurrencyRateService currencyRateService;

  /**
   * Earliest month included in the comparison curve. Earlier periods had small balances and no
   * consistent strategy, so they're excluded from the benchmark by default. Configurable via {@code
   * app.benchmark.comparison-start} as {@code yyyy-MM}.
   */
  private final YearMonth historyStart;

  private LocalDate fetchAttemptedOn;

  public BenchmarkService(
      AccountDailyRepository accountDailyRepository,
      AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository,
      AccountRepository accountRepository,
      AccountStatisticsRepository accountStatisticsRepository,
      NormalizedCashOperationRepository normalizedCashOperationRepository,
      BenchmarkMonthlyCloseRepository benchmarkMonthlyCloseRepository,
      TwelveDataService twelveDataService,
      CurrencyRateService currencyRateService,
      @Value("${app.history-start:2025-01-01}") String historyStart) {
    this.accountDailyRepository = accountDailyRepository;
    this.accountMonthlyPerformanceRepository = accountMonthlyPerformanceRepository;
    this.accountRepository = accountRepository;
    this.accountStatisticsRepository = accountStatisticsRepository;
    this.normalizedCashOperationRepository = normalizedCashOperationRepository;
    this.benchmarkMonthlyCloseRepository = benchmarkMonthlyCloseRepository;
    this.twelveDataService = twelveDataService;
    this.currencyRateService = currencyRateService;
    this.historyStart = parseHistoryStart(historyStart);
  }

  @Cacheable(cacheNames = "benchmark", key = "'all'")
  public Benchmark calculate() {
    return calculate(null);
  }

  @Cacheable(cacheNames = "benchmark", keyGenerator = "investmentCalculationKeyGenerator")
  public Benchmark calculate(Collection<Long> accountIds) {
    Benchmark benchmark = new Benchmark();
    try {
      List<AccountDailyEntity> allRows = accountDailyRepository.findAll();
      Set<Long> requestedAccounts = accountIds == null ? Set.of() : new HashSet<>(accountIds);
      boolean filterSubmitted = accountIds != null;
      Set<Long> benchmarkEligibleAccounts =
          activeAccountIds(allRows, accountStatisticsRepository.findAll());
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
      benchmarkEligibleAccounts.removeAll(cashOnlyAccounts);
      Set<Long> performanceAccounts = new TreeSet<>(allPortfolioAccounts);
      performanceAccounts.removeAll(cashOnlyAccounts);
      Set<Long> selectedAccounts =
          !filterSubmitted
              ? benchmarkEligibleAccounts
              : requestedAccounts.stream()
                  .filter(benchmarkEligibleAccounts::contains)
                  .collect(Collectors.toCollection(TreeSet::new));
      Map<Long, AccountEntity> accountsById = accountRepository.findMapByIdIn(performanceAccounts);
      benchmark.setAccountOptions(
          accountOptions(
              accountsById,
              benchmarkEligibleAccounts,
              filterSubmitted ? selectedAccounts : benchmarkEligibleAccounts));
      Set<Long> accountValueAccounts = filterSubmitted ? selectedAccounts : performanceAccounts;
      benchmark.setAccountValueYears(
          accountValueYears(allRows, accountValueAccounts, accountsById));
      benchmark.setAccountValuesAvailable(!benchmark.getAccountValueYears().isEmpty());
      benchmark.setSelectedAccountValueYear(
          benchmark.isAccountValuesAvailable()
              ? benchmark.getAccountValueYears().getFirst().year()
              : null);

      List<AccountMonthlyPerformanceEntity> monthlyRows =
          accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc().stream()
              .filter(row -> row.getMonth() != null)
              .filter(row -> benchmarkEligibleAccounts.contains(row.getAccountId()))
              .filter(row -> !YearMonth.from(row.getMonth()).isBefore(historyStart))
              .toList();
      if (monthlyRows.isEmpty()) {
        return benchmark;
      }

      List<AccountMonthlyPerformanceEntity> selectedRows =
          monthlyRows.stream()
              .filter(row -> selectedAccounts.contains(row.getAccountId()))
              .toList();
      if (selectedRows.isEmpty()) {
        return benchmark;
      }

      YearMonth start = historyStart;
      YearMonth end =
          monthlyRows.stream()
              .map(AccountMonthlyPerformanceEntity::getMonth)
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
      if (selectedSeries.isEmpty()) {
        return benchmark;
      }

      for (int i = 0; i < labels.size(); i++) {
        int index = i;
        portfolioCurve.add(
            round(
                selectedSeries.stream()
                    .mapToDouble(series -> series.portfolioCurve().get(index))
                    .sum()));
        List<Double> benchmarkValues =
            selectedSeries.stream()
                .map(series -> series.benchmarkCurve().get(index))
                .filter(Objects::nonNull)
                .toList();
        benchmarkCurve.add(
            benchmarkValues.isEmpty()
                ? null
                : round(benchmarkValues.stream().mapToDouble(Double::doubleValue).sum()));
      }

      double investedCapital =
          selectedSeries.stream().mapToDouble(Benchmark.AccountSeries::investedCapital).sum();
      double portfolioPl = portfolioCurve.getLast();
      double benchmarkPl = nz(benchmarkCurve.getLast());
      if (investedCapital == 0.0) {
        return benchmark;
      }

      benchmark.setLabels(labels);
      benchmark.setPortfolioCurve(portfolioCurve);
      benchmark.setBenchmarkCurve(benchmarkCurve);
      benchmark.setPortfolioReturnCurve(portfolioReturnCurve);
      benchmark.setBenchmarkReturnCurve(benchmarkReturnValues);
      benchmark.setInvestedCapital(round(investedCapital));
      benchmark.setPortfolioPl(portfolioPl);
      benchmark.setBenchmarkPl(benchmarkPl);
      benchmark.setPortfolioReturnPct(last(portfolioReturnCurve));
      benchmark.setBenchmarkReturnPct(last(benchmarkReturnValues));
      benchmark.setPortfolioPerformanceAvailable(true);
      benchmark.setBenchmarkAvailable(benchmarkReturnValues.stream().anyMatch(Objects::nonNull));
      benchmark.setAlpha(
          benchmark.isBenchmarkAvailable()
              ? round(benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct())
              : 0.0);
      benchmark.setAvailable(true);
    } catch (Exception e) {
      log.error("Benchmark calculation failed: {}", e.getMessage(), e);
      benchmark.setAvailable(false);
    }
    return benchmark;
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
                    (first, ignored) -> first));
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
      if (!started) {
        if (!label.equals(startLabel)) {
          portfolioCurve.add(0.0);
          benchmarkCurve.add(0.0);
          returnCapitalCurve.add(null);
          returnContributionCurve.add(null);
          returnPctCurve.add(null);
          continue;
        }
        started = true;
      }

      AccountMonthlyPerformanceEntity row = monthlyRows.get(label);
      if (row != null) {
        cumulativeProfit += nz(row.getProfit());
      }

      Double close = exactCloseFor(closes, label);

      double openingCapital = nz(row == null ? null : row.getStartEquity());
      Double monthlyReturn = row == null ? null : row.getReturnPct();
      if (openingCapital == 0.0 || monthlyReturn == null) {
        returnCapitalCurve.add(null);
        returnContributionCurve.add(null);
        returnPctCurve.add(null);
      } else {
        returnCapitalCurve.add(round(openingCapital));
        returnContributionCurve.add(round(openingCapital * monthlyReturn));
        returnPctCurve.add(round(monthlyReturn * 100.0));
      }
      Double benchmarkPl =
          benchmarkAvailable && close != null && close != 0.0
              ? basePortfolioValue * (close / baseClose - 1.0)
              : null;

      portfolioCurve.add(round(cumulativeProfit));
      benchmarkCurve.add(benchmarkPl == null ? null : round(benchmarkPl));
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

  private List<String> requiredCloseLabels(List<String> labels) {
    if (labels.isEmpty()) {
      return labels;
    }
    List<String> required = new ArrayList<>();
    required.add(YearMonth.parse(labels.getFirst()).minusMonths(1).toString());
    required.addAll(labels);
    return required;
  }

  /**
   * Prefer the close immediately before the selected range. If that predecessor was not retained,
   * use the first in-range SPY close as a zero-return baseline instead of suppressing SPY.
   */
  private Double benchmarkBaseClose(List<String> labels, NavigableMap<String, Double> closes) {
    if (labels.isEmpty()) {
      return null;
    }
    Double prior =
        exactCloseFor(closes, YearMonth.parse(labels.getFirst()).minusMonths(1).toString());
    if (prior != null && prior != 0.0) {
      return prior;
    }
    return labels.stream()
        .map(label -> exactCloseFor(closes, label))
        .filter(close -> close != null && close != 0.0)
        .findFirst()
        .orElse(null);
  }

  private Set<Long> activeAccountIds(
      List<AccountDailyEntity> rows, List<AccountStatisticsEntity> statistics) {
    if (!CollectionUtils.isEmpty(statistics)) {
      return statistics.stream()
          .filter(stat -> stat.getAccountId() != null)
          .filter(this::hasAccountValueSurface)
          .map(AccountStatisticsEntity::getAccountId)
          .collect(Collectors.toCollection(TreeSet::new));
    }

    Map<Long, AccountDailyEntity> latestByAccount = new HashMap<>();
    for (AccountDailyEntity row : rows) {
      if (row.getAccountId() == null || row.getDate() == null) {
        continue;
      }
      AccountDailyEntity current = latestByAccount.get(row.getAccountId());
      if (current == null || row.getDate().isAfter(current.getDate())) {
        latestByAccount.put(row.getAccountId(), row);
      }
    }
    return latestByAccount.values().stream()
        .filter(row -> nz(row.getEquity()) > ACTIVE_ACCOUNT_MIN_VALUE)
        .map(AccountDailyEntity::getAccountId)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private boolean hasAccountValueSurface(AccountStatisticsEntity stat) {
    return Math.abs(nz(stat.getCashBalance()) + nz(stat.getMarketValue()))
            > ACTIVE_ACCOUNT_MIN_VALUE
        || Math.abs(nz(stat.getNetDeposit())) > ACTIVE_ACCOUNT_MIN_VALUE;
  }

  private List<Benchmark.AccountOption> accountOptions(
      Map<Long, AccountEntity> accountsById,
      Set<Long> visibleAccounts,
      Set<Long> selectedAccounts) {
    return accountsById.values().stream()
        .filter(account -> visibleAccounts.contains(account.getId()))
        .map(
            account ->
                new Benchmark.AccountOption(
                    account.getId(), account.getName(), selectedAccounts.contains(account.getId())))
        .toList();
  }

  private List<Benchmark.AccountValueYear> accountValueYears(
      List<AccountDailyEntity> dailyRows,
      Set<Long> availableAccounts,
      Map<Long, AccountEntity> accountsById) {
    if (CollectionUtils.isEmpty(dailyRows)
        || CollectionUtils.isEmpty(availableAccounts)
        || CollectionUtils.isEmpty(accountsById)) {
      return List.of();
    }

    NavigableMap<Integer, List<AccountDailyEntity>> rowsByYear =
        dailyRows.stream()
            .filter(row -> row.getDate() != null)
            .filter(row -> availableAccounts.contains(row.getAccountId()))
            .collect(
                Collectors.groupingBy(
                    row -> row.getDate().getYear(), TreeMap::new, Collectors.toList()));

    List<Benchmark.AccountValueYear> years = new ArrayList<>();
    rowsByYear
        .descendingMap()
        .forEach(
            (year, rows) -> {
              List<String> labels = dailyLabels(rows);
              List<Benchmark.AccountValueSeries> accountSeries =
                  accountsById.values().stream()
                      .filter(account -> availableAccounts.contains(account.getId()))
                      .map(account -> dailyAccountValueSeries(account, rows, labels, BASE_CURRENCY))
                      .toList();
              if (!accountSeries.isEmpty()) {
                Benchmark.AccountValueSeries totalSeries =
                    sumAccountValueSeries(rows, availableAccounts, labels, accountSeries);
                years.add(
                    new Benchmark.AccountValueYear(
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
      AccountEntity account,
      List<AccountDailyEntity> rows,
      List<String> labels,
      CurrencyType baseCurrency) {
    Map<String, AccountDailyEntity> valuesByDay =
        rows.stream()
            .filter(row -> account.getId().equals(row.getAccountId()))
            .collect(
                Collectors.groupingBy(
                    row -> row.getDate().toString(),
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        dayRows ->
                            dayRows.stream()
                                .max(Comparator.comparing(AccountDailyEntity::getDate))
                                .orElse(null))));
    List<Double> profitValues = new ArrayList<>();
    List<Double> profitPctValues = new ArrayList<>();
    double cumulativeProfit = 0.0;
    double cumulativeReturnFactor = 1.0;
    boolean returnComplete = true;
    for (String label : labels) {
      AccountDailyEntity point = valuesByDay.get(label);
      if (point != null) {
        cumulativeProfit += dailyProfitInBaseCurrency(point, baseCurrency);
        Double dailyReturn = point.getDailyReturn();
        if (returnComplete && dailyReturn != null && dailyReturn >= -1.0) {
          cumulativeReturnFactor *= 1.0 + dailyReturn;
        } else if (dailyReturn == null || dailyReturn < -1.0) {
          returnComplete = false;
        }
      }
      profitValues.add(round(cumulativeProfit));
      profitPctValues.add(returnComplete ? round((cumulativeReturnFactor - 1.0) * 100.0) : null);
    }
    return new Benchmark.AccountValueSeries(
        account.getId(), account.getName(), profitValues, profitPctValues);
  }

  private double dailyProfitInBaseCurrency(AccountDailyEntity row, CurrencyType baseCurrency) {
    double profit = nz(row.getDailyProfitAmount());
    if (profit == 0.0 || row.getValuationCurrency() == null || baseCurrency == null) {
      return profit;
    }
    CurrencyType sourceCurrency = CurrencyType.valueOf(row.getValuationCurrency());
    return sourceCurrency == baseCurrency
        ? profit
        : currencyRateService.convertToBaseCurrency(
            profit, baseCurrency, sourceCurrency, row.getDate());
  }

  private List<Double> canonicalReturnCurve(
      List<AccountMonthlyPerformanceEntity> rows, List<String> labels) {
    List<Double> curve = new ArrayList<>();
    double factor = 1.0;
    boolean returnStarted = false;
    boolean returnComplete = true;
    for (String label : labels) {
      List<AccountMonthlyPerformanceEntity> monthRows =
          rows.stream()
              .filter(row -> label.equals(YearMonth.from(row.getMonth()).toString()))
              .toList();
      double capital = monthRows.stream().mapToDouble(row -> nz(row.getStartEquity())).sum();
      if (monthRows.isEmpty()) {
        if (returnStarted) returnComplete = false;
        curve.add(null);
        continue;
      }
      if (monthRows.stream().anyMatch(row -> row.getReturnPct() == null)) {
        if (capital != 0.0) returnStarted = true;
        if (returnStarted) returnComplete = false;
        curve.add(null);
        continue;
      }
      if (capital == 0.0) {
        curve.add(null);
        continue;
      }
      if (!returnComplete) {
        curve.add(null);
        continue;
      }
      returnStarted = true;
      double monthReturn =
          monthRows.stream()
                  .mapToDouble(row -> nz(row.getStartEquity()) * nz(row.getReturnPct()))
                  .sum()
              / capital;
      if (monthReturn <= -1.0) {
        factor = 0.0;
      } else {
        factor *= 1.0 + monthReturn;
      }
      curve.add(round((factor - 1.0) * 100.0));
    }
    return curve;
  }

  private List<Double> benchmarkReturnCurve(
      List<String> labels, NavigableMap<String, Double> closes, double baseClose) {
    return labels.stream()
        .map(
            label -> {
              Double close = exactCloseFor(closes, label);
              return close == null || baseClose == 0.0
                  ? null
                  : round((close / baseClose - 1.0) * 100.0);
            })
        .toList();
  }

  private double last(List<Double> values) {
    return values.isEmpty() || values.getLast() == null ? 0.0 : values.getLast();
  }

  private Benchmark.AccountValueSeries sumAccountValueSeries(
      List<AccountDailyEntity> rows,
      Set<Long> accountIds,
      List<String> labels,
      List<Benchmark.AccountValueSeries> accountSeries) {
    int size = labels.size();
    List<Double> profitValues = new ArrayList<>();
    List<Double> profitPctValues = new ArrayList<>();
    double cumulativeProfit = 0.0;
    double cumulativeReturnFactor = 1.0;
    boolean returnComplete = true;
    for (int index = 0; index < size; index++) {
      String label = labels.get(index);
      List<AccountDailyEntity> dailyRows =
          rows.stream().filter(row -> label.equals(row.getDate().toString())).toList();
      int valueIndex = index;
      cumulativeProfit =
          accountSeries.stream().mapToDouble(series -> series.profitValues().get(valueIndex)).sum();
      double capital =
          dailyRows.stream()
              .filter(row -> accountIds.contains(row.getAccountId()))
              .mapToDouble(row -> nz(row.getEquity()))
              .sum();
      if (capital != 0.0
          && dailyRows.stream()
              .filter(row -> accountIds.contains(row.getAccountId()))
              .allMatch(row -> row.getDailyReturn() != null)) {
        double dailyReturn =
            dailyRows.stream()
                    .filter(row -> accountIds.contains(row.getAccountId()))
                    .mapToDouble(row -> nz(row.getEquity()) * nz(row.getDailyReturn()))
                    .sum()
                / capital;
        cumulativeReturnFactor *= dailyReturn <= -1.0 ? 0.0 : 1.0 + dailyReturn;
      } else {
        returnComplete = false;
      }
      profitValues.add(round(cumulativeProfit));
      profitPctValues.add(returnComplete ? round((cumulativeReturnFactor - 1.0) * 100.0) : null);
    }
    return new Benchmark.AccountValueSeries(0L, "Portfolio total", profitValues, profitPctValues);
  }

  private Map<Long, Map<String, Double>> normalizedNetDepositByDay(Set<Long> accountIds) {
    if (CollectionUtils.isEmpty(accountIds)) {
      return Map.of();
    }
    return normalizedCashOperationRepository.findAllByAccountIdIn(accountIds).stream()
        .filter(row -> NET_DEPOSIT_CATEGORIES.contains(row.getNormalizedCategory()))
        .collect(
            Collectors.groupingBy(
                NormalizedCashOperationRepository.NormalizedCashOperationRow::getAccountId,
                Collectors.groupingBy(
                    row -> row.getDate().toString(),
                    Collectors.summingDouble(row -> nz(row.getAmountInBaseCurrency())))));
  }

  private List<String> dailyLabels(List<AccountDailyEntity> rows) {
    List<LocalDate> dates =
        rows.stream().map(AccountDailyEntity::getDate).filter(Objects::nonNull).sorted().toList();
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
      NavigableMap<String, Double> fetched =
          twelveDataService.fetchMonthlyCloses(BENCHMARK_SYMBOL, FETCH_MONTHS);
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
    for (BenchmarkMonthlyCloseEntity row :
        benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc(BENCHMARK_SYMBOL)) {
      if (row.getMonthDate() != null && row.getClosePrice() != null) {
        closes.put(
            YearMonth.from(row.getMonthDate()).toString(), row.getClosePrice().doubleValue());
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
    Map<String, BenchmarkMonthlyCloseEntity> existing =
        benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc(BENCHMARK_SYMBOL).stream()
            .filter(row -> row.getMonthDate() != null)
            .collect(
                Collectors.toMap(
                    row -> YearMonth.from(row.getMonthDate()).toString(),
                    row -> row,
                    (first, ignored) -> first,
                    TreeMap::new));

    ZonedDateTime now = ZonedDateTime.now();
    List<BenchmarkMonthlyCloseEntity> rows = new ArrayList<>();
    fetched.forEach(
        (month, close) -> {
          if (close == null || close == 0.0) {
            return;
          }
          BenchmarkMonthlyCloseEntity row = existing.get(month);
          if (row == null) {
            row =
                BenchmarkMonthlyCloseEntity.builder()
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

  private YearMonth parseHistoryStart(String value) {
    return value.length() == 7 ? YearMonth.parse(value) : YearMonth.from(LocalDate.parse(value));
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
