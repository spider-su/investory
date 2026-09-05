package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Real-estate create/update HTML form. */
public record RealEstateForm(
    Long id,
    String name,
    CurrencyType currency,
    BigDecimal value,
    BigDecimal taxBase,
    LocalDate acquisitionDate,
    String landRegisterNumber,
    String notes) {}
