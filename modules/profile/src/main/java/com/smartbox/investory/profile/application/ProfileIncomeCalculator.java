package com.smartbox.investory.profile.application;

import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure income annualization and yield rules for the profile summary. */
final class ProfileIncomeCalculator {
  private final CurrencyConversion currencyRates;

  ProfileIncomeCalculator(CurrencyConversion currencyRates) {
    this.currencyRates = currencyRates;
  }

  ProfileIncomeSummary calculate(
      BigDecimal marketIncome,
      BrokerageIncomeSnapshot snapshot,
      CurrencyType incomeCurrency,
      BigDecimal marketValue,
      BigDecimal longTermIncome,
      BigDecimal longTermValue,
      BigDecimal total,
      CurrencyType base,
      LocalDate date) {
    BigDecimal projectedMarketIncome = annualize(marketIncome, snapshot, date);
    BigDecimal basis = marketIncomeBasis(snapshot, incomeCurrency, marketValue, base, date);
    BigDecimal combined = projectedMarketIncome.add(longTermIncome);
    return new ProfileIncomeSummary(
        marketIncome,
        projectedMarketIncome,
        ProfileIncomeSummary.ratio(projectedMarketIncome, basis),
        longTermIncome,
        ProfileIncomeSummary.ratio(longTermIncome, longTermValue),
        combined,
        ProfileIncomeSummary.ratio(combined, total));
  }

  private BigDecimal annualize(
      BigDecimal income, BrokerageIncomeSnapshot snapshot, LocalDate asOfDate) {
    LocalDate yearStart = LocalDate.of(asOfDate.getYear(), 1, 1);
    LocalDate start =
        snapshot == null || snapshot.periodStart() == null
            ? yearStart
            : snapshot.periodStart().isBefore(yearStart) ? yearStart : snapshot.periodStart();
    LocalDate end =
        snapshot == null || snapshot.periodEnd() == null || snapshot.periodEnd().isAfter(asOfDate)
            ? asOfDate
            : snapshot.periodEnd();
    if (end.isBefore(start)) return BigDecimal.ZERO;
    long observedDays = ChronoUnit.DAYS.between(start, end) + 1;
    return income
        .multiply(BigDecimal.valueOf(asOfDate.lengthOfYear()))
        .divide(BigDecimal.valueOf(observedDays), 8, RoundingMode.HALF_UP);
  }

  private BigDecimal marketIncomeBasis(
      BrokerageIncomeSnapshot snapshot,
      CurrencyType sourceCurrency,
      BigDecimal fallback,
      CurrencyType base,
      LocalDate date) {
    if (snapshot == null) return fallback;
    BigDecimal start = toBase(snapshot.startValue(), sourceCurrency, base, date);
    BigDecimal end = toBase(snapshot.endValue(), sourceCurrency, base, date);
    if (start.signum() > 0 && end.signum() > 0) {
      return start.add(end).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }
    if (end.signum() > 0) return end;
    if (start.signum() > 0) return start;
    return fallback;
  }

  private BigDecimal toBase(
      BigDecimal value, CurrencyType source, CurrencyType target, LocalDate date) {
    return value == null || source == target
        ? value == null ? BigDecimal.ZERO : value
        : currencyRates.convertToBaseCurrency(value, target, source, date);
  }
}
