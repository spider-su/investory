package com.smartbox.investory.application.planning;

import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformance;
import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformanceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Reads an exact, complete calendar year's portfolio-performance facts. */
@Service
public class HistoricalPortfolioYearSource {
  private final PortfolioMonthlyPerformanceRepository performance;

  public HistoricalPortfolioYearSource(PortfolioMonthlyPerformanceRepository performance) {
    this.performance = performance;
  }

  public HistoricalPortfolioYear read(Long portfolioId, int year) {
    List<PortfolioMonthlyPerformance> rows =
        performance.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
            portfolioId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    Set<LocalDate> expectedMonths =
        java.util.stream.IntStream.rangeClosed(1, 12)
            .mapToObj(month -> LocalDate.of(year, month, 1))
            .collect(Collectors.toSet());
    Set<LocalDate> actualMonths =
        rows.stream().map(PortfolioMonthlyPerformance::getMonth).collect(Collectors.toSet());
    if (rows.size() != 12 || actualMonths.size() != 12 || !actualMonths.equals(expectedMonths)) {
      return HistoricalPortfolioYear.incomplete();
    }

    PortfolioMonthlyPerformance december =
        rows.stream()
            .filter(row -> row.getMonth().equals(LocalDate.of(year, 12, 1)))
            .findFirst()
            .orElseThrow();
    BigDecimal startMarketAssets = previousDecemberClosingValue(portfolioId, year);
    BigDecimal income =
        rows.stream()
            .map(row -> money(row.getDividendsDecimal()).add(money(row.getInterestDecimal())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal grossWithdrawals =
        rows.stream()
            .map(row -> money(row.getWithdrawalFlowDecimal()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal grossDeposits =
        rows.stream()
            .map(row -> money(row.getDepositFlowDecimal()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal netFlow = grossDeposits.subtract(grossWithdrawals);
    BigDecimal annualReturn = null;
    if (rows.stream().allMatch(row -> row.getReturnPctDecimal() != null)) {
      BigDecimal compounded = BigDecimal.ONE;
      for (PortfolioMonthlyPerformance row : rows) {
        compounded = compounded.multiply(BigDecimal.ONE.add(row.getReturnPctDecimal()));
      }
      annualReturn = compounded.subtract(BigDecimal.ONE);
    }
    return new HistoricalPortfolioYear(
        true,
        startMarketAssets,
        money(december.getEndEquityDecimal()),
        income,
        grossDeposits,
        grossWithdrawals,
        netFlow.max(BigDecimal.ZERO),
        netFlow.negate().max(BigDecimal.ZERO),
        annualReturn);
  }

  private BigDecimal previousDecemberClosingValue(Long portfolioId, int year) {
    List<PortfolioMonthlyPerformance> rows =
        performance.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
            portfolioId, LocalDate.of(year - 1, 12, 1), LocalDate.of(year - 1, 12, 31));
    return rows.stream()
        .filter(row -> row.getMonth().equals(LocalDate.of(year - 1, 12, 1)))
        .findFirst()
        .map(PortfolioMonthlyPerformance::getEndEquityDecimal)
        .orElse(null);
  }

  private static BigDecimal money(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  public record HistoricalPortfolioYear(
      boolean complete,
      BigDecimal startMarketAssets,
      BigDecimal marketAssets,
      BigDecimal marketIncome,
      BigDecimal grossDeposits,
      BigDecimal grossWithdrawals,
      BigDecimal netContribution,
      BigDecimal netWithdrawal,
      BigDecimal marketReturn) {
    /** End-of-calendar-year market value from the December row for this year. */
    public BigDecimal endMarketAssets() {
      return marketAssets;
    }

    static HistoricalPortfolioYear incomplete() {
      return new HistoricalPortfolioYear(false, null, null, null, null, null, null, null, null);
    }
  }
}
