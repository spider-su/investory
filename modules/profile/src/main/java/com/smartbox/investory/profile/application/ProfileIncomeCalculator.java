package com.smartbox.investory.profile.application;

import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.investment.api.reporting.InvestmentIncomeSummaryReader.InvestmentIncomeSummary;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Pure income annualization and yield rules for the profile summary. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ProfileIncomeCalculator {
  private final ProfileCurrencyNormalizer currencyNormalizer;

  ProfileIncomeSummary calculate(
      BigDecimal marketIncome,
      BrokerageIncomeSnapshot snapshot,
      CurrencyType incomeCurrency,
      BigDecimal marketValue,
      BigDecimal longTermIncome,
      BigDecimal longTermInvestmentValue,
      BigDecimal totalInvestmentValue,
      CurrencyType base,
      LocalDate date) {
    BigDecimal projectedMarketIncome = annualize(marketIncome, snapshot, date);
    BigDecimal marketNetIncomeYtd = netOfProfitTax(marketIncome);
    BigDecimal marketNetAnnualIncome = netOfProfitTax(projectedMarketIncome);
    BigDecimal basis = marketIncomeBasis(snapshot, incomeCurrency, marketValue, base, date);
    BigDecimal combined = projectedMarketIncome.add(longTermIncome);
    return new ProfileIncomeSummary(
        marketIncome,
        projectedMarketIncome,
        ProfileIncomeSummary.ratio(projectedMarketIncome, basis),
        longTermIncome,
        ProfileIncomeSummary.ratio(longTermIncome, longTermInvestmentValue),
        combined,
        ProfileIncomeSummary.ratio(combined, totalInvestmentValue),
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        marketNetIncomeYtd,
        marketNetAnnualIncome,
        marketNetAnnualIncome.add(longTermIncome));
  }

  ProfileIncomeSummary calculate(
      InvestmentIncomeSummary market,
      BigDecimal longTermIncome,
      BigDecimal longTermInvestmentValue,
      BigDecimal totalInvestmentValue) {
    BigDecimal combined = market.expectedAnnualInvestmentResult().add(longTermIncome);
    BigDecimal marketNetIncomeYtd = netOfProfitTax(market.investmentResultYtd());
    BigDecimal marketNetAnnualIncome = netOfProfitTax(market.expectedAnnualInvestmentResult());
    return new ProfileIncomeSummary(
        market.investmentResultYtd(),
        market.expectedAnnualInvestmentResult(),
        market.expectedAnnualReturn(),
        longTermIncome,
        ProfileIncomeSummary.ratio(longTermIncome, longTermInvestmentValue),
        combined,
        ProfileIncomeSummary.ratio(combined, totalInvestmentValue),
        market.investmentBase(),
        market.expectedAnnualInvestmentResult(),
        market.expectedAnnualReturn(),
        market.investmentResultYtd(),
        market.expectedInvestmentResultYtd(),
        market.expectationProgress(),
        market.available(),
        marketNetIncomeYtd,
        marketNetAnnualIncome,
        marketNetAnnualIncome.add(longTermIncome));
  }

  private BigDecimal netOfProfitTax(BigDecimal gross) {
    return (gross == null ? BigDecimal.ZERO : gross)
        .multiply(BigDecimal.ONE.subtract(FinancialPolicyDefaults.GLOBAL_PROFIT_TAX_RATE));
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
    BigDecimal start = currencyNormalizer.toBase(snapshot.startValue(), sourceCurrency, base, date);
    BigDecimal end = currencyNormalizer.toBase(snapshot.endValue(), sourceCurrency, base, date);
    if (start.signum() > 0 && end.signum() > 0) {
      return start.add(end).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }
    if (end.signum() > 0) return end;
    if (start.signum() > 0) return start;
    return fallback;
  }
}
