package com.smartbox.investory.investment.infrastructure.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummary;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BrokeragePortfolioContextReadServiceTest {
  @Test
  void exposesExistingPortfolioIdAndBaseCurrency() {
    PortfolioKpiSummaryRepository summaries = Mockito.mock(PortfolioKpiSummaryRepository.class);
    PortfolioKpiSummary summary = new PortfolioKpiSummary();
    summary.setBaseCurrency(CurrencyType.USD);
    when(summaries.findById(7L)).thenReturn(Optional.of(summary));

    var result = new BrokeragePortfolioContextReadService(summaries).findById(7L);

    assertEquals(7L, result.orElseThrow().portfolioId());
    assertEquals(CurrencyType.USD, result.orElseThrow().baseCurrency());
  }

  @Test
  void preservesMissingPortfolioBehavior() {
    PortfolioKpiSummaryRepository summaries = Mockito.mock(PortfolioKpiSummaryRepository.class);
    when(summaries.findById(7L)).thenReturn(Optional.empty());

    assertTrue(new BrokeragePortfolioContextReadService(summaries).findById(7L).isEmpty());
  }
}
