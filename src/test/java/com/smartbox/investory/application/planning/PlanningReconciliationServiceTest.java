package com.smartbox.investory.application.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.smartbox.investory.infrastructure.repository.portfolio.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanningReconciliationServiceTest {
  @Test
  void comparesSupportedHistoricalMarketMetricsAgainstCalendarYearPerformance() {
    PortfolioMonthlyPerformanceRepository repository =
        mock(PortfolioMonthlyPerformanceRepository.class);
    List<PortfolioMonthlyPerformance> rows = new java.util.ArrayList<>();
    for (int month = 1; month <= 12; month++) {
      PortfolioMonthlyPerformance row = mock(PortfolioMonthlyPerformance.class);
      when(row.getMonth()).thenReturn(LocalDate.of(2025, month, 1));
      when(row.getReturnPctDecimal()).thenReturn(BigDecimal.ZERO);
      rows.add(row);
    }
    PortfolioMonthlyPerformance january = rows.getFirst();
    PortfolioMonthlyPerformance december = rows.getLast();
    when(january.getDividendsDecimal()).thenReturn(new BigDecimal("2"));
    when(january.getInterestDecimal()).thenReturn(new BigDecimal("1"));
    when(january.getWithdrawalFlowDecimal()).thenReturn(new BigDecimal("4"));
    when(january.getDepositFlowDecimal()).thenReturn(new BigDecimal("1"));
    when(january.getReturnPctDecimal()).thenReturn(new BigDecimal("0.10"));
    when(december.getDividendsDecimal()).thenReturn(new BigDecimal("3"));
    when(december.getInterestDecimal()).thenReturn(new BigDecimal("2"));
    when(december.getWithdrawalFlowDecimal()).thenReturn(new BigDecimal("6"));
    when(december.getDepositFlowDecimal()).thenReturn(new BigDecimal("1"));
    when(december.getReturnPctDecimal()).thenReturn(new BigDecimal("0.20"));
    when(december.getEndEquityDecimal()).thenReturn(new BigDecimal("100"));
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
            1L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
        .thenReturn(rows);
    PastPlanningYear planningYear =
        new PastPlanningYear(
            2025,
            PlanningYearStatus.CLOSED,
            null,
            Map.of(
                PlanningMetric.MARKET_ASSETS, value(PlanningMetric.MARKET_ASSETS, "100"),
                PlanningMetric.MARKET_INCOME, value(PlanningMetric.MARKET_INCOME, "8"),
                PlanningMetric.MARKET_WITHDRAWAL, value(PlanningMetric.MARKET_WITHDRAWAL, "8"),
                PlanningMetric.MARKET_RETURN, value(PlanningMetric.MARKET_RETURN, "0.32")),
            Map.of());

    HistoricalReconciliation result =
        new PlanningReconciliationService(repository).reconcile(1L, planningYear);

    assertEquals(4, result.metrics().size());
    assertEquals(4, result.matchedCount());
    verify(repository)
        .findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
            1L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
  }

  @Test
  void missingHistoricalSourceIsNotAvailableAndDoesNotInventAComparison() {
    PortfolioMonthlyPerformanceRepository repository =
        mock(PortfolioMonthlyPerformanceRepository.class);
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(List.of());
    PastPlanningYear planningYear =
        new PastPlanningYear(
            2025,
            PlanningYearStatus.DRAFT,
            null,
            Map.of(PlanningMetric.MARKET_ASSETS, value(PlanningMetric.MARKET_ASSETS, "100")),
            Map.of());

    HistoricalReconciliation result =
        new PlanningReconciliationService(repository).reconcile(1L, planningYear);

    assertEquals(ReconciliationStatus.NOT_AVAILABLE, result.metrics().getFirst().status());
    assertEquals(ReconciliationQuality.UNAVAILABLE, result.metrics().getFirst().quality());
  }

  @Test
  void incompleteCalendarYearIsNotLabeledExact() {
    PortfolioMonthlyPerformanceRepository repository =
        mock(PortfolioMonthlyPerformanceRepository.class);
    PortfolioMonthlyPerformance january = mock(PortfolioMonthlyPerformance.class);
    when(january.getMonth()).thenReturn(LocalDate.of(2025, 1, 1));
    when(january.getEndEquityDecimal()).thenReturn(new BigDecimal("100"));
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(List.of(january));
    PastPlanningYear planningYear =
        new PastPlanningYear(
            2025,
            PlanningYearStatus.CLOSED,
            null,
            Map.of(PlanningMetric.MARKET_ASSETS, value(PlanningMetric.MARKET_ASSETS, "100")),
            Map.of());

    HistoricalReconciliation result =
        new PlanningReconciliationService(repository).reconcile(1L, planningYear);

    assertEquals(ReconciliationStatus.NOT_AVAILABLE, result.metrics().getFirst().status());
    assertEquals(ReconciliationQuality.UNAVAILABLE, result.metrics().getFirst().quality());
  }

  private static PlanningMetricValue value(PlanningMetric metric, String amount) {
    return new PlanningMetricValue(
        metric, new BigDecimal(amount), null, PlanningValueSource.ACCOUNTING_DERIVED, null);
  }
}
