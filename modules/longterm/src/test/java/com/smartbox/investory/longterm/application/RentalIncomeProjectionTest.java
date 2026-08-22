package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.api.model.RentalIncomeProjectionModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RentalIncomeProjectionTest {
  @Test
  void openEndedIncomeUsesConfiguredGrowth() {
    var asset = asset(period(2027, null, "180000", CashFlowTypeModel.RENT));
    var first = RentalIncomeProjectionModel.project(asset, Map.of(), 2027, bd("0.01"));
    var second = RentalIncomeProjectionModel.project(asset, first.incomeByType(), 2028, bd("0.01"));
    var third = RentalIncomeProjectionModel.project(asset, second.incomeByType(), 2029, bd("0.01"));

    assertBd("180000", first.netIncome());
    assertBd("181800", second.netIncome());
    assertBd("183618", third.netIncome());
  }

  @Test
  void explicitPeriodWinsAndGrowthContinuesFromIt() {
    var asset =
        asset(
            period(2027, 2027, "180000", CashFlowTypeModel.RENT),
            period(2028, null, "210000", CashFlowTypeModel.RENT));
    var first = RentalIncomeProjectionModel.project(asset, Map.of(), 2027, bd("0.10"));
    var second = RentalIncomeProjectionModel.project(asset, first.incomeByType(), 2028, bd("0.10"));
    var third = RentalIncomeProjectionModel.project(asset, second.incomeByType(), 2029, bd("0.10"));

    assertBd("180000", first.netIncome());
    assertBd("210000", second.netIncome());
    assertBd("231000", third.netIncome());
  }

  @Test
  void storedEndDateDoesNotExpireForwardBaseline() {
    var asset = asset(period(2027, 2028, "180000", CashFlowTypeModel.RENT));
    var active = RentalIncomeProjectionModel.project(asset, Map.of(), 2028, BigDecimal.ZERO);
    var expired =
        RentalIncomeProjectionModel.project(asset, active.incomeByType(), 2029, BigDecimal.ZERO);

    assertBd("180000", active.netIncome());
    assertBd("180000", expired.netIncome());
  }

  @Test
  void explicitZeroReplacementTerminatesIncome() {
    var asset =
        asset(
            period(2027, 2027, "180000", CashFlowTypeModel.RENT),
            period(2028, null, "0", CashFlowTypeModel.RENT));
    var first = RentalIncomeProjectionModel.project(asset, Map.of(), 2027, bd("0.10"));
    var replacement =
        RentalIncomeProjectionModel.project(asset, first.incomeByType(), 2028, bd("0.10"));
    var after =
        RentalIncomeProjectionModel.project(asset, replacement.incomeByType(), 2029, bd("0.10"));

    assertBd("180000", first.netIncome());
    assertBd("0", replacement.netIncome());
    assertBd("0", after.netIncome());
  }

  @Test
  void terminatedHistoricalContractRemainsForwardBaselineWithoutSuccessor() {
    var contract = new RentalContractModel(1L, LocalDate.of(2027, 1, 1), null,
        LocalDate.of(2028, 6, 30), null,
        List.of(new RentalContractModel.Term(CashFlowTypeModel.RENT, bd("180000"),
            FrequencyModel.ANNUAL, false)));
    var asset = assetWithContracts(contract);
    var prior = RentalIncomeProjectionModel.project(asset, Map.of(), 2028, BigDecimal.ZERO);
    var future = RentalIncomeProjectionModel.project(asset, prior.incomeByType(), 2029, bd("0.01"));
    assertBd("180000", prior.netIncome());
    assertBd("181800", future.netIncome());
  }

  @Test
  void contractTaxOwnershipOverridesAssetDefaultAndHistoricalDatesAreRespected() {
    var contract = new RentalContractModel(1L, LocalDate.of(2024, 7, 1), LocalDate.of(2025, 6, 30),
        null, true, List.of(new RentalContractModel.Term(CashFlowTypeModel.RENT,
            bd("120000"), FrequencyModel.ANNUAL, false)));
    var asset = new LongTermAssetProjectionModel(1L, "Rental", LongTermAssetTypeModel.REAL_ESTATE,
        CurrencyType.USD, BigDecimal.ZERO, List.of(), List.of(contract), null, null, null,
        bd("0.20"), bd("100000"), false);
    var actual = RentalIncomeProjectionModel.actualYear(asset, 2024);
    assertTrue(actual.grossIncome().subtract(bd("60327.868852459016400"))
        .abs().compareTo(bd("0.000000000000001")) < 0);
    assertBd("0", actual.tax());
    assertBd("0", RentalIncomeProjectionModel.actualYear(asset, 2026).grossIncome());
  }

  @Test
  void landlordExpensesStayFlatAndTenantPaidExpensesStayExcluded() {
    var contract = new RentalContractModel(1L, LocalDate.of(2027, 1, 1), null, null, null,
        List.of(
            new RentalContractModel.Term(CashFlowTypeModel.RENT, bd("120000"), FrequencyModel.ANNUAL, false),
            new RentalContractModel.Term(CashFlowTypeModel.ADMIN_FEE, bd("12000"), FrequencyModel.ANNUAL, false),
            new RentalContractModel.Term(CashFlowTypeModel.UTILITIES, bd("6000"), FrequencyModel.ANNUAL, true)));
    var asset = assetWithContracts(contract);
    var first = RentalIncomeProjectionModel.project(asset, Map.of(), 2027, bd("0.10"));
    var second = RentalIncomeProjectionModel.project(asset, first.incomeByType(), 2028, bd("0.10"));
    assertBd("12000", first.expenses());
    assertBd("12000", second.expenses());
    assertBd("132000", second.grossIncome());
  }

  @Test
  void actualYearCombinesPeriodsByEffectiveCoverage() {
    var asset =
        asset(
            periodBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "24000", CashFlowTypeModel.RENT),
            periodBetween(LocalDate.of(2026, 7, 1), null, "27600", CashFlowTypeModel.RENT));

    var result = RentalIncomeProjectionModel.actualYear(asset, 2026);
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
            period(2027, 2028, "180000", CashFlowTypeModel.RENT),
            new LongTermAssetProjectionModel.Period(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2028, 12, 31),
                BigDecimal.ZERO,
                bd("12000"),
                BigDecimal.ZERO,
                CashFlowTypeModel.ADMIN_FEE));
    var first = RentalIncomeProjectionModel.project(asset, Map.of(), 2027, bd("0.10"));
    var second = RentalIncomeProjectionModel.project(asset, first.incomeByType(), 2028, bd("0.10"));

    assertBd("12000", first.expenses());
    assertBd("12000", second.expenses());
    assertBd("186000", second.netIncome());
  }

  @Test
  void incomeComponentsGrowButExpensesAndTaxRemainCanonical() {
    var asset =
        asset(
            new LongTermAssetProjectionModel.Period(
                LocalDate.of(2027, 1, 1),
                null,
                bd("120000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CashFlowTypeModel.RENT),
            new LongTermAssetProjectionModel.Period(
                LocalDate.of(2027, 1, 1),
                null,
                bd("12000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CashFlowTypeModel.PARKING_RENT),
            new LongTermAssetProjectionModel.Period(
                LocalDate.of(2027, 1, 1),
                null,
                BigDecimal.ZERO,
                bd("10000"),
                BigDecimal.ZERO,
                CashFlowTypeModel.ADMIN_FEE));
    var result = RentalIncomeProjectionModel.project(asset, Map.of(), 2027, bd("0.02"));

    assertBd("132000", result.grossIncome());
    assertBd("10000", result.expenses());
    assertBd("122000", result.netIncome());
  }

  @Test
  void expensesStoredOnIncomePeriodRemainPartOfNetRentalEconomics() {
    var asset =
        asset(
            new LongTermAssetProjectionModel.Period(
                LocalDate.of(2027, 1, 1),
                null,
                bd("180000"),
                bd("34054"),
                BigDecimal.ZERO,
                CashFlowTypeModel.RENT));

    assertBd(
        "145946",
        RentalIncomeProjectionModel.project(asset, Map.of(), 2027, BigDecimal.ZERO).netIncome());
  }

  private static LongTermAssetProjectionModel asset(
      LongTermAssetProjectionModel.Period... periods) {
    return new LongTermAssetProjectionModel(
        1L,
        "Rental",
        LongTermAssetTypeModel.REAL_ESTATE,
        CurrencyType.USD,
        BigDecimal.ZERO,
        List.of(periods),
        null,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static LongTermAssetProjectionModel assetWithContracts(RentalContractModel... contracts) {
    return new LongTermAssetProjectionModel(1L, "Rental", LongTermAssetTypeModel.REAL_ESTATE,
        CurrencyType.USD, BigDecimal.ZERO, List.of(), List.of(contracts), null, null, null,
        BigDecimal.ZERO, BigDecimal.ZERO, false);
  }

  private static LongTermAssetProjectionModel.Period period(
      int from, Integer to, String amount, CashFlowTypeModel type) {
    return new LongTermAssetProjectionModel.Period(
        LocalDate.of(from, 1, 1),
        to == null ? null : LocalDate.of(to, 12, 31),
        bd(amount),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        type);
  }

  private static LongTermAssetProjectionModel.Period periodBetween(
      LocalDate from, LocalDate to, String amount, CashFlowTypeModel type) {
    return new LongTermAssetProjectionModel.Period(
        from, to, bd(amount), BigDecimal.ZERO, BigDecimal.ZERO, type);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static void assertBd(String expected, BigDecimal actual) {
    assertEquals(0, bd(expected).compareTo(actual));
  }
}
