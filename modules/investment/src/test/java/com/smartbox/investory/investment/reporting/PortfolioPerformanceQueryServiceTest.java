package com.smartbox.investory.investment.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.ClosedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.ClosedPositionRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioPerformanceQueryServiceTest {
  private final ClosedPositionRepository closedPositions = mock();
  private final AccountDailyRepository daily = mock();
  private final AccountRepository accounts = mock();
  private final AccountMonthlyPerformanceRepository accountMonths = mock();
  private final AccountStatisticsRepository statistics = mock();
  private final PortfolioMonthlyPerformanceRepository portfolioMonths = mock();
  private final SymbolPerformanceRepository symbols = mock();
  private final PortfolioPerformanceQueryService service =
      new PortfolioPerformanceQueryService(
          closedPositions, daily, accounts, accountMonths, statistics, portfolioMonths, symbols);

  @Test
  void winRateUsesClosedPositionProfitSign() {
    ClosedPosition profitable = new ClosedPosition();
    profitable.setProfit(10.0);
    ClosedPosition loss = new ClosedPosition();
    loss.setProfit(-1.0);
    when(closedPositions.findAll()).thenReturn(List.of(profitable, loss));

    assertThat(service.calculateWinRate()).isEqualTo(50.0);
  }

  @Test
  void instrumentPerformancePreservesOtherBucketSemantics() {
    when(symbols.findAll())
        .thenReturn(
            List.of(
                new SymbolPerformanceEntity("major", 100, 0, 100, 0, 0, 0, 10, 20, null),
                new SymbolPerformanceEntity("minor", 0.5, 0, 1, 0, 0, 0, 5, 6, null)));

    var result = service.calculatePerformancePerInstrument();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getSymbol()).isEqualTo("major");
    assertThat(result.get(1).getSymbol()).isEqualTo("Other");
    assertThat(result.get(1).getTotal()).isEqualTo(0.5);
  }
}
