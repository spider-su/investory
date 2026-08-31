package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.TrailingPortfolioReturnReader;
import com.smartbox.investory.investment.api.reporting.model.PerformanceAttribution;
import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application query over the canonical SQL portfolio-monthly reporting projection. */
@Service
@Transactional(readOnly = true)
public class PortfolioPerformanceQuery implements TrailingPortfolioReturnReader {
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
    List<PortfolioMonthlyPerformanceEntity> rows = monthlyRows(null, from, to);
    if (rows.isEmpty()) {
      return empty(from, to);
    }
    Map<Long, List<PortfolioMonthlyPerformanceEntity>> byPortfolio =
        rows.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    PortfolioMonthlyPerformanceEntity::getPortfolioId,
                    LinkedHashMap::new,
                    java.util.stream.Collectors.toList()));
    List<CurrencyType> currencies =
        rows.stream().map(PortfolioMonthlyPerformanceEntity::getBaseCurrency).distinct().toList();
    if (currencies.size() != 1) {
      throw new IllegalStateException(
          "Shared portfolio income requires one base currency, found " + currencies);
    }
    LocalDate start =
        rows.stream()
            .map(PortfolioMonthlyPerformanceEntity::getFirstDate)
            .min(Comparator.naturalOrder())
            .orElseThrow();
    LocalDate end =
        rows.stream()
            .map(PortfolioMonthlyPerformanceEntity::getEndDate)
            .max(Comparator.naturalOrder())
            .orElseThrow();
    BigDecimal startValue =
        byPortfolio.values().stream()
            .map(List::getFirst)
            .map(PortfolioMonthlyPerformanceEntity::getStartEquity)
            .map(this::nz)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal endValue =
        byPortfolio.values().stream()
            .map(List::getLast)
            .map(PortfolioMonthlyPerformanceEntity::getEndEquity)
            .map(this::nz)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal contributions = sum(rows, PortfolioMonthlyPerformanceEntity::getDepositFlow);
    BigDecimal withdrawals = sum(rows, PortfolioMonthlyPerformanceEntity::getWithdrawalFlow);
    PerformanceResult result =
        new PerformanceResult(
            new PerformancePeriod(start, end),
            currencies.getFirst(),
            startValue,
            endValue,
            contributions,
            withdrawals,
            contributions.subtract(withdrawals),
            sum(rows, PortfolioMonthlyPerformanceEntity::getProfit),
            sum(rows, PortfolioMonthlyPerformanceEntity::getRealizedProfit),
            null,
            sum(rows, PortfolioMonthlyPerformanceEntity::getDividends),
            sum(rows, PortfolioMonthlyPerformanceEntity::getInterest),
            sum(rows, PortfolioMonthlyPerformanceEntity::getFees),
            sum(rows, PortfolioMonthlyPerformanceEntity::getTaxes),
            null,
            ReturnMetric.unavailable(
                ReturnMetric.Status.INSUFFICIENT_DATA,
                "Shared multi-portfolio return is not defined"),
            ReturnMetric.unavailable(
                ReturnMetric.Status.INSUFFICIENT_DATA,
                "Shared multi-portfolio return is not defined"),
            null);
    return withAttribution(result);
  }

  @Override
  public BigDecimal returnPercentage(Long portfolioId, YearMonth from, YearMonth to) {
    return forPortfolioMonths(portfolioId, from, to).returnPercentage();
  }

  /** Returns the exact aggregate for one portfolio and inclusive monthly range. */
  public PerformanceResult forPortfolioMonths(Long portfolioId, YearMonth from, YearMonth to) {
    List<PortfolioMonthlyPerformanceEntity> rows = monthlyRows(portfolioId, from, to);
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

  /** Returns the cumulative portfolio result without loading the dashboard projection rows. */
  public PortfolioResult portfolioResult(Long portfolioId) {
    var currencies = repository.findCurrenciesByPortfolioId(portfolioId);
    if (currencies.isEmpty()) {
      return new PortfolioResult(null, null);
    }
    return new PortfolioResult(
        repository.sumProfitByPortfolioId(portfolioId), currencies.getFirst());
  }

  private List<PortfolioMonthlyPerformanceEntity> monthlyRows(
      Long portfolioId, YearMonth from, YearMonth to) {
    if (portfolioId == null) {
      LocalDate fromDate = from == null ? null : from.atDay(1);
      LocalDate toDate = to == null ? null : to.atEndOfMonth();
      if (fromDate == null && toDate == null) {
        return repository.findAllByOrderByMonthAscPortfolioIdAsc();
      }
      if (fromDate == null) {
        return repository.findByMonthLessThanEqualOrderByMonthAscPortfolioIdAsc(toDate);
      }
      if (toDate == null) {
        return repository.findByMonthGreaterThanEqualOrderByMonthAscPortfolioIdAsc(fromDate);
      }
      return repository.findByMonthBetweenOrderByMonthAscPortfolioIdAsc(fromDate, toDate);
    }
    LocalDate fromDate = from == null ? null : from.atDay(1);
    LocalDate toDate = to == null ? null : to.atEndOfMonth();
    if (fromDate == null && toDate == null) {
      return repository.findByPortfolioIdOrderByMonthAsc(portfolioId);
    }
    if (fromDate == null) {
      return repository.findByPortfolioIdAndMonthLessThanEqualOrderByMonthAsc(portfolioId, toDate);
    }
    if (toDate == null) {
      return repository.findByPortfolioIdAndMonthGreaterThanEqualOrderByMonthAsc(
          portfolioId, fromDate);
    }
    return repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
        portfolioId, fromDate, toDate);
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
                    row.getDate(), row.getEndValue(), row.getContributions(), row.getWithdrawals()))
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

  private PerformanceResult withAttribution(PerformanceResult result) {
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

  public record PortfolioResult(BigDecimal investmentResult, CurrencyType baseCurrency) {}
}
