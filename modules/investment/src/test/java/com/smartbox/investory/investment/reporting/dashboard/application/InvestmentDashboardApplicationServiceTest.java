package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.investment.reporting.PerformancePeriod;
import com.smartbox.investory.investment.reporting.PerformanceResult;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQuery;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("Investment Dashboard Application Service")
@ExtendWith(MockitoExtension.class)
class InvestmentDashboardApplicationServiceTest {

  @Mock InvestmentDashboardFacade dashboard;
  @Mock PortfolioPerformanceQuery performance;
  @Mock PortfolioContextReader portfolios;

  @Test
  void returnsCanonicalAnnualizedReturnWithoutMixingItWithCashIncome() {
    when(portfolios.findById(7L))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(PortfolioContext.class)));
    when(dashboard.loadPerformanceKpi(7L))
        .thenReturn(
            new InvestmentDashboardFacade.PerformanceKpi(
                ReturnMetric.available(new BigDecimal("0.173")),
                "2026-01-01",
                ReturnMetric.available(new BigDecimal("0.10")),
                new BigDecimal("0.08"),
                BigDecimal.ONE,
                "1Y portfolio history + benchmark estimate"));

    var service = service();

    var view = service.loadPerformanceKpi(7L);

    assertThat(view.totalReturn()).isEqualByComparingTo("0.173");
    assertThat(view.historicalAnnualizedReturn()).isEqualByComparingTo("0.10");
    assertThat(view.expectedAnnualReturn()).isEqualByComparingTo("0.08");
    assertThat(view.totalReturnDisplay()).isEqualTo("+17.3%");
    assertThat(view.historicalAnnualizedReturnDisplay()).isEqualTo("+10.0%");
    assertThat(view.expectedAnnualReturnDisplay()).isEqualTo("+8.0%");
    assertThat(view.kpiStartDate()).isEqualTo("2026-01-01");
  }

  @Test
  void ytdTwrReaderReturnsTheSameAvailabilityAwareCanonicalDashboardMetric() {
    when(portfolios.findById(7L))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(PortfolioContext.class)));
    ReturnMetric canonical = ReturnMetric.available(new BigDecimal("0.173"));
    when(dashboard.loadPerformanceKpi(7L))
        .thenReturn(
            new InvestmentDashboardFacade.PerformanceKpi(
                canonical,
                "2026-01-01",
                ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "history"),
                null,
                null,
                null));

    assertThat(service().ytdTwr(7L)).isSameAs(canonical);
    verify(dashboard).loadPerformanceKpi(7L);
  }

  @Test
  void returnsOnlyCurrentCalendarYearInvestmentResult() {
    when(portfolios.findById(7L))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(PortfolioContext.class)));
    when(performance.forPortfolioMonths(7L, YearMonth.of(2026, 1), YearMonth.of(2026, 8)))
        .thenReturn(result(CurrencyType.PLN, "100", "20", "10"));

    var view = service().investmentResultYtd(7L);

    assertThat(view.available()).isTrue();
    assertThat(view.amount()).isEqualByComparingTo("42");
    assertThat(view.currency()).isEqualTo(CurrencyType.PLN);
    verify(performance).forPortfolioMonths(7L, YearMonth.of(2026, 1), YearMonth.of(2026, 8));
  }

  private InvestmentDashboardApplicationService service() {
    return new InvestmentDashboardApplicationService(
        dashboard,
        performance,
        portfolios,
        Clock.fixed(Instant.parse("2026-08-26T12:00:00Z"), ZoneOffset.UTC));
  }

  private static PerformanceResult result(
      CurrencyType currency, String dividends, String interest, String taxes) {
    return new PerformanceResult(
        new PerformancePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 26)),
        currency,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("42"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal(dividends),
        new BigDecimal(interest),
        BigDecimal.ZERO,
        new BigDecimal(taxes),
        BigDecimal.ZERO,
        null,
        null,
        null);
  }
}
