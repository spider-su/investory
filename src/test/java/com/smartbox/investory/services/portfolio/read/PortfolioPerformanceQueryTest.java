package com.smartbox.investory.services.portfolio.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.infrastructure.repository.account.AccountDaily;
import com.smartbox.investory.infrastructure.repository.account.AccountDailyRepository;
import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformance;
import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioPerformanceQueryTest {
  private final PortfolioMonthlyPerformanceRepository repository = mock();
  private final AccountDailyRepository dailyRepository = mock();
  private final PortfolioPerformanceQuery query =
      new PortfolioPerformanceQuery(repository, dailyRepository);

  @Test
  void noFlowPeriodPreservesExactValues() {
    PortfolioMonthlyPerformance row = row("2026-01-01", "2026-01-31");
    row.setStartEquity(new BigDecimal("1000.12345678"));
    row.setEndEquity(new BigDecimal("1000.12345679"));
    when(repository.findAllByOrderByMonthAscPortfolioIdAsc()).thenReturn(List.of(row));

    PerformanceResult result = query.forMonths(YearMonth.of(2026, 1), YearMonth.of(2026, 1));

    assertThat(result.startValue()).isEqualByComparingTo("1000.12345678");
    assertThat(result.endValue()).isEqualByComparingTo("1000.12345679");
    assertThat(result.netExternalFlows()).isEqualByComparingTo("0");
  }

  @Test
  void aggregatesFlowsIncomeAndProfitFromReportingRows() {
    PortfolioMonthlyPerformance row = row("2026-02-01", "2026-02-28");
    row.setDepositFlow(new BigDecimal("100.10"));
    row.setWithdrawalFlow(new BigDecimal("20.05"));
    row.setDividends(new BigDecimal("1.25"));
    row.setInterest(new BigDecimal("2.35"));
    row.setFees(new BigDecimal("0.15"));
    row.setTaxes(new BigDecimal("0.25"));
    row.setRealizedProfit(new BigDecimal("3.45"));
    row.setProfit(new BigDecimal("6.65"));
    when(repository.findAllByOrderByMonthAscPortfolioIdAsc()).thenReturn(List.of(row));

    PerformanceResult result = query.forMonths(YearMonth.of(2026, 2), YearMonth.of(2026, 2));

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

  @Test
  void excludesRowsOutsideRequestedPeriod() {
    PortfolioMonthlyPerformance included = row("2026-03-01", "2026-03-31");
    included.setProfit(new BigDecimal("4.00"));
    PortfolioMonthlyPerformance excluded = row("2026-04-01", "2026-04-30");
    excluded.setProfit(new BigDecimal("99.00"));
    when(repository.findAllByOrderByMonthAscPortfolioIdAsc())
        .thenReturn(List.of(included, excluded));

    PerformanceResult result = query.forMonths(YearMonth.of(2026, 3), YearMonth.of(2026, 3));

    assertThat(result.investmentResult()).isEqualByComparingTo("4.00");
  }

  @Test
  void assemblesReturnMetricsFromAccountDailyBoundaries() {
    PortfolioMonthlyPerformance row = row("2026-05-01", "2026-05-31");
    row.setStartEquity(new BigDecimal("100"));
    row.setEndEquity(new BigDecimal("110"));
    when(repository.findAllByOrderByMonthAscPortfolioIdAsc()).thenReturn(List.of(row));
    AccountDaily daily = new AccountDaily();
    daily.setAccountId(1L);
    daily.setDate(LocalDate.parse("2026-05-31"));
    daily.setEquity(new BigDecimal("110"));
    daily.setDeposits(BigDecimal.ZERO);
    daily.setWithdrawals(BigDecimal.ZERO);
    when(dailyRepository.findAllByOrderByDateAscAccountIdAsc()).thenReturn(List.of(daily));

    PerformanceResult result = query.forMonths(YearMonth.of(2026, 5), YearMonth.of(2026, 5));

    assertThat(result.timeWeightedReturn().status()).isEqualTo(ReturnMetric.Status.AVAILABLE);
    assertThat(result.timeWeightedReturn().value()).isEqualByComparingTo("0.1");
    assertThat(result.moneyWeightedReturn().status()).isEqualTo(ReturnMetric.Status.AVAILABLE);
  }

  private static PortfolioMonthlyPerformance row(String firstDate, String endDate) {
    PortfolioMonthlyPerformance row = new PortfolioMonthlyPerformance();
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
