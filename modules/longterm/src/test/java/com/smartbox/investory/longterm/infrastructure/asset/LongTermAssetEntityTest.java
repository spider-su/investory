package com.smartbox.investory.longterm.infrastructure.asset;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.shared.currency.CurrencyType;
import org.junit.jupiter.api.Test;

class LongTermAssetEntityTest {
  @Test
  void rejectsRelabelingPersistedMoneyWithAnotherCurrency() {
    var asset = new LongTermAssetEntity();
    asset.setCurrency(CurrencyType.PLN);
    asset.loaded();

    asset.setCurrency(CurrencyType.USD);

    assertThrows(IllegalArgumentException.class, asset::updated);
  }
}
