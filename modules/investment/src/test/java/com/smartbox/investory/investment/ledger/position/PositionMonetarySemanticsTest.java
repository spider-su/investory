package com.smartbox.investory.investment.ledger.position;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Position Monetary Semantics")
class PositionMonetarySemanticsTest {

  @DisplayName("usd Asset In Usd Account Keeps All Explicit Currencies")
  @Test
  void usdAssetInUsdAccountKeepsAllExplicitCurrencies() {
    PositionEntity position = position(10L, 100L, PositionType.BUY, CurrencyType.USD);

    assertEquals(CurrencyType.USD, position.getPriceCurrency());
    assertEquals(CurrencyType.USD, position.getCostCurrency());
    assertEquals(CurrencyType.USD, position.getProfitCurrency());
    assertEquals(CurrencyType.USD, position.getCommissionCurrency());
    assertEquals(5.0, position.signedQuantity());
  }

  @DisplayName("usd Asset In Pln Account Keeps Purchase Value In Usd")
  @Test
  void usdAssetInPlnAccountKeepsPurchaseValueInUsd() {
    PositionEntity position = position(20L, 100L, PositionType.BUY, CurrencyType.USD);
    position.setPurchaseValue(java.math.BigDecimal.valueOf(500.0));

    assertEquals(20L, position.getAccount());
    assertEquals(100L, position.getAssetId());
    assertEquals(500.0, position.getPurchaseValue().doubleValue());
    assertEquals(CurrencyType.USD, position.getCostCurrency());
  }

  @DisplayName("same Asset In Different Account Currencies Keeps One Canonical Identity")
  @Test
  void sameAssetInDifferentAccountCurrenciesKeepsOneCanonicalIdentity() {
    PositionEntity usdAccount = position(10L, 100L, PositionType.BUY, CurrencyType.USD);
    PositionEntity plnAccount = position(20L, 100L, PositionType.BUY, CurrencyType.USD);

    assertEquals(usdAccount.getAssetId(), plnAccount.getAssetId());
    assertEquals(CurrencyType.USD, usdAccount.getCostCurrency());
    assertEquals(CurrencyType.USD, plnAccount.getCostCurrency());
  }

  @DisplayName("buy And Sell Use Canonical Signed Quantity")
  @Test
  void buyAndSellUseCanonicalSignedQuantity() {
    assertEquals(5.0, position(10L, 100L, PositionType.BUY, CurrencyType.USD).signedQuantity());
    assertEquals(-5.0, position(10L, 100L, PositionType.SELL, CurrencyType.USD).signedQuantity());
  }

  private static PositionEntity position(
      Long accountId, Long assetId, PositionType type, CurrencyType currency) {
    PositionEntity position = new PositionEntity();
    position.setAccount(accountId);
    position.setAssetId(assetId);
    position.setSymbol("AAPL.US");
    position.setSourceAssetSymbol("AAPL");
    position.setType(type);
    position.setVolume(java.math.BigDecimal.valueOf(5.0));
    position.setPriceCurrency(currency);
    position.setCostCurrency(currency);
    position.setProfitCurrency(currency);
    position.setCommissionCurrency(currency);
    return position;
  }
}
