package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.longterm.api.CashFlowType;
import com.smartbox.investory.longterm.api.LongTermAssetProjection;
import com.smartbox.investory.longterm.api.LongTermAssetType;
import com.smartbox.investory.longterm.api.RentalIncomeProjection;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RentalIncomeProjectionTest {
  @Test
  void openEndedIncomeUsesConfiguredGrowth() {
    var asset = asset(period(2027, null, "180000", CashFlowType.RENT));
    var first = RentalIncomeProjection.project(asset, Map.of(), 2027, bd("0.01"));
    var second = RentalIncomeProjection.project(asset, first.incomeByType(), 2028, bd("0.01"));
    var third = RentalIncomeProjection.project(asset, second.incomeByType(), 2029, bd("0.01"));

    assertBd("180000", first.netIncome());
    assertBd("181800", second.netIncome());
    assertBd("183618", third.netIncome());
  }

  @Test
  void explicitPeriodWinsAndGrowthContinuesFromIt() {
    var asset =
        asset(
            period(2027, 2027, "180000", CashFlowType.RENT),
            period(2028, null, "210000", CashFlowType.RENT));
    var first = RentalIncomeProjection.project(asset, Map.of(), 2027, bd("0.10"));
    var second = RentalIncomeProjection.project(asset, first.incomeByType(), 2028, bd("0.10"));
    var third = RentalIncomeProjection.project(asset, second.incomeByType(), 2029, bd("0.10"));

    assertBd("180000", first.netIncome());
    assertBd("210000", second.netIncome());
    assertBd("231000", third.netIncome());
  }

  @Test
  void storedEndDateDoesNotExpireForwardBaseline() {
    var asset = asset(period(2027, 2028, "180000", CashFlowType.RENT));
    var active = RentalIncomeProjection.project(asset, Map.of(), 2028, BigDecimal.ZERO);
    var expired =
        RentalIncomeProjection.project(asset, active.incomeByType(), 2029, BigDecimal.ZERO);

    assertBd("180000", active.netIncome());
    assertBd("180000", expired.netIncome());
  }

  @Test
  void explicitZeroReplacementTerminatesIncome() {
    var asset =
        asset(
            period(2027, 2027, "180000", CashFlowType.RENT),
            period(2028, null, "0", CashFlowType.RENT));
    var first = RentalIncomeProjection.project(asset, Map.of(), 2027, bd("0.10"));
    var replacement = RentalIncomeProjection.project(asset, first.incomeByType(), 2028, bd("0.10"));
    var after = RentalIncomeProjection.project(asset, replacement.incomeByType(), 2029, bd("0.10"));

    assertBd("180000", first.netIncome());
    assertBd("0", replacement.netIncome());
    assertBd("0", after.netIncome());
  }

  @Test
  void actualYearCombinesPeriodsByEffectiveCoverage() {
    var asset =
        asset(
            periodBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "24000", CashFlowType.RENT),
            periodBetween(LocalDate.of(2026, 7, 1), null, "27600", CashFlowType.RENT));

    var result = RentalIncomeProjection.actualYear(asset, 2026);
    var expected =
        bd("24000")
            .multiply(bd("181"))
            .add(bd("27600").multiply(bd("184")))
            .divide(bd("365"), 18, java.math.RoundingMode.HALF_UP);

    assertTrue(expected.subtract(result.grossIncome()).abs().compareTo(bd("0.000000000001")) < 0);
    assertTrue(expected.subtract(result.netIncome()).abs().compareTo(bd("0.000000000001")) < 0);
  }

  @Test
  void expensesCarryForwardWithoutRentalGrowth() {
    var asset =
        asset(
            period(2027, 2028, "180000", CashFlowType.RENT),
            new LongTermAssetProjection.Period(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2028, 12, 31),
                BigDecimal.ZERO,
                bd("12000"),
                BigDecimal.ZERO,
                CashFlowType.ADMIN_FEE));
    var first = RentalIncomeProjection.project(asset, Map.of(), 2027, bd("0.10"));
    var second = RentalIncomeProjection.project(asset, first.incomeByType(), 2028, bd("0.10"));

    assertBd("12000", first.expenses());
    assertBd("12000", second.expenses());
    assertBd("186000", second.netIncome());
  }

  @Test
  void incomeComponentsGrowButExpensesAndTaxRemainCanonical() {
    var asset =
        asset(
            new LongTermAssetProjection.Period(
                LocalDate.of(2027, 1, 1),
                null,
                bd("120000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CashFlowType.RENT),
            new LongTermAssetProjection.Period(
                LocalDate.of(2027, 1, 1),
                null,
                bd("12000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CashFlowType.PARKING_RENT),
            new LongTermAssetProjection.Period(
                LocalDate.of(2027, 1, 1),
                null,
                BigDecimal.ZERO,
                bd("10000"),
                BigDecimal.ZERO,
                CashFlowType.ADMIN_FEE));
    var result = RentalIncomeProjection.project(asset, Map.of(), 2027, bd("0.02"));

    assertBd("132000", result.grossIncome());
    assertBd("10000", result.expenses());
    assertBd("122000", result.netIncome());
  }

  private static LongTermAssetProjection asset(LongTermAssetProjection.Period... periods) {
    return new LongTermAssetProjection(
        1L,
        "Rental",
        LongTermAssetType.REAL_ESTATE,
        CurrencyType.USD,
        BigDecimal.ZERO,
        List.of(periods),
        null,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static LongTermAssetProjection.Period period(
      int from, Integer to, String amount, CashFlowType type) {
    return new LongTermAssetProjection.Period(
        LocalDate.of(from, 1, 1),
        to == null ? null : LocalDate.of(to, 12, 31),
        bd(amount),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        type);
  }

  private static LongTermAssetProjection.Period periodBetween(
      LocalDate from, LocalDate to, String amount, CashFlowType type) {
    return new LongTermAssetProjection.Period(
        from, to, bd(amount), BigDecimal.ZERO, BigDecimal.ZERO, type);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static void assertBd(String expected, BigDecimal actual) {
    assertEquals(0, bd(expected).compareTo(actual));
  }
}
