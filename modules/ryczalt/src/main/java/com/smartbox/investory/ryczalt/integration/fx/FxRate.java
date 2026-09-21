package com.smartbox.investory.ryczalt.integration.fx;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A persisted or newly acquired historical FX fact. */
public record FxRate(
    String currency,
    LocalDate requestedDate,
    LocalDate effectiveDate,
    BigDecimal rate,
    String provider,
    String providerReference) {}
