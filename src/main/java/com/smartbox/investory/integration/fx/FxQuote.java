package com.smartbox.investory.integration.fx;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;

public record FxQuote(
    CurrencyType base, CurrencyType target, double rate, LocalDate providerDate, String source) {}
