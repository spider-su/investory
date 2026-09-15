package com.smartbox.investory.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.investment.api.reporting.InvestmentIncomeSummaryReader.InvestmentIncomeSummary;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProfileIncomeCalculatorTest {
  private static final LocalDate AS_OF = LocalDate.of(2026, 6, 1);

  @Test
  void passesInvestmentOwnedProjectionAndAssumptionThroughUnchanged() {
    var market =
        new InvestmentIncomeSummary(
            true,
            CurrencyType.USD,
            new BigDecimal("143900"),
            new BigDecimal("10073"),
            new BigDecimal("0.07"),
            new BigDecimal("1200"),
            new BigDecimal("5036.50"),
            new BigDecimal("0.238"));

    var result =
        new ProfileIncomeCalculator(new ProfileCurrencyNormalizer(mock()))
            .calculate(market, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("143900"));

    assertThat(result.investmentIncomeBase()).isEqualByComparingTo("143900");
    assertThat(result.expectedAnnualInvestmentResult()).isEqualByComparingTo("10073");
    assertThat(result.expectedAnnualReturn()).isEqualByComparingTo("0.07");
    assertThat(result.investmentResultYtd()).isEqualByComparingTo("1200");
  }

  @Test
  void annualizesCalendarIncomeAndCombinesLongTermIncome() {
    var snapshot =
        new BrokerageIncomeSnapshot(
            CurrencyType.USD,
            LocalDate.of(2026, 1, 1),
            AS_OF,
            new BigDecimal("1800"),
            new BigDecimal("2200"),
            new BigDecimal("100"),
            new BigDecimal("20"),
            new BigDecimal("10"));

    ProfileIncomeSummary result =
        new ProfileIncomeCalculator(new ProfileCurrencyNormalizer(mock()))
            .calculate(
                new BigDecimal("110"),
                snapshot,
                CurrencyType.USD,
                new BigDecimal("2000"),
                new BigDecimal("260"),
                new BigDecimal("3000"),
                new BigDecimal("5000"),
                CurrencyType.USD,
                AS_OF);

    assertThat(result.marketAnnualIncome()).isEqualByComparingTo("264.14473684");
    assertThat(result.combinedAnnualIncome()).isEqualByComparingTo("524.14473684");
    assertThat(result.marketNetYield()).isEqualByComparingTo("0.13207237");
    assertThat(result.combinedNetYield()).isEqualByComparingTo("0.10482895");
  }

  @Test
  void clipsIncomePeriodToCurrentCalendarYearAndObservationDate() {
    var snapshot =
        new BrokerageIncomeSnapshot(
            CurrencyType.USD,
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2026, 12, 31),
            new BigDecimal("1000"),
            new BigDecimal("1000"),
            new BigDecimal("100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    var result =
        new ProfileIncomeCalculator(new ProfileCurrencyNormalizer(mock()))
            .calculate(
                new BigDecimal("100"),
                snapshot,
                CurrencyType.USD,
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                CurrencyType.USD,
                AS_OF);

    assertThat(result.marketAnnualIncome()).isEqualByComparingTo("240.13157895");
  }

  @Test
  void returnsZeroAnnualIncomeForAnInvalidObservedPeriod() {
    var snapshot =
        new BrokerageIncomeSnapshot(
            CurrencyType.USD,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    var result =
        new ProfileIncomeCalculator(new ProfileCurrencyNormalizer(mock()))
            .calculate(
                new BigDecimal("100"),
                snapshot,
                CurrencyType.USD,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CurrencyType.USD,
                AS_OF);

    assertThat(result.marketAnnualIncome()).isZero();
  }

  @Test
  void convertsIncomeBasisAndFallsBackToOneAvailableBoundaryValue() {
    var rates = mock(com.smartbox.investory.shared.currency.CurrencyConversion.class);
    when(rates.convertToBaseCurrency(
            eq(new BigDecimal("1000")), eq(CurrencyType.USD), eq(CurrencyType.EUR), eq(AS_OF)))
        .thenReturn(new BigDecimal("1100"));
    when(rates.convertToBaseCurrency(
            eq(BigDecimal.ZERO), eq(CurrencyType.USD), eq(CurrencyType.EUR), eq(AS_OF)))
        .thenReturn(BigDecimal.ZERO);
    var snapshot =
        new BrokerageIncomeSnapshot(
            CurrencyType.EUR,
            AS_OF,
            AS_OF,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    var result =
        new ProfileIncomeCalculator(new ProfileCurrencyNormalizer(rates))
            .calculate(
                new BigDecimal("110"),
                snapshot,
                CurrencyType.EUR,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1100"),
                CurrencyType.USD,
                AS_OF);

    assertThat(result.marketNetYield()).isEqualByComparingTo("36.5");
  }
}
