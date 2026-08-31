package com.smartbox.investory.services.portfolio.read;

import com.smartbox.investory.infrastructure.repository.account.AccountDaily;
import com.smartbox.investory.infrastructure.repository.account.AccountDailyRepository;
import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformance;
import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformanceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application query over the canonical SQL portfolio-monthly reporting projection. */
@Service
@Transactional(readOnly = true)
public class PortfolioPerformanceQuery {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final PortfolioMonthlyPerformanceRepository repository;
  private final AccountDailyRepository dailyRepository;

  @Autowired
  public PortfolioPerformanceQuery(
      PortfolioMonthlyPerformanceRepository repository, AccountDailyRepository dailyRepository) {
    this.repository = repository;
    this.dailyRepository = dailyRepository;
  }

  /** Returns the exact aggregate for the inclusive monthly range. */
  public PerformanceResult forMonths(YearMonth from, YearMonth to) {
    List<PortfolioMonthlyPerformance> rows =
        repository.findAllByOrderByMonthAscPortfolioIdAsc().stream()
            .filter(row -> from == null || !YearMonth.from(row.getMonth()).isBefore(from))
            .filter(row -> to == null || !YearMonth.from(row.getMonth()).isAfter(to))
            .toList();
    if (rows.isEmpty()) {
      return empty(from, to);
    }

    PortfolioMonthlyPerformance first = rows.getFirst();
    PortfolioMonthlyPerformance last = rows.getLast();
    BigDecimal contributions = sum(rows, PortfolioMonthlyPerformance::getDepositFlowDecimal);
    BigDecimal withdrawals = sum(rows, PortfolioMonthlyPerformance::getWithdrawalFlowDecimal);
    BigDecimal investmentResult = sum(rows, PortfolioMonthlyPerformance::getProfitDecimal);
    BigDecimal returnPercentage = last.getReturnPctDecimal();
    PerformanceResult result =
        new PerformanceResult(
            new PerformancePeriod(first.getFirstDate(), last.getEndDate()),
            first.getBaseCurrency(),
            first.getStartEquityDecimal(),
            last.getEndEquityDecimal(),
            contributions,
            withdrawals,
            contributions.subtract(withdrawals),
            investmentResult,
            sum(rows, PortfolioMonthlyPerformance::getRealizedProfitDecimal),
            null,
            sum(rows, PortfolioMonthlyPerformance::getDividendsDecimal),
            sum(rows, PortfolioMonthlyPerformance::getInterestDecimal),
            sum(rows, PortfolioMonthlyPerformance::getFeesDecimal),
            sum(rows, PortfolioMonthlyPerformance::getTaxesDecimal),
            returnPercentage,
            ReturnMetric.unavailable(
                ReturnMetric.Status.INSUFFICIENT_DATA,
                "Daily account valuations are not available"),
            ReturnMetric.unavailable(
                ReturnMetric.Status.INSUFFICIENT_DATA,
                "Daily account valuations are not available"),
            null);
    if (dailyRepository == null) {
      return new PerformanceResult(
          result.period(),
          result.baseCurrency(),
          result.startValue(),
          result.endValue(),
          result.contributions(),
          result.withdrawals(),
          result.netExternalFlows(),
          result.investmentResult(),
          result.realizedProfit(),
          result.unrealizedProfit(),
          result.dividends(),
          result.interest(),
          result.fees(),
          result.taxes(),
          result.returnPercentage(),
          result.timeWeightedReturn(),
          result.moneyWeightedReturn(),
          PerformanceAttributionCalculator.from(result));
    }
    List<DailyPortfolioValue> dailyValues = dailyValues(first.getFirstDate(), last.getEndDate());
    return new PerformanceResult(
        result.period(),
        result.baseCurrency(),
        result.startValue(),
        result.endValue(),
        result.contributions(),
        result.withdrawals(),
        result.netExternalFlows(),
        result.investmentResult(),
        result.realizedProfit(),
        result.unrealizedProfit(),
        result.dividends(),
        result.interest(),
        result.fees(),
        result.taxes(),
        result.returnPercentage(),
        PortfolioReturnCalculator.twr(result.startValue(), dailyValues),
        PortfolioReturnCalculator.xirr(
            first.getFirstDate(),
            result.startValue(),
            last.getEndDate(),
            result.endValue(),
            dailyValues),
        PerformanceAttributionCalculator.from(result));
  }

  private PerformanceResult empty(YearMonth from, YearMonth to) {
    LocalDate start = from == null ? null : from.atDay(1);
    LocalDate end = to == null ? null : to.atEndOfMonth();
    return new PerformanceResult(
        new PerformancePeriod(start, end),
        null,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        null,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        null,
        ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "No reporting rows"),
        ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "No reporting rows"),
        new PerformanceAttribution(
            ZERO, null, ZERO, ZERO, null, ZERO, ZERO, ZERO, ZERO, true, false));
  }

  private List<DailyPortfolioValue> dailyValues(LocalDate from, LocalDate to) {
    Map<LocalDate, DailyTotals> totals = new TreeMap<>();
    for (AccountDaily row : dailyRepository.findAllByOrderByDateAscAccountIdAsc()) {
      if (row.getDate().isBefore(from) || row.getDate().isAfter(to)) continue;
      DailyTotals total = totals.computeIfAbsent(row.getDate(), ignored -> new DailyTotals());
      total.endValue = total.endValue.add(nz(row.getEquityValue()));
      total.contributions = total.contributions.add(nz(row.getDepositsValue()));
      total.withdrawals = total.withdrawals.add(nz(row.getWithdrawalsValue()));
    }
    return totals.entrySet().stream()
        .map(
            entry ->
                new DailyPortfolioValue(
                    entry.getKey(),
                    entry.getValue().endValue,
                    entry.getValue().contributions,
                    entry.getValue().withdrawals))
        .toList();
  }

  private static final class DailyTotals {
    private BigDecimal endValue = ZERO;
    private BigDecimal contributions = ZERO;
    private BigDecimal withdrawals = ZERO;
  }

  private BigDecimal sum(
      List<PortfolioMonthlyPerformance> rows,
      java.util.function.Function<PortfolioMonthlyPerformance, BigDecimal> value) {
    return rows.stream().map(value).map(this::nz).reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }
}
