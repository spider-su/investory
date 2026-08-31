package com.smartbox.investory.investment.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import com.smartbox.investory.investment.ledger.position.persistence.ClosedPosition;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioBuilders;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxCalculatorTest {

  @Mock private CurrencyRateService currencyRateService;

  private TaxCalculator taxCalculator;

  @BeforeEach
  void setUp() {
    taxCalculator = new TaxCalculator(currencyRateService);
    // lenient() because the empty-trades test never triggers FX conversion.
    org.mockito.Mockito.lenient()
        .when(
            currencyRateService.convertToBaseCurrency(
                any(BigDecimal.class), any(), any(), any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));
  }

  @Test
  void calculate_returnsZeroWhenThereAreNoTrades() {
    TaxCalculator.TaxSummary tax = taxCalculator.calculate(List.of(), CurrencyType.USD, 2026);
    assertEquals(new BigDecimal("0.00"), tax.capitalGainsTax());
    assertEquals(new BigDecimal("0.00"), tax.lossCarryForward());
  }

  @Test
  void calculate_appliesNineteenPercentToCurrentYearNetGains() {
    TaxCalculator.TaxSummary tax =
        taxCalculator.calculate(List.of(closed(1000.0, 0.0, 0.0, 2026)), CurrencyType.USD, 2026);

    assertEquals(new BigDecimal("190.00"), tax.capitalGainsTax());
    assertEquals(new BigDecimal("0.00"), tax.lossCarryForward());
  }

  @Test
  void calculate_consumesPriorYearLossesAgainstCurrentYearGains() {
    TaxCalculator.TaxSummary tax =
        taxCalculator.calculate(
            List.of(closed(-400.0, 0.0, 0.0, 2024), closed(1000.0, 0.0, 0.0, 2026)),
            CurrencyType.USD,
            2026);

    // Gain (1000) - applied loss (400) = 600 taxable -> 19% = 114.
    assertEquals(new BigDecimal("114.00"), tax.capitalGainsTax());
    assertEquals(new BigDecimal("400.00"), tax.lossCarryForward());
  }

  @Test
  void calculate_ignoresLossesOlderThanFiveYears() {
    TaxCalculator.TaxSummary tax =
        taxCalculator.calculate(
            List.of(closed(-1000.0, 0.0, 0.0, 2018), closed(500.0, 0.0, 0.0, 2026)),
            CurrencyType.USD,
            2026);

    // 2018 loss is outside the 5-year window for 2026 (2026 - 5 = 2021).
    assertEquals(new BigDecimal("95.00"), tax.capitalGainsTax());
    assertEquals(new BigDecimal("0.00"), tax.lossCarryForward());
  }

  @Test
  void calculate_returnsZeroTaxWhenCurrentYearIsNetLoss() {
    TaxCalculator.TaxSummary tax =
        taxCalculator.calculate(List.of(closed(-500.0, 0.0, 0.0, 2026)), CurrencyType.USD, 2026);

    assertEquals(new BigDecimal("0.00"), tax.capitalGainsTax());
  }

  private static ClosedPosition closed(
      double profit, double commission, double swap, int closeYear) {
    return PortfolioBuilders.closedPosition(PortfolioTestData.AAPL)
        .profit(profit)
        .commission(commission)
        .swap(swap)
        .closeOn(LocalDate.of(closeYear, 12, 31))
        .build();
  }
}
