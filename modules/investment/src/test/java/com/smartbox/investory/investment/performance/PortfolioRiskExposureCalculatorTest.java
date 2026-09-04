package com.smartbox.investory.investment.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioCurrencyBreakdownEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioCurrencyBreakdownRepository;
import com.smartbox.investory.investment.performance.model.Portfolio;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortfolioRiskExposureCalculatorTest {
  private PortfolioAssetAllocationRepository allocations;
  private PortfolioCurrencyBreakdownRepository currencies;
  private PortfolioRiskExposureCalculator calculator;

  @BeforeEach
  void setUp() {
    allocations = mock(PortfolioAssetAllocationRepository.class);
    currencies = mock(PortfolioCurrencyBreakdownRepository.class);
    calculator = new PortfolioRiskExposureCalculator(allocations, currencies);
  }

  @Test
  void emptyPortfolioProducesUnavailableRiskWithoutDivision() {
    when(allocations.findAllByPortfolioId(1L)).thenReturn(List.of(allocation(null)));
    var portfolio = portfolio();
    calculator.applyTo(portfolio, 1L);
    assertThat(portfolio.getRiskExposure().warnings()).contains("Exposure data unavailable");
  }

  @Test
  void exactThresholdsProduceWarningsAndCurrencyExposure() {
    when(allocations.findAllByPortfolioId(1L))
        .thenReturn(
            List.of(
                allocation("20"),
                allocation("10"),
                allocation("10"),
                allocation("5"),
                allocation("5"),
                allocation("50")));
    when(currencies.findAllByPortfolioIdAndMetricType(1L, "ACCOUNT_LATEST"))
        .thenReturn(List.of(currency(CurrencyType.USD, "70"), currency(CurrencyType.PLN, "30")));
    var portfolio = portfolio();

    calculator.applyTo(portfolio, 1L);

    assertThat(portfolio.getRiskExposure().largestAssetWeightPct()).isEqualTo(50.0);
    assertThat(portfolio.getRiskExposure().baseCurrencyAccountExposurePct()).isEqualTo(70.0);
    assertThat(portfolio.getRiskExposure().foreignCurrencyAccountExposurePct()).isEqualTo(30.0);
    assertThat(portfolio.getRiskExposure().warnings()).hasSize(2);
  }

  private static Portfolio portfolio() {
    var portfolio = new Portfolio();
    portfolio.setBaseCurrency(CurrencyType.USD);
    portfolio.setBalance(100.0);
    portfolio.setCash(10.0);
    portfolio.setDividends(2.0);
    portfolio.setInterest(1.0);
    return portfolio;
  }

  private static PortfolioAssetAllocationEntity allocation(String value) {
    var row = new PortfolioAssetAllocationEntity();
    row.setTotalValueInBaseCurrency(value == null ? null : new BigDecimal(value));
    return row;
  }

  private static PortfolioCurrencyBreakdownEntity currency(CurrencyType currency, String value) {
    var row = new PortfolioCurrencyBreakdownEntity();
    row.setMetricType("ACCOUNT_LATEST");
    row.setCurrency(currency);
    row.setAmountInBaseCurrency(new BigDecimal(value));
    return row;
  }
}
