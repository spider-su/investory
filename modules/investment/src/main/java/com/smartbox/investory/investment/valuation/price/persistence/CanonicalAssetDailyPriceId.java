package com.smartbox.investory.investment.valuation.price.persistence;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CanonicalAssetDailyPriceId implements Serializable {
  private Long assetId;
  private LocalDate priceDate;
}
