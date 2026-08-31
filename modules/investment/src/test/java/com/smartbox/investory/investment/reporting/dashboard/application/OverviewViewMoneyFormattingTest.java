package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.investment.api.reporting.model.OverviewView;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Overview View Money Formatting")
class OverviewViewMoneyFormattingTest {

  @DisplayName("base Currency Values Do Not Repeat Currency And Original Currency Uses Iso Code")
  @Test
  void baseCurrencyValuesDoNotRepeatCurrencyAndOriginalCurrencyUsesIsoCode() {
    OverviewView usd = view(CurrencyType.USD);

    assertEquals("148,851", usd.formatBase(148851.0));
    assertEquals("53,754 PLN", usd.formatMoney(53754.0, CurrencyType.PLN));
    assertEquals("22,686 EUR", usd.formatMoney(22686.0, CurrencyType.EUR));
  }

  @DisplayName("changing Base Currency Changes Which Currency Is Implicit")
  @Test
  void changingBaseCurrencyChangesWhichCurrencyIsImplicit() {
    OverviewView eur = view(CurrencyType.EUR);

    assertEquals("1,420,354", eur.formatMoney(1420354.0, CurrencyType.EUR));
    assertEquals("1,420,354 USD", eur.formatMoney(1420354.0, CurrencyType.USD));
  }

  private OverviewView view(CurrencyType baseCurrency) {
    return new OverviewView(
        0,
        0,
        0,
        null,
        0,
        null,
        0,
        null,
        null,
        List.of(),
        null,
        Map.of(),
        baseCurrency,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
