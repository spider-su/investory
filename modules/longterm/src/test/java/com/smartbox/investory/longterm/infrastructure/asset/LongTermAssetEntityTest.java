package com.smartbox.investory.longterm.infrastructure.asset;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.shared.currency.CurrencyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Long Term Asset Entity")
class LongTermAssetEntityTest {
  @DisplayName("rejects Relabeling Persisted Money With Another Currency")
  @Test
  void rejectsRelabelingPersistedMoneyWithAnotherCurrency() {
    var asset = new LongTermAssetEntity();
    asset.setCurrency(CurrencyType.PLN);
    asset.loaded();

    asset.setCurrency(CurrencyType.USD);

    assertThrows(IllegalArgumentException.class, asset::updated);
  }
}
