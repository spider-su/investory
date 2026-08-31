package com.smartbox.investory.application.longterm;

import com.smartbox.investory.infrastructure.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RealEstateEntry(
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
  public RealEstateEntry(
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
        notes);
  }
}
