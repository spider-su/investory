package com.smartbox.investory.application.longterm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.application.profile.EconomicBucket;
import com.smartbox.investory.application.profile.Liquidity;
import com.smartbox.investory.application.profile.ProjectedLongTermAsset;
import com.smartbox.investory.infrastructure.longterm.CashFlowType;
import com.smartbox.investory.infrastructure.longterm.LongTermAssetType;
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
  void genuineExpirationProducesZero() {
    var asset = asset(period(2027, 2028, "180000", CashFlowType.RENT));
    var active = RentalIncomeProjection.project(asset, Map.of(), 2028, BigDecimal.ZERO);
    var expired =
        RentalIncomeProjection.project(asset, active.incomeByType(), 2029, BigDecimal.ZERO);

    assertBd("180000", active.netIncome());
    assertBd("0", expired.netIncome());
  }

  @Test
  void incomeComponentsGrowButExpensesAndTaxRemainCanonical() {
    var asset =
        asset(
            new ProjectedLongTermAsset.Period(
                LocalDate.of(2027, 1, 1),
                null,
                bd("120000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CashFlowType.RENT),
            new ProjectedLongTermAsset.Period(
                LocalDate.of(2027, 1, 1),
                null,
                bd("12000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CashFlowType.PARKING_RENT),
            new ProjectedLongTermAsset.Period(
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

  private static ProjectedLongTermAsset asset(ProjectedLongTermAsset.Period... periods) {
    return new ProjectedLongTermAsset(
        1L,
        "Rental",
        LongTermAssetType.REAL_ESTATE,
        EconomicBucket.REAL_ESTATE,
        CurrencyType.USD,
        BigDecimal.ZERO,
        Liquidity.ILLIQUID,
        List.of(periods),
        null,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static ProjectedLongTermAsset.Period period(
      int from, Integer to, String amount, CashFlowType type) {
    return new ProjectedLongTermAsset.Period(
        LocalDate.of(from, 1, 1),
        to == null ? null : LocalDate.of(to, 12, 31),
        bd(amount),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        type);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static void assertBd(String expected, BigDecimal actual) {
    assertEquals(0, bd(expected).compareTo(actual));
  }
}
