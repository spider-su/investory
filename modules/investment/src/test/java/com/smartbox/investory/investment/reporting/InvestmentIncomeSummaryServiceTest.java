package com.smartbox.investory.investment.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.investment.reporting.dashboard.application.InvestmentDashboardFacade;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentIncomeSummaryServiceTest {
  private static final Long PORTFOLIO_ID = 7L;
  private final PortfolioMonthlyPerformanceRepository monthly = mock();
  private final PortfolioPerformanceQuery performance = mock();
  private final InvestmentDashboardFacade dashboard = mock();
  private final Clock clock = Clock.fixed(Instant.parse("2026-02-15T12:00:00Z"), ZoneOffset.UTC);
  private final InvestmentIncomeSummaryService service =
      new InvestmentIncomeSummaryService(monthly, performance, dashboard, clock);

  @Test
  void reportsUnavailableWhenCurrentYearHasNoJanuaryRow() {
    when(monthly.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
            PORTFOLIO_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)))
        .thenReturn(List.of());

    var result = service.load(PORTFOLIO_ID);

    assertThat(result.available()).isFalse();
    assertThat(result.currency()).isNull();
    verifyNoInteractions(dashboard, performance);
  }

  @Test
  void reportsUnavailableWhenAnnualizedReturnCannotBeCalculated() {
    when(monthly.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
            PORTFOLIO_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)))
        .thenReturn(List.of(row("2026-01-01", "1000", "0", "0")));
    when(dashboard.loadPerformanceKpi(PORTFOLIO_ID))
        .thenReturn(
            new InvestmentDashboardFacade.PerformanceKpi(
                ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "not enough"),
                ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "not enough"),
                "2026-01-01"));

    var result = service.load(PORTFOLIO_ID);

    assertThat(result.available()).isFalse();
    assertThat(result.currency()).isEqualTo(CurrencyType.USD);
    assertThat(result.incomeBase()).isNull();
    verifyNoInteractions(performance);
  }

  @Test
  void calculatesProjectedAndYearToDateIncomeFromIndependentMonthlyFacts() {
    when(monthly.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
            PORTFOLIO_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)))
        .thenReturn(
            List.of(row("2026-01-01", "1000", "100", "20"), row("2026-02-01", "1080", "0", "0")));
    when(dashboard.loadPerformanceKpi(PORTFOLIO_ID))
        .thenReturn(
            new InvestmentDashboardFacade.PerformanceKpi(
                ReturnMetric.available(new BigDecimal("0.10")),
                ReturnMetric.available(new BigDecimal("0.10")),
                "2026-01-01"));
    when(performance.forPortfolioMonths(
            PORTFOLIO_ID, java.time.YearMonth.of(2026, 1), java.time.YearMonth.of(2026, 2)))
        .thenReturn(
            new PerformanceResult(
                new PerformancePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)),
                CurrencyType.USD,
                bd("1000"),
                bd("1080"),
                bd("100"),
                bd("20"),
                bd("80"),
                bd("9"),
                bd("9"),
                null,
                bd("2"),
                bd("1"),
                bd("0"),
                bd("0"),
                null,
                ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "test"),
                ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "test"),
                null));

    var result = service.load(PORTFOLIO_ID);

    assertThat(result.available()).isTrue();
    assertThat(result.incomeBase()).isEqualByComparingTo("1080.00000000");
    assertThat(result.projectedAnnualIncome()).isEqualByComparingTo("108.00000000");
    assertThat(result.annualizedYield()).isEqualByComparingTo("0.10");
    assertThat(result.investmentResultYtd()).isEqualByComparingTo("9");
    assertThat(result.expectedIncomeYtd()).isEqualByComparingTo("18.00000000");
    assertThat(result.expectationProgress()).isEqualByComparingTo("0.50000000");
  }

  private static PortfolioMonthlyPerformanceEntity row(
      String month, String start, String deposit, String withdrawal) {
    var row = new PortfolioMonthlyPerformanceEntity();
    row.setPortfolioId(PORTFOLIO_ID);
    row.setMonth(LocalDate.parse(month));
    row.setFirstDate(LocalDate.parse(month));
    row.setEndDate(LocalDate.parse(month).plusMonths(1).minusDays(1));
    row.setBaseCurrency(CurrencyType.USD);
    row.setStartEquity(bd(start));
    row.setEndEquity(bd(start));
    row.setDepositFlow(bd(deposit));
    row.setWithdrawalFlow(bd(withdrawal));
    row.setProfit(BigDecimal.ZERO);
    row.setRealizedProfit(BigDecimal.ZERO);
    row.setDividends(BigDecimal.ZERO);
    row.setInterest(BigDecimal.ZERO);
    row.setFees(BigDecimal.ZERO);
    row.setTaxes(BigDecimal.ZERO);
    return row;
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
