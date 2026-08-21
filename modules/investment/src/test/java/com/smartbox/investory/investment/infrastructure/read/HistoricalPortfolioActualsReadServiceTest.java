package com.smartbox.investory.investment.infrastructure.read;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.infrastructure.persistence.portfolio.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalPortfolioActualsReadServiceTest {
  @Test
  void completeYearProvidesAnnualFactsAndDecemberAssets() {
    PortfolioMonthlyPerformanceRepository repository =
        mock(PortfolioMonthlyPerformanceRepository.class);
    List<PortfolioMonthlyPerformanceEntity> current = rows(12, true);
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(current);

    var result = new HistoricalPortfolioActualsReadService(repository).read(1L, 2025);

    assertTrue(result.complete());
    assertNull(result.startMarketAssets());
    assertEquals(new BigDecimal("120"), result.marketAssets());
    assertEquals(new BigDecimal("12"), result.marketIncome());
    assertEquals(new BigDecimal("12"), result.grossWithdrawals());
    assertEquals(BigDecimal.ZERO, result.netContribution());
    assertEquals(new BigDecimal("12"), result.netWithdrawal());
    assertNotNull(result.marketReturn());
  }

  @Test
  void usesPreviousDecemberForStartAndNetAnnualFlow() {
    PortfolioMonthlyPerformanceRepository repository =
        mock(PortfolioMonthlyPerformanceRepository.class);
    List<PortfolioMonthlyPerformanceEntity> current = rows(2025, 12, true);
    current.forEach(
        row -> {
          when(row.getDepositFlowDecimal())
              .thenReturn(
                  new BigDecimal("700000").divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP));
          when(row.getWithdrawalFlowDecimal())
              .thenReturn(
                  new BigDecimal("740429.67")
                      .divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP));
        });
    PortfolioMonthlyPerformanceEntity previous = row(LocalDate.of(2024, 12, 1), "1000000", "0", "0");
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenAnswer(
            invocation -> {
              LocalDate from = invocation.getArgument(1);
              if (from.getYear() == 2024) return List.of(previous);
              return current;
            });

    var result = new HistoricalPortfolioActualsReadService(repository).read(1L, 2025);

    assertEquals(new BigDecimal("1000000"), result.startMarketAssets());
    assertEquals(
        new BigDecimal("700000.00"), result.grossDeposits().setScale(2, RoundingMode.HALF_UP));
    assertEquals(
        new BigDecimal("740429.67"), result.grossWithdrawals().setScale(2, RoundingMode.HALF_UP));
    assertEquals(
        BigDecimal.ZERO.setScale(2), result.netContribution().setScale(2, RoundingMode.HALF_UP));
    assertEquals(
        new BigDecimal("40429.67"), result.netWithdrawal().setScale(2, RoundingMode.HALF_UP));
  }

  @Test
  void depositsExceedWithdrawalsProduceNetContributionOnly() {
    PortfolioMonthlyPerformanceRepository repository =
        mock(PortfolioMonthlyPerformanceRepository.class);
    List<PortfolioMonthlyPerformanceEntity> current = rows(2025, 12, true);
    current.forEach(
        row -> {
          when(row.getDepositFlowDecimal())
              .thenReturn(
                  new BigDecimal("800000").divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP));
          when(row.getWithdrawalFlowDecimal())
              .thenReturn(
                  new BigDecimal("300000").divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP));
        });
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(current);

    var result = new HistoricalPortfolioActualsReadService(repository).read(1L, 2025);

    assertEquals(
        new BigDecimal("500000.00"), result.netContribution().setScale(2, RoundingMode.HALF_UP));
    assertEquals(
        BigDecimal.ZERO.setScale(2), result.netWithdrawal().setScale(2, RoundingMode.HALF_UP));
  }

  @Test
  void noFlowsProduceZeroNetContributionAndWithdrawal() {
    PortfolioMonthlyPerformanceRepository repository =
        mock(PortfolioMonthlyPerformanceRepository.class);
    List<PortfolioMonthlyPerformanceEntity> current = rows(12, true);
    current.forEach(
        row -> {
          when(row.getDepositFlowDecimal()).thenReturn(null);
          when(row.getWithdrawalFlowDecimal()).thenReturn(null);
        });
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(current);

    var result = new HistoricalPortfolioActualsReadService(repository).read(1L, 2025);

    assertEquals(BigDecimal.ZERO, result.netContribution());
    assertEquals(BigDecimal.ZERO, result.netWithdrawal());
  }

  @Test
  void partialOrDuplicateCalendarYearIsUnavailable() {
    PortfolioMonthlyPerformanceRepository repository =
        mock(PortfolioMonthlyPerformanceRepository.class);
    List<PortfolioMonthlyPerformanceEntity> partial = rows(9, true);
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(partial);
    assertFalse(new HistoricalPortfolioActualsReadService(repository).read(1L, 2025).complete());

    List<PortfolioMonthlyPerformanceEntity> duplicate = rowsWithDuplicateMonth();
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(duplicate);
    assertFalse(new HistoricalPortfolioActualsReadService(repository).read(1L, 2025).complete());
  }

  @Test
  void missingMonthlyReturnKeepsOtherFactsButNotAnnualReturn() {
    PortfolioMonthlyPerformanceRepository repository =
        mock(PortfolioMonthlyPerformanceRepository.class);
    List<PortfolioMonthlyPerformanceEntity> current = rows(12, false);
    when(repository.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenReturn(current);

    var result = new HistoricalPortfolioActualsReadService(repository).read(1L, 2025);

    assertTrue(result.complete());
    assertNotNull(result.marketAssets());
    assertNotNull(result.marketIncome());
    assertNotNull(result.grossWithdrawals());
    assertNull(result.marketReturn());
  }

  private static List<PortfolioMonthlyPerformanceEntity> rows(int count, boolean returns) {
    return rows(2025, count, returns);
  }

  private static List<PortfolioMonthlyPerformanceEntity> rows(int year, int count, boolean returns) {
    List<PortfolioMonthlyPerformanceEntity> result = new ArrayList<>();
    for (int month = 1; month <= count; month++) {
      PortfolioMonthlyPerformanceEntity row = mock(PortfolioMonthlyPerformanceEntity.class);
      when(row.getMonth()).thenReturn(LocalDate.of(year, month, 1));
      when(row.getEndEquityDecimal()).thenReturn(new BigDecimal(month * 10));
      when(row.getDividendsDecimal()).thenReturn(BigDecimal.ONE);
      when(row.getInterestDecimal()).thenReturn(BigDecimal.ZERO);
      when(row.getWithdrawalFlowDecimal()).thenReturn(BigDecimal.ONE);
      when(row.getReturnPctDecimal())
          .thenReturn(returns ? BigDecimal.ZERO : (month == 6 ? null : BigDecimal.ZERO));
      result.add(row);
    }
    return result;
  }

  private static PortfolioMonthlyPerformanceEntity row(
      LocalDate month, String endEquity, String deposits, String withdrawals) {
    PortfolioMonthlyPerformanceEntity row = mock(PortfolioMonthlyPerformanceEntity.class);
    when(row.getMonth()).thenReturn(month);
    when(row.getEndEquityDecimal()).thenReturn(new BigDecimal(endEquity));
    when(row.getDepositFlowDecimal()).thenReturn(new BigDecimal(deposits));
    when(row.getWithdrawalFlowDecimal()).thenReturn(new BigDecimal(withdrawals));
    return row;
  }

  private static List<PortfolioMonthlyPerformanceEntity> rowsWithDuplicateMonth() {
    List<PortfolioMonthlyPerformanceEntity> result = rows(12, true);
    when(result.get(11).getMonth()).thenReturn(LocalDate.of(2025, 11, 1));
    return result;
  }
}
