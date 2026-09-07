package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongTermAssetEconomicsTest {
  @Test
  void rentalSeparatesIncomeLandlordExpensesTenantPaymentsAndAnnualTaxBase() {
    var result =
        LongTermAssetEconomics.rental(
            List.of(
                term(CashFlowType.RENT, "100", Frequency.MONTHLY, false),
                term(CashFlowType.PARKING_RENT, "120", Frequency.ANNUAL, false),
                term(CashFlowType.OTHER_INCOME, "12", Frequency.ANNUAL, false),
                term(CashFlowType.ADMIN_FEE, "30", Frequency.MONTHLY, false),
                term(CashFlowType.UTILITIES, "10", Frequency.MONTHLY, true),
                term(CashFlowType.OTHER_EXPENSE, "120", Frequency.ANNUAL, false)),
            new BigDecimal("2400"),
            new BigDecimal("10000"));

    assertThat(result.economics().grossAnnualIncome()).isEqualByComparingTo("1332");
    assertThat(result.economics().annualExpenses()).isEqualByComparingTo("480");
    assertThat(result.economics().annualTax()).isEqualByComparingTo("204");
    assertThat(result.economics().monthlyTaxBase()).isEqualByComparingTo("200");
    assertThat(result.economics().monthlyTax()).isEqualByComparingTo("17");
    assertThat(result.economics().netAnnualIncomeAfterTax()).isEqualByComparingTo("648");
    assertThat(result.monthlyPayment()).isEqualByComparingTo("121");
  }

  @Test
  void zeroValueProducesSafeZeroYieldsWithoutChangingIncomeOrTax() {
    var normal =
        LongTermAssetEconomics.economics(
            new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("1000"));
    var zeroValue =
        LongTermAssetEconomics.economics(new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);

    assertThat(normal.annualTax()).isEqualByComparingTo("19");
    assertThat(normal.netAnnualIncomeAfterTax()).isEqualByComparingTo("71");
    assertThat(normal.netYieldAfterTax()).isEqualByComparingTo("0.071");
    assertThat(zeroValue.grossAnnualIncome()).isEqualByComparingTo("100");
    assertThat(zeroValue.annualTax()).isEqualByComparingTo("19");
    assertThat(zeroValue.netAnnualIncomeAfterTax()).isEqualByComparingTo("81");
    assertThat(zeroValue.grossYield()).isZero();
    assertThat(zeroValue.netYieldBeforeTax()).isZero();
    assertThat(zeroValue.netYieldAfterTax()).isZero();
  }

  @Test
  void calendarAccrualUsesFullMonthsAndProratesPartialPeriods() {
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                new BigDecimal("100"),
                Frequency.MONTHLY,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 6, 30)))
        .isEqualByComparingTo("600");
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                new BigDecimal("310"),
                Frequency.MONTHLY,
                LocalDate.of(2025, 1, 16),
                LocalDate.of(2025, 1, 31)))
        .isEqualByComparingTo("160");
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                new BigDecimal("365"),
                Frequency.ANNUAL,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 6, 30)))
        .isEqualByComparingTo("181");
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                BigDecimal.TEN,
                Frequency.MONTHLY,
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 1, 31)))
        .isZero();
  }

  private static RentalContractModel.Term term(
      CashFlowType type, String amount, Frequency frequency, boolean paidByTenant) {
    return new RentalContractModel.Term(type, new BigDecimal(amount), frequency, paidByTenant);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(CashFlowType.class)
  void everyTermCategorySeparatesTenantAndLandlordObligations(CashFlowType type) {
    boolean income =
        java.util.Set.of(CashFlowType.RENT, CashFlowType.PARKING_RENT, CashFlowType.OTHER_INCOME)
            .contains(type);
    for (Frequency frequency : Frequency.values()) {
      var landlord =
          LongTermAssetEconomics.rental(
              List.of(term(type, "120", frequency, false)),
              BigDecimal.ZERO,
              new BigDecimal("10000"));
      var tenant =
          LongTermAssetEconomics.rental(
              List.of(term(type, "120", frequency, true)),
              BigDecimal.ZERO,
              new BigDecimal("10000"));
      String annual = frequency == Frequency.MONTHLY ? "1440" : "120";
      String monthly = frequency == Frequency.MONTHLY ? "120" : "10";
      assertThat(landlord.economics().grossAnnualIncome())
          .isEqualByComparingTo(income ? annual : "0");
      assertThat(landlord.economics().annualExpenses()).isEqualByComparingTo(income ? "0" : annual);
      assertThat(tenant.economics().annualExpenses()).isZero();
      assertThat(tenant.monthlyPayment()).isEqualByComparingTo(monthly);
      assertThat(landlord.monthlyPayment()).isEqualByComparingTo(income ? monthly : "0");
    }
  }

  @Test
  void accrualRespectsLeapYearAndBothCalendarYearDenominators() {
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                new BigDecimal("290"),
                Frequency.MONTHLY,
                LocalDate.of(2024, 2, 15),
                LocalDate.of(2024, 2, 29)))
        .isEqualByComparingTo("150");
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                new BigDecimal("366"),
                Frequency.ANNUAL,
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 2, 29)))
        .isEqualByComparingTo("1");
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                new BigDecimal("100"),
                Frequency.ANNUAL,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 12, 31)))
        .isEqualByComparingTo("200");
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                null, Frequency.MONTHLY, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
        .isZero();
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                BigDecimal.TEN, Frequency.MONTHLY, null, LocalDate.of(2025, 1, 31)))
        .isZero();
    assertThat(
            LongTermAssetEconomics.accruedAmount(
                BigDecimal.TEN, Frequency.MONTHLY, LocalDate.of(2025, 1, 31), null))
        .isZero();
  }

  @Test
  void vacantPropertyStillExposesDerivedMonthlyTaxValuesAndNegativeNetIncome() {
    var result =
        LongTermAssetEconomics.rental(List.of(), new BigDecimal("1000"), new BigDecimal("100000"));
    assertThat(result.economics().annualTax()).isEqualByComparingTo("85");
    assertThat(result.economics().monthlyTaxBase()).isEqualByComparingTo("83.333333333333");
    assertThat(result.economics().monthlyTax()).isEqualByComparingTo("7.083333333333");
    assertThat(result.economics().netAnnualIncomeAfterTax()).isEqualByComparingTo("-85");
    assertThat(result.economics().monthlyNetIncomeAfterTax())
        .isEqualByComparingTo("-7.083333333333");
    assertThat(result.economics().netYieldAfterTax()).isEqualByComparingTo("-0.00085");
    assertThat(result.monthlyPayment()).isZero();
  }

  @Test
  void nullAnnualTaxBaseMeansZeroTaxBase() {
    var result = LongTermAssetEconomics.rental(List.of(), null, new BigDecimal("100000"));

    assertThat(result.economics().annualTax()).isZero();
    assertThat(result.economics().monthlyTaxBase()).isZero();
    assertThat(result.economics().monthlyTax()).isZero();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(Frequency.class)
  void annualizeSupportsEveryFrequency(Frequency frequency) {
    assertThat(LongTermAssetEconomics.annualize(new BigDecimal("12"), frequency))
        .isEqualByComparingTo(frequency == Frequency.MONTHLY ? "144" : "12");
  }
}
