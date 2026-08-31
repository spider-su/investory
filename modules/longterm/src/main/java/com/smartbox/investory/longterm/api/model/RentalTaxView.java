package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record RentalTaxView(Long id, LocalDate validFrom, LocalDate validTo, BigDecimal rate) {}
