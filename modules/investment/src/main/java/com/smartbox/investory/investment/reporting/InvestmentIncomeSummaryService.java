package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.InvestmentIncomeSummaryReader;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Composes the Investment-owned income source from canonical monthly reporting data. */
@Service
@RequiredArgsConstructor
public class InvestmentIncomeSummaryService implements InvestmentIncomeSummaryReader {
  private final PortfolioMonthlyPerformanceRepository monthlyPerformance;
  private final PortfolioPerformanceQuery performance;
  private final com.smartbox.investory.investment.reporting.dashboard.application
          .InvestmentDashboardFacade
      dashboard;
  private final Clock clock;

  @Override
  public InvestmentIncomeSummary load(Long portfolioId) {
    LocalDate today = LocalDate.now(clock);
    YearMonth current = YearMonth.from(today);
    List<PortfolioMonthlyPerformanceEntity> rows =
        monthlyPerformance.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
            portfolioId, current.withMonth(1).atDay(1), current.atEndOfMonth());
    if (rows.isEmpty() || !rows.getFirst().getMonth().equals(current.withMonth(1).atDay(1))) {
      return new InvestmentIncomeSummary(false, null, null, null, null, null, null, null);
    }
    var kpi = dashboard.loadPerformanceKpi(portfolioId);
    if (kpi.annualizedReturn() == null || kpi.annualizedReturn().value() == null) {
      return new InvestmentIncomeSummary(
          false, rows.getFirst().getBaseCurrency(), null, null, null, null, null, null);
    }
    var flows =
        rows.stream()
            .map(
                row ->
                    new InvestmentIncomeCalculator.MonthlyExternalFlow(
                        YearMonth.from(row.getMonth()),
                        row.getDepositFlowDecimal().subtract(row.getWithdrawalFlowDecimal())))
            .toList();
    var base =
        InvestmentIncomeCalculator.weightedIncomeBase(
            rows.getFirst().getStartEquityDecimal(), flows);
    var projected =
        InvestmentIncomeCalculator.projectedAnnualIncome(base, kpi.annualizedReturn().value());
    var expected = InvestmentIncomeCalculator.expectedIncomeYtd(projected, current.getMonthValue());
    var ytd =
        performance
            .forPortfolioMonths(portfolioId, current.withMonth(1), current)
            .investmentResult();
    return new InvestmentIncomeSummary(
        true,
        rows.getFirst().getBaseCurrency(),
        base,
        projected,
        kpi.annualizedReturn().value(),
        ytd,
        expected,
        InvestmentIncomeCalculator.expectationProgress(ytd, expected));
  }
}
