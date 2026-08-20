package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.longterm.api.LongTermAssetType;
import com.smartbox.investory.retirement.profile.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Regression coverage for rental-income-only retirement simulation. */
class RetirementSimulationRealEstateRegressionTest {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final RetirementSimulationService service = new RetirementSimulationService();

  @Test
  void propertyValueIsExcludedWhileRentalIncomeRemainsModeled() {
    InvestmentProfile profile = profile();
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            44,
            new BigDecimal("100000"),
            new BigDecimal("0.02"),
            new BigDecimal("0.02"),
            new BigDecimal("0.04"),
            new BigDecimal("0.06"),
            new BigDecimal("0.03"),
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            new BigDecimal("20000"),
            List.of(
                new SimulationEvent(
                    null,
                    2028,
                    "Renovation",
                    new BigDecimal("20000"),
                    SimulationEventType.ONE_OFF_EXPENSE,
                    null),
                new SimulationEvent(
                    null,
                    2030,
                    "Car",
                    new BigDecimal("30000"),
                    SimulationEventType.ONE_OFF_EXPENSE,
                    null)));

    List<SimulationYear> actual =
        service.simulate(profile, assumptions, SimulationScenario.BASE).years();
    for (SimulationYear year : actual) {
      assertBd("0", year.realEstateEnd(), "property value is not simulated");
      assertBd(
          year.cashEnd()
              .add(year.fixedIncomeEnd())
              .add(year.equityEnd())
              .add(year.otherEnd())
              .add(year.lockedContractualAssets()),
          year.endNetWorth(),
          "terminal value is modeled portfolio only");
    }
    assertBd("123509", actual.get(0).passiveIncome(), "year 1 passive income");
    assertBd("0", actual.get(0).requiredPortfolioWithdrawal(), "year 1 withdrawal");
    assertBd("20000", actual.get(2).eventExpenses(), "year 3 event expense");
    assertBd("30000", actual.get(4).eventExpenses(), "year 5 event expense");
    assertTrue(actual.get(4).requiredPortfolioWithdrawal().signum() > 0);
  }

  @Test
  void changingPropertyValueDoesNotChangeFundingResultWhenRentIsEqual() {
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            42,
            new BigDecimal("200000"),
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            ZERO,
            List.of());
    SimulationResult low =
        service.simulate(
            profileWithProperties(
                List.of(property(1L, "Low", "500000", "120000", "0")), new BigDecimal("500000")),
            assumptions,
            SimulationScenario.BASE);
    SimulationResult high =
        service.simulate(
            profileWithProperties(
                List.of(property(1L, "High", "5000000", "120000", "0")), new BigDecimal("5000000")),
            assumptions,
            SimulationScenario.BASE);

    for (int i = 0; i < low.years().size(); i++) {
      assertEquals(
          0,
          low.years()
              .get(i)
              .requiredPortfolioFunding()
              .compareTo(high.years().get(i).requiredPortfolioFunding()));
      assertEquals(
          0, low.years().get(i).endNetWorth().compareTo(high.years().get(i).endNetWorth()));
    }
  }

  private static InvestmentProfile profile() {
    List<ProjectedLongTermAsset> properties =
        List.of(
            property(1L, "Property A", "710000", "34800", "8764"),
            property(2L, "Property B", "710000", "33600", "8370"),
            property(3L, "Property C", "700000", "31200", "5090"),
            property(4L, "Property D", "780000", "36000", "5390"),
            property(5L, "Property E", "750000", "36600", "6440"));
    BigDecimal realEstate = new BigDecimal("3650000");
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        new BigDecimal("600000"),
        realEstate,
        new BigDecimal("4250000"),
        ZERO,
        new BigDecimal("123509"),
        new BigDecimal("123509"),
        new BigDecimal("600000"),
        realEstate,
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH, new BigDecimal("100000"), ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME, new BigDecimal("200000"), ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.EQUITY, new BigDecimal("300000"), ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE, realEstate, ZERO, Liquidity.ILLIQUID)),
        properties);
  }

  private static InvestmentProfile profileWithProperties(
      List<ProjectedLongTermAsset> properties, BigDecimal realEstate) {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        new BigDecimal("600000"),
        realEstate,
        new BigDecimal("600000").add(realEstate),
        ZERO,
        new BigDecimal("120000"),
        new BigDecimal("120000"),
        new BigDecimal("600000"),
        realEstate,
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH, new BigDecimal("100000"), ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME, new BigDecimal("200000"), ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.EQUITY, new BigDecimal("300000"), ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE, realEstate, ZERO, Liquidity.ILLIQUID)),
        properties);
  }

  private static ProjectedLongTermAsset property(
      Long id, String name, String value, String gross, String expenses) {
    return new ProjectedLongTermAsset(
        id,
        name,
        LongTermAssetType.REAL_ESTATE,
        EconomicBucket.REAL_ESTATE,
        CurrencyType.PLN,
        new BigDecimal(value),
        Liquidity.ILLIQUID,
        List.of(
            new ProjectedLongTermAsset.Period(
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal(gross),
                new BigDecimal(expenses),
                new BigDecimal("0.03"),
                com.smartbox.investory.longterm.api.CashFlowType.RENT)),
        null,
        null,
        null,
        new BigDecimal("0.085"),
        new BigDecimal(gross));
  }

  private static void assertBd(String expected, BigDecimal actual, String label) {
    assertEquals(
        0,
        new BigDecimal(expected).compareTo(actual.setScale(8, java.math.RoundingMode.HALF_UP)),
        label + ": expected " + expected + " but was " + actual);
  }

  private static void assertBd(BigDecimal expected, BigDecimal actual, String label) {
    assertEquals(
        0, expected.compareTo(actual), label + ": expected " + expected + " but was " + actual);
  }
}
