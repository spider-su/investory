package com.smartbox.investory.services.imports.xtb;

import static org.junit.jupiter.api.Assertions.*;

import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.infrastructure.repository.ClosedPosition;
import org.junit.jupiter.api.Test;

class XtbPositionCurrencyResolverTest {

  private final XtbPositionCurrencyResolver resolver = new XtbPositionCurrencyResolver();

  @Test
  void resolvesNonAccountQuoteCurrencyFromBrokerConversionEvidence() {
    ClosedPosition position = position(5.0, 53.25, 55.0, 988.32, 985.33, 3.712, 3.5830181818);

    XtbPositionCurrencyResolver.Resolution result =
        resolver.resolve(position, CurrencyType.PLN, CurrencyType.PLN, CurrencyType.USD);

    assertEquals(CurrencyType.USD, result.priceCurrency());
    assertFalse(result.normalizePricesToAccountCurrency());
  }

  @Test
  void resolvesSameCurrencyWhenBrokerRateIsOne() {
    ClosedPosition position = position(5.0, 53.25, 55.0, 266.25, 275.0, 1.0, 1.0);

    XtbPositionCurrencyResolver.Resolution result =
        resolver.resolve(position, CurrencyType.USD, CurrencyType.PLN, CurrencyType.USD);

    assertEquals(CurrencyType.USD, result.priceCurrency());
    assertFalse(result.normalizePricesToAccountCurrency());
  }

  @Test
  void rejectsBrokerValueThatContradictsConversionRate() {
    ClosedPosition position = position(5.0, 53.25, 55.0, 500.0, 985.33, 3.712, 3.5830181818);

    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolve(position, CurrencyType.PLN, CurrencyType.PLN, CurrencyType.USD));
  }

  @Test
  void requestsAccountCurrencyNormalizationForUnsupportedQuoteCurrency() {
    ClosedPosition position = position(2.0, 551.0, 560.0, 100.0, 102.0, 0.0907441, 0.0910714);

    XtbPositionCurrencyResolver.Resolution result =
        resolver.resolve(position, CurrencyType.USD, CurrencyType.USD, null);

    assertEquals(CurrencyType.USD, result.priceCurrency());
    assertTrue(result.normalizePricesToAccountCurrency());
  }

  private ClosedPosition position(
      double volume,
      double openPrice,
      double closePrice,
      double purchaseValue,
      double saleValue,
      double openRate,
      double closeRate) {
    ClosedPosition position = new ClosedPosition();
    position.setAccount(51729109L);
    position.setSymbol("NCLR.UK");
    position.setSourcePositionId("123");
    position.setVolume(volume);
    position.setOpenPrice(openPrice);
    position.setClosePrice(closePrice);
    position.setPurchaseValue(purchaseValue);
    position.setSaleValue(saleValue);
    position.setOpenConversionRate(openRate);
    position.setCloseConversionRate(closeRate);
    return position;
  }
}
