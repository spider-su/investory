package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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
    return forPortfolioMonths(null, from, to);
  }

  /** Returns the exact aggregate for one portfolio and inclusive monthly range. */
  public PerformanceResult forPortfolioMonths(Long portfolioId, YearMonth from, YearMonth to) {
    List<PortfolioMonthlyPerformanceEntity> rows =
        repository.findAllByOrderByMonthAscPortfolioIdAsc().stream()
            .filter(row -> portfolioId == null || portfolioId.equals(row.getPortfolioId()))
            .filter(row -> from == null || !YearMonth.from(row.getMonth()).isBefore(from))
            .filter(row -> to == null || !YearMonth.from(row.getMonth()).isAfter(to))
            .toList();
    if (rows.isEmpty()) {
      return empty(from, to);
    }

    PortfolioMonthlyPerformanceEntity first = rows.getFirst();
    PortfolioMonthlyPerformanceEntity last = rows.getLast();
    BigDecimal contributions = sum(rows, PortfolioMonthlyPerformanceEntity::getDepositFlowDecimal);
    BigDecimal withdrawals = sum(rows, PortfolioMonthlyPerformanceEntity::getWithdrawalFlowDecimal);
    BigDecimal investmentResult = sum(rows, PortfolioMonthlyPerformanceEntity::getProfitDecimal);
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
            sum(rows, PortfolioMonthlyPerformanceEntity::getRealizedProfitDecimal),
            null,
            sum(rows, PortfolioMonthlyPerformanceEntity::getDividendsDecimal),
            sum(rows, PortfolioMonthlyPerformanceEntity::getInterestDecimal),
            sum(rows, PortfolioMonthlyPerformanceEntity::getFeesDecimal),
            sum(rows, PortfolioMonthlyPerformanceEntity::getTaxesDecimal),
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
    List<DailyPortfolioValue> dailyValues =
        dailyValues(first.getPortfolioId(), first.getFirstDate(), last.getEndDate());
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

  private List<DailyPortfolioValue> dailyValues(Long portfolioId, LocalDate from, LocalDate to) {
    return dailyRepository.findPortfolioPerformanceDaily(portfolioId, from, to).stream()
        .map(
            row ->
                new DailyPortfolioValue(
                    row.getDate(),
                    row.getEndValue(),
                    row.getContributions(),
                    row.getWithdrawals()))
        .toList();
  }

  private BigDecimal sum(
      List<PortfolioMonthlyPerformanceEntity> rows,
      java.util.function.Function<PortfolioMonthlyPerformanceEntity, BigDecimal> value) {
    return rows.stream().map(value).map(this::nz).reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }
}
