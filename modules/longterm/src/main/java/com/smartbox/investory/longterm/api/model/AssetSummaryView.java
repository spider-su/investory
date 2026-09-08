package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record AssetSummaryView(
    Long id,
    String name,
    LongTermAssetType type,
    CurrencyType currency,
    BigDecimal currentValue,
    LocalDate maturityDate,
    BigDecimal currentAnnualRate,
    AnnualEconomicsView annualEconomics,
    BigDecimal totalPaymentMonthly,
    LocalDate rentEnd,
    boolean integrityWarning) {
  public AssetSummaryView(
      Long id,
      String name,
      LongTermAssetType type,
      CurrencyType currency,
      BigDecimal currentValue,
      LocalDate maturityDate,
      BigDecimal currentAnnualRate,
      AnnualEconomicsView annualEconomics,
      BigDecimal totalPaymentMonthly,
      LocalDate rentEnd) {
    this(
        id,
        name,
        type,
        currency,
        currentValue,
        maturityDate,
        currentAnnualRate,
        annualEconomics,
        totalPaymentMonthly,
        rentEnd,
        false);
  }

  public AssetSummaryView(
      Long id,
      String name,
      LongTermAssetType type,
      CurrencyType currency,
      BigDecimal currentValue,
      LocalDate maturityDate,
      BigDecimal currentAnnualRate,
      AnnualEconomicsView annualEconomics,
      LocalDate rentEnd) {
    this(
        id,
        name,
        type,
        currency,
        currentValue,
        maturityDate,
        currentAnnualRate,
        annualEconomics,
        BigDecimal.ZERO,
        rentEnd,
        false);
  }
}
