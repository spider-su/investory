package com.smartbox.investory.profile.application;

import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Converts optional Profile facts into the portfolio base currency. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ProfileCurrencyNormalizer {
  private final CurrencyConversion rates;

  BigDecimal toBase(BigDecimal value, CurrencyType source, CurrencyType base, LocalDate date) {
    if (value == null) return BigDecimal.ZERO;
    return source == base ? value : rates.convertToBaseCurrency(value, base, source, date);
  }
}
