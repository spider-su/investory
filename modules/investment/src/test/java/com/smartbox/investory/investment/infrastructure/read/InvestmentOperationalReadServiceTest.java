package com.smartbox.investory.investment.infrastructure.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.imports.BrokerType;
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
  private final PositionRepository positions = mock();
  private final CurrencyRateService currencyRates = mock();
  private final InvestmentOperationalReadService service =
      new InvestmentOperationalReadService(portfolios, imports, positions, currencyRates);

  @Test
  void portfolioUsesLegacyDefaultPortfolioId() {
    var value = new Portfolio();
    value.setBalance(10.5);
    value.setTotalProfit(2.5);
    when(portfolios.calculateTotalProfitLoss(1L)).thenReturn(value);

    var result = service.portfolio();

    assertEquals("USD", result.baseCurrency());
    assertEquals(10.5, result.balance().doubleValue());
    verify(portfolios).calculateTotalProfitLoss(1L);
  }

  @Test
  void latestImportMapsBrokerState() {
    var batch = new ImportHistoryEntity();
    batch.setId(7L);
    batch.setBroker(BrokerType.IBKR);
    batch.setStatus(com.smartbox.investory.investment.imports.ImportBatchStatus.COMPLETED);
    batch.setStartedAt(ZonedDateTime.parse("2026-09-08T10:00:00Z"));
    batch.setFinishedAt(ZonedDateTime.parse("2026-09-08T10:01:00Z"));
    when(imports.findFirstByOrderByIdDesc()).thenReturn(Optional.of(batch));

    var result = service.latestImport().orElseThrow();

    assertEquals(7L, result.batchId());
    assertEquals(
        com.smartbox.investory.investment.api.importing.ImportBroker.IBKR, result.broker());
    assertEquals("COMPLETED", result.status());
  }

  @Test
  void symbolExposuresUsesMarketPriceAndUsdCompatibilityOutput() {
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
    when(positions.findOpen()).thenReturn(List.of(position));
    when(currencyRates.convertToBaseCurrency(10.0, CurrencyType.USD, CurrencyType.EUR))
        .thenReturn(11.0);

    var result = service.symbolExposures();

    assertEquals(
        List.of(
            new com.smartbox.investory.investment.api.operations.PortfolioExposureReader
                .SymbolExposure("ABC", BigDecimal.valueOf(11.0), "USD")),
        result);
    verify(currencyRates).convertToBaseCurrency(10.0, CurrencyType.USD, CurrencyType.EUR);
  }
}
