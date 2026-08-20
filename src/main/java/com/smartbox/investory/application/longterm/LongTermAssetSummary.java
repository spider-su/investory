package com.smartbox.investory.application.longterm;

import com.smartbox.investory.infrastructure.longterm.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Common asset facts plus normalized annual economics and type-specific planning facts. */
public record LongTermAssetSummary(
    Long id,
    String name,
    LongTermAssetType type,
    CurrencyType currency,
    BigDecimal currentValue,
    LocalDate maturityDate,
    BigDecimal currentAnnualRate,
    AnnualEconomics annualEconomics,
    RealEstatePlanningSummary realEstatePlanning,
    BondPlanningSummary bondPlanning,
    LocalDate rentEnd) {

  /** Compatibility constructor for callers that do not need overview rent-end data. */
  public LongTermAssetSummary(
      Long id,
      String name,
      LongTermAssetType type,
      CurrencyType currency,
      BigDecimal currentValue,
      LocalDate maturityDate,
      BigDecimal currentAnnualRate,
      AnnualEconomics annualEconomics,
      RealEstatePlanningSummary realEstatePlanning,
      BondPlanningSummary bondPlanning) {
    this(
        id,
        name,
        type,
        currency,
        currentValue,
        maturityDate,
        currentAnnualRate,
        annualEconomics,
        realEstatePlanning,
        bondPlanning,
        null);
  }

  /** Temporary constructor for tests and external callers migrating from the flat read model. */
  @Deprecated
  public LongTermAssetSummary(
      Long id,
      String name,
      LongTermAssetType type,
      CurrencyType currency,
      BigDecimal currentValue,
      BigDecimal grossAnnualIncome,
      BigDecimal annualExpenses,
      BigDecimal netAnnualIncomeBeforeTax,
      BigDecimal estimatedAnnualTax,
      BigDecimal netAnnualIncomeAfterTax,
      BigDecimal grossYield,
      BigDecimal netYieldBeforeTax,
      BigDecimal netYieldAfterTax,
      LocalDate maturityDate,
      BigDecimal currentAnnualRate) {
    this(
        id,
        name,
        type,
        currency,
        currentValue,
        maturityDate,
        currentAnnualRate,
        new AnnualEconomics(
            grossAnnualIncome,
            annualExpenses,
            estimatedAnnualTax,
            netAnnualIncomeBeforeTax,
            netAnnualIncomeAfterTax,
            grossYield,
            netYieldBeforeTax,
            netYieldAfterTax),
        null,
        null,
        null);
  }

  /** Temporary constructor for callers migrating real-estate planning data. */
  @Deprecated
  public LongTermAssetSummary(
      Long id,
      String name,
      LongTermAssetType type,
      CurrencyType currency,
      BigDecimal currentValue,
      BigDecimal grossAnnualIncome,
      BigDecimal annualExpenses,
      BigDecimal netAnnualIncomeBeforeTax,
      BigDecimal estimatedAnnualTax,
      BigDecimal netAnnualIncomeAfterTax,
      BigDecimal grossYield,
      BigDecimal netYieldBeforeTax,
      BigDecimal netYieldAfterTax,
      LocalDate maturityDate,
      BigDecimal currentAnnualRate,
      RealEstatePlanningSummary realEstatePlanning) {
    this(
        id,
        name,
        type,
        currency,
        currentValue,
        maturityDate,
        currentAnnualRate,
        new AnnualEconomics(
            grossAnnualIncome,
            annualExpenses,
            estimatedAnnualTax,
            netAnnualIncomeBeforeTax,
            netAnnualIncomeAfterTax,
            grossYield,
            netYieldBeforeTax,
            netYieldAfterTax),
        realEstatePlanning,
        null,
        null);
  }

  /**
   * @deprecated Use {@link #annualEconomics()}.
   */
  @Deprecated
  public BigDecimal grossAnnualIncome() {
    return annualEconomics.grossAnnualIncome();
  }

  /**
   * @deprecated Use {@link #annualEconomics()}.
   */
  @Deprecated
  public BigDecimal annualExpenses() {
    return annualEconomics.annualExpenses();
  }

  /**
   * @deprecated Use {@link #annualEconomics()}.
   */
  @Deprecated
  public BigDecimal netAnnualIncomeBeforeTax() {
    return annualEconomics.netAnnualIncomeBeforeTax();
  }

  /**
   * @deprecated Use {@link #annualEconomics()}.
   */
  @Deprecated
  public BigDecimal estimatedAnnualTax() {
    return annualEconomics.annualTax();
  }

  /**
   * @deprecated Use {@link #annualEconomics()}.
   */
  @Deprecated
  public BigDecimal netAnnualIncomeAfterTax() {
    return annualEconomics.netAnnualIncomeAfterTax();
  }

  /**
   * @deprecated Use {@link #annualEconomics()}.
   */
  @Deprecated
  public BigDecimal grossYield() {
    return annualEconomics.grossYield();
  }

  /**
   * @deprecated Use {@link #annualEconomics()}.
   */
  @Deprecated
  public BigDecimal netYieldBeforeTax() {
    return annualEconomics.netYieldBeforeTax();
  }

  /**
   * @deprecated Use {@link #annualEconomics()}.
   */
  @Deprecated
  public BigDecimal netYieldAfterTax() {
    return annualEconomics.netYieldAfterTax();
  }
}
