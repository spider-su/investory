package com.smartbox.investory.investment.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository.PortfolioPerformanceDailyRow;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio Performance Query")
class PortfolioPerformanceQueryTest {
  private final PortfolioMonthlyPerformanceRepository repository = mock();
  private final AccountDailyRepository dailyRepository = mock();
  private final PortfolioPerformanceQuery query =
      new PortfolioPerformanceQuery(repository, dailyRepository);

  @DisplayName("no Flow Period Preserves Exact Values")
  @Test
  void noFlowPeriodPreservesExactValues() {
    PortfolioMonthlyPerformanceEntity row = row("2026-01-01", "2026-01-31");
    row.setStartEquity(new BigDecimal("1000.12345678"));
    row.setEndEquity(new BigDecimal("1000.12345679"));
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(List.of(row));

    PerformanceResult result =
        query.forPortfolioMonths(1L, YearMonth.of(2026, 1), YearMonth.of(2026, 1));

    assertThat(result.startValue()).isEqualByComparingTo("1000.12345678");
    assertThat(result.endValue()).isEqualByComparingTo("1000.12345679");
    assertThat(result.netExternalFlows()).isEqualByComparingTo("0");
  }

  @DisplayName("aggregates Flows Income And Profit From Reporting Rows")
  @Test
  void aggregatesFlowsIncomeAndProfitFromReportingRows() {
    PortfolioMonthlyPerformanceEntity row = row("2026-02-01", "2026-02-28");
    row.setDepositFlow(new BigDecimal("100.10"));
    row.setWithdrawalFlow(new BigDecimal("20.05"));
    row.setDividends(new BigDecimal("1.25"));
    row.setInterest(new BigDecimal("2.35"));
    row.setFees(new BigDecimal("0.15"));
    row.setTaxes(new BigDecimal("0.25"));
    row.setRealizedProfit(new BigDecimal("3.45"));
    row.setProfit(new BigDecimal("6.65"));
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(List.of(row));

    PerformanceResult result =
        query.forPortfolioMonths(1L, YearMonth.of(2026, 2), YearMonth.of(2026, 2));

    assertThat(result.contributions()).isEqualByComparingTo("100.10");
    assertThat(result.withdrawals()).isEqualByComparingTo("20.05");
    assertThat(result.netExternalFlows()).isEqualByComparingTo("80.05");
    assertThat(result.investmentResult()).isEqualByComparingTo("6.65");
    assertThat(result.realizedProfit()).isEqualByComparingTo("3.45");
    assertThat(result.dividends()).isEqualByComparingTo("1.25");
    assertThat(result.interest()).isEqualByComparingTo("2.35");
    assertThat(result.fees()).isEqualByComparingTo("0.15");
    assertThat(result.taxes()).isEqualByComparingTo("0.25");
  }

  @DisplayName("excludes Rows Outside Requested Period")
  @Test
  void excludesRowsOutsideRequestedPeriod() {
    PortfolioMonthlyPerformanceEntity included = row("2026-03-01", "2026-03-31");
    included.setProfit(new BigDecimal("4.00"));
    PortfolioMonthlyPerformanceEntity excluded = row("2026-04-01", "2026-04-30");
    excluded.setProfit(new BigDecimal("99.00"));
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(List.of(included));

    PerformanceResult result =
        query.forPortfolioMonths(1L, YearMonth.of(2026, 3), YearMonth.of(2026, 3));

    assertThat(result.investmentResult()).isEqualByComparingTo("4.00");
  }

  @DisplayName("assembles Return Metrics From Canonical Portfolio Daily Boundaries")
  @Test
  void assemblesReturnMetricsFromCanonicalPortfolioDailyBoundaries() {
    PortfolioMonthlyPerformanceEntity row = row("2026-05-01", "2026-05-31");
    row.setStartEquity(new BigDecimal("100"));
    row.setEndEquity(new BigDecimal("110"));
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(List.of(row));
    PortfolioPerformanceDailyRow dailyRow =
        daily("2026-05-31", new BigDecimal("110"), BigDecimal.ZERO, BigDecimal.ZERO);
    when(dailyRepository.findPortfolioPerformanceDaily(
            1L, LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")))
        .thenReturn(List.of(dailyRow));

    PerformanceResult result =
        query.forPortfolioMonths(1L, YearMonth.of(2026, 5), YearMonth.of(2026, 5));

    assertThat(result.timeWeightedReturn().status()).isEqualTo(ReturnMetric.Status.AVAILABLE);
    assertThat(result.timeWeightedReturn().value()).isEqualByComparingTo("0.1");
    assertThat(result.moneyWeightedReturn().status()).isEqualTo(ReturnMetric.Status.AVAILABLE);
    verify(dailyRepository)
        .findPortfolioPerformanceDaily(
            1L, LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"));
  }

  @DisplayName("missing Normalized Flow Makes Return Unavailable")
  @Test
  void missingNormalizedFlowMakesReturnUnavailable() {
    PortfolioMonthlyPerformanceEntity row = row("2026-06-01", "2026-06-30");
    row.setStartEquity(new BigDecimal("100"));
    row.setEndEquity(new BigDecimal("110"));
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(List.of(row));
    PortfolioPerformanceDailyRow dailyRow =
        daily("2026-06-30", new BigDecimal("110"), null, BigDecimal.ZERO);
    when(dailyRepository.findPortfolioPerformanceDaily(
            1L, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30")))
        .thenReturn(List.of(dailyRow));

    PerformanceResult result =
        query.forPortfolioMonths(1L, YearMonth.of(2026, 6), YearMonth.of(2026, 6));

    assertThat(result.timeWeightedReturn().status())
        .isEqualTo(ReturnMetric.Status.INSUFFICIENT_DATA);
    assertThat(result.moneyWeightedReturn().status())
        .isEqualTo(ReturnMetric.Status.INSUFFICIENT_DATA);
  }

  @DisplayName("scopes Monthly And Daily Performance To Requested Portfolio")
  @Test
  void scopesMonthlyAndDailyPerformanceToRequestedPortfolio() {
    PortfolioMonthlyPerformanceEntity firstPortfolio = row("2026-07-01", "2026-07-31");
    PortfolioMonthlyPerformanceEntity otherPortfolio = row("2026-07-01", "2026-07-31");
    otherPortfolio.setPortfolioId(2L);
    otherPortfolio.setProfit(new BigDecimal("999"));
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(List.of(firstPortfolio));

    PerformanceResult result =
        query.forPortfolioMonths(1L, YearMonth.of(2026, 7), YearMonth.of(2026, 7));

    assertThat(result.investmentResult()).isEqualByComparingTo("0");
    verify(dailyRepository)
        .findPortfolioPerformanceDaily(
            1L, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));
  }

  @DisplayName("trailing Return Reader Does Not Leak Another Portfolios Observation")
  @Test
  void trailingReturnReaderDoesNotLeakAnotherPortfoliosObservation() {
    PortfolioMonthlyPerformanceEntity requested = row("2026-07-01", "2026-07-31");
    requested.setReturnPct(new BigDecimal("0.0"));
    PortfolioMonthlyPerformanceEntity other = row("2026-07-01", "2026-07-31");
    other.setPortfolioId(2L);
    other.setReturnPct(new BigDecimal("12.5"));
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0, Long.class) == 1L ? List.of(requested) : List.of());

    assertThat(query.returnPercentage(1L, YearMonth.of(2026, 7), YearMonth.of(2026, 7)))
        .isEqualByComparingTo("0.0");
    assertThat(query.returnPercentage(3L, YearMonth.of(2026, 7), YearMonth.of(2026, 7))).isNull();
  }

  @DisplayName("portfolio Result Uses Narrow Repository Queries")
  @Test
  void portfolioResultUsesNarrowRepositoryQueries() {
    when(repository.findCurrenciesByPortfolioId(1L)).thenReturn(List.of(CurrencyType.USD));
    when(repository.sumProfitByPortfolioId(1L)).thenReturn(new BigDecimal("12.34"));

    PortfolioPerformanceQuery.PortfolioResult result = query.portfolioResult(1L);

    assertThat(result.investmentResult()).isEqualByComparingTo("12.34");
    assertThat(result.baseCurrency()).isEqualTo(CurrencyType.USD);
  }

  private static PortfolioPerformanceDailyRow daily(
      String date, BigDecimal endValue, BigDecimal contributions, BigDecimal withdrawals) {
    PortfolioPerformanceDailyRow row = mock();
    when(row.getDate()).thenReturn(LocalDate.parse(date));
    when(row.getEndValue()).thenReturn(endValue);
    when(row.getContributions()).thenReturn(contributions);
    when(row.getWithdrawals()).thenReturn(withdrawals);
    return row;
  }

  private static PortfolioMonthlyPerformanceEntity row(String firstDate, String endDate) {
    PortfolioMonthlyPerformanceEntity row = new PortfolioMonthlyPerformanceEntity();
    row.setPortfolioId(1L);
    row.setMonth(LocalDate.parse(firstDate));
    row.setFirstDate(LocalDate.parse(firstDate));
    row.setEndDate(LocalDate.parse(endDate));
    row.setBaseCurrency(CurrencyType.USD);
    row.setStartEquity(BigDecimal.ZERO);
    row.setEndEquity(BigDecimal.ZERO);
    row.setDepositFlow(BigDecimal.ZERO);
    row.setWithdrawalFlow(BigDecimal.ZERO);
    row.setDividends(BigDecimal.ZERO);
    row.setInterest(BigDecimal.ZERO);
    row.setFees(BigDecimal.ZERO);
    row.setTaxes(BigDecimal.ZERO);
    row.setRealizedProfit(BigDecimal.ZERO);
    row.setProfit(BigDecimal.ZERO);
    return row;
  }
}
