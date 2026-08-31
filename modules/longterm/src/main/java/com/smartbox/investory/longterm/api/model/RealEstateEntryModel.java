package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RealEstateEntryModel(
    String name,
    CurrencyType currency,
    LocalDate acquisitionDate,
    BigDecimal acquisitionValue,
    BigDecimal currentValue,
    BigDecimal taxBase,
    BigDecimal monthlyRent,
    BigDecimal monthlyParkingIncome,
    BigDecimal monthlyAdministrationCost,
    BigDecimal monthlyOtherCost,
    BigDecimal annualPropertyTax,
    BigDecimal annualInsurance,
    LocalDate effectiveFrom,
    BigDecimal expectedAnnualGrowthRate,
    String notes,
    boolean rentalTaxPaidByTenant) {
  public RealEstateEntryModel(
      String name,
      CurrencyType currency,
      LocalDate acquisitionDate,
      BigDecimal acquisitionValue,
      BigDecimal currentValue,
      BigDecimal monthlyRent,
      BigDecimal monthlyParkingIncome,
      BigDecimal monthlyAdministrationCost,
      BigDecimal monthlyOtherCost,
      BigDecimal annualPropertyTax,
      BigDecimal annualInsurance,
      LocalDate effectiveFrom,
      BigDecimal expectedAnnualGrowthRate,
      String notes) {
    this(
        name,
        currency,
        acquisitionDate,
        acquisitionValue,
        currentValue,
        null,
        monthlyRent,
        monthlyParkingIncome,
        monthlyAdministrationCost,
        monthlyOtherCost,
        annualPropertyTax,
        annualInsurance,
        effectiveFrom,
        expectedAnnualGrowthRate,
        notes,
        false);
  }

  public RealEstateEntryModel(
      String name,
      CurrencyType currency,
      LocalDate acquisitionDate,
      BigDecimal acquisitionValue,
      BigDecimal currentValue,
      BigDecimal taxBase,
      BigDecimal monthlyRent,
      BigDecimal monthlyParkingIncome,
      BigDecimal monthlyAdministrationCost,
      BigDecimal monthlyOtherCost,
      BigDecimal annualPropertyTax,
      BigDecimal annualInsurance,
      LocalDate effectiveFrom,
      BigDecimal expectedAnnualGrowthRate,
      String notes) {
    this(
        name,
        currency,
        acquisitionDate,
        acquisitionValue,
        currentValue,
        taxBase,
        monthlyRent,
        monthlyParkingIncome,
        monthlyAdministrationCost,
        monthlyOtherCost,
        annualPropertyTax,
        annualInsurance,
        effectiveFrom,
        expectedAnnualGrowthRate,
        notes,
        false);
  }
}
