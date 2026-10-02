package com.smartbox.investory.profile.application;

import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Converts Profile facts into the requested portfolio display currency. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ProfileCurrencyNormalizer {
  private final CurrencyConversion rates;

  BigDecimal toBase(BigDecimal value, CurrencyType source, CurrencyType base, LocalDate date) {
    Objects.requireNonNull(value, "Profile monetary value");
    Objects.requireNonNull(source, "Profile monetary source currency");
    Objects.requireNonNull(base, "Profile monetary target currency");
    return source == base ? value : rates.convertToBaseCurrency(value, base, source, date);
  }
}
