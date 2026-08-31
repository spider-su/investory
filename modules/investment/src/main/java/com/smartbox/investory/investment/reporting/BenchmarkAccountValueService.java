package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.performance.model.Benchmark;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** Builds account-selection and daily account-value series for benchmark presentation. */
@Service
class BenchmarkAccountValueService {

  private static final double ACTIVE_ACCOUNT_MIN_VALUE = 50.0;
  private static final CurrencyType BASE_CURRENCY = CurrencyType.USD;

  private final CurrencyRateService currencyRateService;

  BenchmarkAccountValueService(CurrencyRateService currencyRateService) {
    this.currencyRateService = currencyRateService;
  }

  Set<Long> activeAccountIds(
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
      if (row.getAccountId() == null || row.getDate() == null) continue;
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

  List<Benchmark.AccountOption> accountOptions(
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

  List<Benchmark.AccountValueYear> accountValueYears(
      List<AccountDailyEntity> dailyRows,
      Set<Long> availableAccounts,
      Map<Long, AccountEntity> accountsById) {
    if (CollectionUtils.isEmpty(dailyRows)
        || CollectionUtils.isEmpty(availableAccounts)
        || CollectionUtils.isEmpty(accountsById)) return List.of();
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
        .forEach((year, rows) -> addYear(years, year, rows, availableAccounts, accountsById));
    return years;
  }

  private void addYear(
      List<Benchmark.AccountValueYear> years,
      int year,
      List<AccountDailyEntity> rows,
      Set<Long> accountIds,
      Map<Long, AccountEntity> accountsById) {
    List<String> labels = dailyLabels(rows);
    List<Benchmark.AccountValueSeries> series =
        accountsById.values().stream()
            .filter(account -> accountIds.contains(account.getId()))
            .map(account -> dailyAccountValueSeries(account, rows, labels))
            .toList();
    if (!series.isEmpty()) {
      Benchmark.AccountValueSeries total = sumAccountValueSeries(rows, accountIds, labels, series);
      years.add(
          new Benchmark.AccountValueYear(
              year, labels, series, total.profitValues(), total.profitPctValues()));
    }
  }

  private Benchmark.AccountValueSeries dailyAccountValueSeries(
      AccountEntity account, List<AccountDailyEntity> rows, List<String> labels) {
    Map<String, AccountDailyEntity> valuesByDay =
        rows.stream()
            .filter(row -> account.getId().equals(row.getAccountId()))
            .collect(Collectors.toMap(row -> row.getDate().toString(), row -> row, (a, b) -> b));
    List<Double> profitValues = new ArrayList<>();
    List<Double> returnValues = new ArrayList<>();
    double cumulativeProfit = 0.0;
    double factor = 1.0;
    boolean complete = true;
    for (String label : labels) {
      AccountDailyEntity point = valuesByDay.get(label);
      if (point != null) {
        cumulativeProfit += dailyProfitInBaseCurrency(point);
        Double dailyReturn = point.getDailyReturn();
        if (complete && dailyReturn != null && dailyReturn >= -1.0) factor *= 1.0 + dailyReturn;
        else if (dailyReturn == null || dailyReturn < -1.0) complete = false;
      }
      profitValues.add(round(cumulativeProfit));
      returnValues.add(complete ? round((factor - 1.0) * 100.0) : null);
    }
    return new Benchmark.AccountValueSeries(
        account.getId(), account.getName(), profitValues, returnValues);
  }

  private Benchmark.AccountValueSeries sumAccountValueSeries(
      List<AccountDailyEntity> rows,
      Set<Long> accountIds,
      List<String> labels,
      List<Benchmark.AccountValueSeries> accountSeries) {
    List<Double> profitValues = new ArrayList<>();
    List<Double> returnValues = new ArrayList<>();
    double factor = 1.0;
    boolean complete = true;
    for (int index = 0; index < labels.size(); index++) {
      String label = labels.get(index);
      List<AccountDailyEntity> dailyRows =
          rows.stream()
              .filter(row -> accountIds.contains(row.getAccountId()))
              .filter(row -> label.equals(row.getDate().toString()))
              .toList();
      int valueIndex = index;
      double profit =
          accountSeries.stream().mapToDouble(s -> s.profitValues().get(valueIndex)).sum();
      double capital = dailyRows.stream().mapToDouble(row -> nz(row.getEquity())).sum();
      if (capital != 0.0 && dailyRows.stream().allMatch(row -> row.getDailyReturn() != null)) {
        double dailyReturn =
            dailyRows.stream()
                    .mapToDouble(row -> nz(row.getEquity()) * nz(row.getDailyReturn()))
                    .sum()
                / capital;
        factor *= dailyReturn <= -1.0 ? 0.0 : 1.0 + dailyReturn;
      } else complete = false;
      profitValues.add(round(profit));
      returnValues.add(complete ? round((factor - 1.0) * 100.0) : null);
    }
    return new Benchmark.AccountValueSeries(0L, "Portfolio total", profitValues, returnValues);
  }

  private double dailyProfitInBaseCurrency(AccountDailyEntity row) {
    double profit = nz(row.getDailyProfitAmount());
    if (profit == 0.0 || row.getValuationCurrency() == null) return profit;
    CurrencyType source = CurrencyType.valueOf(row.getValuationCurrency());
    return source == BASE_CURRENCY
        ? profit
        : currencyRateService.convertToBaseCurrency(profit, BASE_CURRENCY, source, row.getDate());
  }

  private boolean hasAccountValueSurface(AccountStatisticsEntity stat) {
    return Math.abs(nz(stat.getCashBalance()) + nz(stat.getMarketValue()))
            > ACTIVE_ACCOUNT_MIN_VALUE
        || Math.abs(nz(stat.getNetDeposit())) > ACTIVE_ACCOUNT_MIN_VALUE;
  }

  private static List<String> dailyLabels(List<AccountDailyEntity> rows) {
    List<LocalDate> dates =
        rows.stream().map(AccountDailyEntity::getDate).filter(Objects::nonNull).sorted().toList();
    if (dates.isEmpty()) return List.of();
    List<String> labels = new ArrayList<>();
    for (LocalDate day = dates.getFirst(); !day.isAfter(dates.getLast()); day = day.plusDays(1)) {
      labels.add(day.toString());
    }
    return labels;
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
