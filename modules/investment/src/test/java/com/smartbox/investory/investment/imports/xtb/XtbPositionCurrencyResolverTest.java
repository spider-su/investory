package com.smartbox.investory.investment.imports.xtb;

import static org.junit.jupiter.api.Assertions.*;

import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Xtb Position Currency Resolver")
class XtbPositionCurrencyResolverTest {

  private final XtbPositionCurrencyResolver resolver = new XtbPositionCurrencyResolver();

  @DisplayName("resolves Non Account Quote Currency From Broker Conversion Evidence")
  @Test
  void resolvesNonAccountQuoteCurrencyFromBrokerConversionEvidence() {
    PositionEntity position = position(5.0, 53.25, 55.0, 988.32, 985.33, 3.712, 3.5830181818);

    XtbPositionCurrencyResolver.Resolution result =
        resolver.resolve(position, CurrencyType.PLN, CurrencyType.PLN, CurrencyType.USD);

    assertEquals(CurrencyType.USD, result.priceCurrency());
    assertFalse(result.normalizePricesToAccountCurrency());
  }

  @DisplayName("resolves Same Currency When Broker Rate Is One")
  @Test
  void resolvesSameCurrencyWhenBrokerRateIsOne() {
    PositionEntity position = position(5.0, 53.25, 55.0, 266.25, 275.0, 1.0, 1.0);

    XtbPositionCurrencyResolver.Resolution result =
        resolver.resolve(position, CurrencyType.USD, CurrencyType.PLN, CurrencyType.USD);

    assertEquals(CurrencyType.USD, result.priceCurrency());
    assertFalse(result.normalizePricesToAccountCurrency());
  }

  @DisplayName("rejects Broker Value That Contradicts Conversion Rate")
  @Test
  void rejectsBrokerValueThatContradictsConversionRate() {
    PositionEntity position = position(5.0, 53.25, 55.0, 500.0, 985.33, 3.712, 3.5830181818);

    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolve(position, CurrencyType.PLN, CurrencyType.PLN, CurrencyType.USD));
  }

  @DisplayName("requests Account Currency Normalization For Unsupported Quote Currency")
  @Test
  void requestsAccountCurrencyNormalizationForUnsupportedQuoteCurrency() {
    PositionEntity position = position(2.0, 551.0, 560.0, 100.0, 102.0, 0.0907441, 0.0910714);

    XtbPositionCurrencyResolver.Resolution result =
        resolver.resolve(position, CurrencyType.USD, CurrencyType.USD, null);

    assertEquals(CurrencyType.USD, result.priceCurrency());
    assertTrue(result.normalizePricesToAccountCurrency());
  }

  private PositionEntity position(
      double volume,
      double openPrice,
      double closePrice,
      double purchaseValue,
      double saleValue,
      double openRate,
      double closeRate) {
    PositionEntity position = new PositionEntity();
    position.setAccount(51729109L);
    position.setSymbol("NCLR.UK");
    position.setSourcePositionId("123");
    position.setVolume(java.math.BigDecimal.valueOf(volume));
    position.setOpenPrice(java.math.BigDecimal.valueOf(openPrice));
    position.setClosePrice(java.math.BigDecimal.valueOf(closePrice));
    position.setPurchaseValue(java.math.BigDecimal.valueOf(purchaseValue));
    position.setSaleValue(java.math.BigDecimal.valueOf(saleValue));
    position.setOpenConversionRate(java.math.BigDecimal.valueOf(openRate));
    position.setCloseConversionRate(java.math.BigDecimal.valueOf(closeRate));
    return position;
  }
}
