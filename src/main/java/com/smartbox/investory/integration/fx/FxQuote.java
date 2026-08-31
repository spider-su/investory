package com.smartbox.investory.integration.fx;

import com.smartbox.investory.infrastructure.CurrencyType;
import java.time.LocalDate;

public record FxQuote(
    CurrencyType base, CurrencyType target, double rate, LocalDate providerDate, String source) {}
