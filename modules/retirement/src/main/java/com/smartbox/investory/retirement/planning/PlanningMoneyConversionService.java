package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Currency-only boundary used by planning presentation models. */
@Service
public class PlanningMoneyConversionService {
  private static final CurrencyType CANONICAL = CurrencyType.USD;
  private final CurrencyConversion rates;
  private final Clock clock;

  public PlanningMoneyConversionService(CurrencyConversion rates, Clock clock) {
    this.rates = rates;
    this.clock = clock;
  }

  public BigDecimal toDisplay(BigDecimal canonical, CurrencyType display) {
    return canonical == null || display == CANONICAL
        ? canonical
        : rates.convertToBaseCurrency(canonical, display, CANONICAL, LocalDate.now(clock));
  }

  public BigDecimal toDisplay(BigDecimal amount, CurrencyType source, CurrencyType display) {
    return amount == null || source == display
        ? amount
        : rates.convertToBaseCurrency(amount, display, source, LocalDate.now(clock));
  }

  public BigDecimal fromDisplay(BigDecimal amount, CurrencyType display, BigDecimal fallback) {
    return amount == null
        ? fallback
        : display == CANONICAL
            ? amount
            : rates.convertToBaseCurrency(amount, CANONICAL, display, LocalDate.now(clock));
  }
}
