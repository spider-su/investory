package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Real-estate create/update HTML form. */
public record RealEstateForm(
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
    String notes) {}
