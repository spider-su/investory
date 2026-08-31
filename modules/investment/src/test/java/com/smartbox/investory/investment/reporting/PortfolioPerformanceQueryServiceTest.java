package com.smartbox.investory.investment.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio Performance Query Service")
class PortfolioPerformanceQueryServiceTest {
  private final PositionRepository closedPositions = mock();
  private final AccountDailyRepository daily = mock();
  private final AccountRepository accounts = mock();
  private final AccountMonthlyPerformanceRepository accountMonths = mock();
  private final AccountStatisticsRepository statistics = mock();
  private final PortfolioMonthlyPerformanceRepository portfolioMonths = mock();
  private final SymbolPerformanceRepository symbols = mock();
  private final PortfolioPerformanceQueryService service =
      new PortfolioPerformanceQueryService(
          closedPositions, daily, accounts, accountMonths, statistics, portfolioMonths, symbols);

  @DisplayName("win Rate Uses Closed Position Profit Sign")
  @Test
  void winRateUsesPositionEntityProfitSign() {
    PositionEntity profitable = new PositionEntity();
    profitable.setProfit(java.math.BigDecimal.valueOf(10.0));
    PositionEntity loss = new PositionEntity();
    loss.setProfit(java.math.BigDecimal.valueOf(-1.0));
    when(closedPositions.findClosed()).thenReturn(List.of(profitable, loss));

    assertThat(service.calculateWinRate()).isEqualTo(50.0);
  }

  @DisplayName("instrument Performance Returns Symbols For Dashboard Bucketing")
  @Test
  void instrumentPerformanceReturnsSymbolsForDashboardBucketing() {
    when(symbols.findAll())
        .thenReturn(
            List.of(
                new SymbolPerformanceEntity("major", 100, 0, 100, 0, 0, 0, 10, 20, null),
                new SymbolPerformanceEntity("minor", 0.5, 0, 1, 0, 0, 0, 5, 6, null)));

    var result = service.calculatePerformancePerInstrument();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getSymbol()).isEqualTo("major");
    assertThat(result.get(1).getSymbol()).isEqualTo("minor");
    assertThat(result.get(1).getTotal()).isEqualTo(1.0);
  }

  @Test
  void dailyDetailAggregatesRowsAndUsesRequestedAccountFilter() {
    LocalDate date = LocalDate.of(2026, 5, 4);
    var row = new AccountDailyEntity();
    row.setAccountId(7L);
    row.setEquity(new BigDecimal("120"));
    row.setDailyProfitAmount(new BigDecimal("10"));
    row.setDailyReturn(new BigDecimal("0.02"));
    row.setDeposits(new BigDecimal("20"));
    row.setWithdrawals(new BigDecimal("5"));
    row.setDividends(new BigDecimal("2"));
    row.setInterest(new BigDecimal("1"));
    row.setFees(new BigDecimal("-1"));
    row.setTaxes(new BigDecimal("-2"));
    when(daily.findByDateAndAccountIdInOrderByAccountIdAsc(date, Set.of(7L)))
        .thenReturn(List.of(row));

    var result = service.dailyPerformanceDetail(date, Set.of(7L));

    assertThat(result.openingEquity()).isEqualTo(95.0);
    assertThat(result.closingEquity()).isEqualTo(120.0);
    assertThat(result.unresolvedResidual()).isEqualTo(10.0);
    assertThat(result.accounts())
        .singleElement()
        .satisfies(account -> assertThat(account.accountId()).isEqualTo(7L));
  }

  @Test
  void monthlyPerformanceCombinesPortfolioAttributionAndVisibleAccountContribution() {
    LocalDate month = LocalDate.of(2026, 5, 1);
    ZonedDateTime updated = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    var portfolio =
        new PortfolioMonthlyPerformanceEntity(
            1L,
            month,
            month,
            LocalDate.of(2026, 5, 31),
            1000,
            1150,
            CurrencyType.USD,
            100,
            0,
            5,
            2,
            -1,
            -2,
            20,
            50,
            0.05,
            updated);
    var account =
        new AccountMonthlyPerformanceEntity(
            "ignored",
            7L,
            month,
            LocalDate.of(2026, 5, 31),
            1000,
            1150,
            100,
            0,
            100,
            50,
            0.05,
            updated);
    when(accounts.findAll()).thenReturn(List.of());
    when(statistics.findAll()).thenReturn(List.of());
    when(portfolioMonths.findAllByOrderByMonthAscPortfolioIdAsc()).thenReturn(List.of(portfolio));
    when(accountMonths.findAllByOrderByMonthAscAccountIdAsc()).thenReturn(List.of(account));

    var result = service.calculateMonthlyPerformance();

    assertThat(result.getCalculateMonthlyPerformance()).containsEntry("2026-05", 50.0);
    assertThat(result.getMonthlyCashflow()).containsEntry("2026-05", 100.0);
    assertThat(result.getMonthlyOperationsCount()).containsEntry("2026-05", 1L);
    assertThat(result.getMonthlyAttributions().get("2026-05").accounts()).hasSize(1);
  }
}
