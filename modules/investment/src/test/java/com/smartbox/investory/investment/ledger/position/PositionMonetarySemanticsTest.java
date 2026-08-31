package com.smartbox.investory.investment.ledger.position;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.investment.ledger.position.persistence.OpenedPosition;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.junit.jupiter.api.Test;

class PositionMonetarySemanticsTest {

  @Test
  void usdAssetInUsdAccountKeepsAllExplicitCurrencies() {
    OpenedPosition position = position(10L, 100L, PositionType.BUY, CurrencyType.USD);

    assertEquals(CurrencyType.USD, position.getPriceCurrency());
    assertEquals(CurrencyType.USD, position.getCostCurrency());
    assertEquals(CurrencyType.USD, position.getProfitCurrency());
    assertEquals(CurrencyType.USD, position.getCommissionCurrency());
    assertEquals(5.0, position.signedQuantity());
  }

  @Test
  void usdAssetInPlnAccountKeepsPurchaseValueInUsd() {
    OpenedPosition position = position(20L, 100L, PositionType.BUY, CurrencyType.USD);
    position.setPurchaseValue(500.0);

    assertEquals(20L, position.getAccount());
    assertEquals(100L, position.getAssetId());
    assertEquals(500.0, position.getPurchaseValue());
    assertEquals(CurrencyType.USD, position.getCostCurrency());
  }

  @Test
  void sameAssetInDifferentAccountCurrenciesKeepsOneCanonicalIdentity() {
    OpenedPosition usdAccount = position(10L, 100L, PositionType.BUY, CurrencyType.USD);
    OpenedPosition plnAccount = position(20L, 100L, PositionType.BUY, CurrencyType.USD);

    assertEquals(usdAccount.getAssetId(), plnAccount.getAssetId());
    assertEquals(CurrencyType.USD, usdAccount.getCostCurrency());
    assertEquals(CurrencyType.USD, plnAccount.getCostCurrency());
  }

  @Test
  void buyAndSellUseCanonicalSignedQuantity() {
    assertEquals(5.0, position(10L, 100L, PositionType.BUY, CurrencyType.USD).signedQuantity());
    assertEquals(-5.0, position(10L, 100L, PositionType.SELL, CurrencyType.USD).signedQuantity());
  }

  private static OpenedPosition position(
      Long accountId, Long assetId, PositionType type, CurrencyType currency) {
    OpenedPosition position = new OpenedPosition();
    position.setAccount(accountId);
    position.setAssetId(assetId);
    position.setSymbol("AAPL.US");
    position.setSourceAssetSymbol("AAPL");
    position.setType(type);
    position.setVolume(5.0);
    position.setPriceCurrency(currency);
    position.setCostCurrency(currency);
    position.setProfitCurrency(currency);
    position.setCommissionCurrency(currency);
    return position;
  }
}
