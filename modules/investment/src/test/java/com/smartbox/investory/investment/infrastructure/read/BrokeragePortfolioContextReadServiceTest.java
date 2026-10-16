package com.smartbox.investory.investment.infrastructure.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("Brokerage Portfolio Context Read Service")
class BrokeragePortfolioContextReadServiceTest {
  @DisplayName("exposes Existing Portfolio Id And Both Stored Currencies")
  @Test
  void exposesExistingPortfolioIdAndBothStoredCurrencies() {
    PortfolioKpiSummaryRepository summaries = Mockito.mock(PortfolioKpiSummaryRepository.class);
    var currencies = Mockito.mock(PortfolioKpiSummaryRepository.PortfolioCurrenciesRow.class);
    when(currencies.getBaseCurrency()).thenReturn("USD");
    when(currencies.getLocalCurrency()).thenReturn("EUR");
    when(summaries.findCurrenciesByPortfolioId(7L)).thenReturn(Optional.of(currencies));

    var result = new BrokeragePortfolioContextReadService(summaries).findById(7L);

    assertEquals(7L, result.orElseThrow().portfolioId());
    assertEquals(CurrencyType.USD, result.orElseThrow().baseCurrency());
    assertEquals(CurrencyType.EUR, result.orElseThrow().localCurrency());
  }

  @DisplayName("preserves Missing Portfolio Behavior")
  @Test
  void preservesMissingPortfolioBehavior() {
    PortfolioKpiSummaryRepository summaries = Mockito.mock(PortfolioKpiSummaryRepository.class);
    when(summaries.findCurrenciesByPortfolioId(7L)).thenReturn(Optional.empty());

    assertTrue(new BrokeragePortfolioContextReadService(summaries).findById(7L).isEmpty());
  }
}
