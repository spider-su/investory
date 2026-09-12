package com.smartbox.investory.investment.infrastructure.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportRepository;
import com.smartbox.investory.investment.ledger.position.PositionType;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.performance.PortfolioMetricsService;
import com.smartbox.investory.investment.performance.model.Portfolio;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InvestmentOperationalReadServiceTest {
  private final PortfolioMetricsService portfolios = mock();
  private final ImportRepository imports = mock();
  private final AccountRepository accounts = mock();
  private final PositionRepository positions = mock();
  private final CurrencyRateService currencyRates = mock();
  private final InvestmentOperationalReadService service =
      new InvestmentOperationalReadService(portfolios, imports, accounts, positions, currencyRates);

  @Test
  void portfolioUsesRequestedPortfolioId() {
    var value = new Portfolio();
    value.setBalance(10.5);
    value.setTotalProfit(2.5);
    when(portfolios.calculateTotalProfitLoss(2L)).thenReturn(value);

    var result = service.portfolio(2L);

    assertEquals("USD", result.baseCurrency());
    assertEquals(10.5, result.balance().doubleValue());
    verify(portfolios).calculateTotalProfitLoss(2L);
  }

  @Test
  void latestImportMapsBrokerStateForPortfolio() {
    var batch = new ImportHistoryEntity();
    batch.setId(7L);
    batch.setBroker(BrokerType.IBKR);
    batch.setStatus(com.smartbox.investory.investment.imports.ImportBatchStatus.COMPLETED);
    batch.setStartedAt(ZonedDateTime.parse("2026-09-08T10:00:00Z"));
    batch.setFinishedAt(ZonedDateTime.parse("2026-09-08T10:01:00Z"));
    when(imports.findFirstByPortfolioIdOrderByIdDesc(2L)).thenReturn(Optional.of(batch));

    var result = service.latestImport(2L).orElseThrow();

    assertEquals(7L, result.batchId());
    assertEquals(
        com.smartbox.investory.investment.api.importing.ImportBroker.IBKR, result.broker());
    assertEquals("COMPLETED", result.status());
  }

  @Test
  void symbolExposuresUsesOnlyRequestedPortfolio() {
    PositionEntity position =
        PositionEntity.builder()
            .id(1L)
            .sourceAssetSymbol("ABC")
            .symbol("ABC")
            .type(PositionType.BUY)
            .volume(BigDecimal.TWO)
            .openPrice(BigDecimal.valueOf(4))
            .priceCurrency(CurrencyType.EUR)
            .build();
    position.setMarketPrice(BigDecimal.valueOf(5));
    var portfolio = new Portfolio();
    portfolio.setBaseCurrency(CurrencyType.PLN);
    when(accounts.findIdsByPortfolioId(2L)).thenReturn(List.of(11L));
    when(positions.findOpenByAccountIn(List.of(11L))).thenReturn(List.of(position));
    when(portfolios.calculateTotalProfitLoss(2L)).thenReturn(portfolio);
    when(currencyRates.convertToBaseCurrency(10.0, CurrencyType.PLN, CurrencyType.EUR))
        .thenReturn(11.0);

    var result = service.symbolExposures(2L);

    assertEquals(
        List.of(
            new com.smartbox.investory.investment.api.operations.PortfolioExposureReader
                .SymbolExposure("ABC", BigDecimal.valueOf(11.0), "PLN")),
        result);
    verify(currencyRates).convertToBaseCurrency(10.0, CurrencyType.PLN, CurrencyType.EUR);
    verify(positions).findOpenByAccountIn(List.of(11L));
  }
}
