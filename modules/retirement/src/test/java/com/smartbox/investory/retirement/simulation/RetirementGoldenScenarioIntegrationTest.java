package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.application.InvestmentAnnualProjectionService;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.application.service.LongTermAnnualProjectionService;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/** Approved cross-module financial contract. Do not refresh constants from production output. */
class RetirementGoldenScenarioIntegrationTest {
  // This contract intentionally starts at RetirementSimulationService with a prepared planning
  // state, so it protects simulation arithmetic. Source-to-state mapping is covered separately
  // by InvestmentProfileFacadeTest and the Long-Term six-bond projection regression.
  @Test
  void sustainableShortHorizonMatchesApprovedContract() {
    var service = new RetirementSimulationService(new LongTermAnnualProjectionService(),
        new InvestmentAnnualProjectionService());
    var result = service.simulate(fixture(), assumptions(60), SimulationScenario.BASE);
    assertThat(result.simulationFailed()).isFalse();
    assertThat(result.failureAge()).isNull();
    assertThat(result.totalUnfundedAmount()).isZero();
    assertCheckpoints(result, false);
  }

  @Test
  void longHorizonRemainsSustainableUnderTheBucketModel() {
    var service = new RetirementSimulationService(new LongTermAnnualProjectionService(),
        new InvestmentAnnualProjectionService());
    var result = service.simulate(fixture(), assumptions(80), SimulationScenario.BASE);
    assertThat(result.simulationFailed()).isFalse();
    assertThat(result.failureAge()).isNull();
    assertThat(result.totalUnfundedAmount()).isZero();
    assertCheckpoints(result, true);
  }

  private static void assertCheckpoints(SimulationResult result, boolean longHorizon) {
    assertYear(result, 2025, 40, "0", "333683.62", "174803.62", "38880");
    assertYear(result, 2026, 41, "0", "335431.66", "176551.66", "38880");
    assertYear(result, 2027, 42, "240000", "217197.17", "178317.17", "38880");
    assertYear(result, 2035, 50, "270358.22", "193091.95", "193091.95", "0");
    var y2045 = result.years().stream().filter(y -> y.year() == 2045).findFirst().orElseThrow();
    assertThat(money(y2045.totalExpenses())).isEqualByComparingTo("266697.49");
    assertThat(money(y2045.rentalIncome())).isEqualByComparingTo("213293.64");
    if (longHorizon) { var y2052 = result.years().stream().filter(y -> y.year() == 2052).findFirst().orElseThrow();
      assertThat(y2052.age()).isEqualTo(67);
      assertThat(money(y2052.pensionIncome())).isEqualByComparingTo("7000");
      var y2055 = result.years().stream().filter(y -> y.year() == 2055).findFirst().orElseThrow();
      assertThat(money(y2055.totalExpenses())).isEqualByComparingTo("273099.99");
      assertThat(y2055.age()).isEqualTo(70);
    }
    var years = result.years().stream().map(SimulationYear::year).toList();
    assertThat(years.getFirst()).isEqualTo(2025);
    assertThat(years.getLast()).isEqualTo(longHorizon ? 2065 : 2045);
    assertThat(years).allMatch(year -> year >= 2025 && year < 3000);
    for (int i = 1; i < years.size(); i++) assertThat(years.get(i)).isEqualTo(years.get(i - 1) + 1);
    for (SimulationYear year : result.years()) {
      BigDecimal cashIncome = year.employmentIncome().add(year.rentalIncome())
          .add(year.bondIncome()).add(year.pensionIncome()).add(year.eventIncome());
      assertThat(year.totalIncome()).isEqualByComparingTo(cashIncome);
      assertThat(year.requiredPortfolioFunding()).isEqualByComparingTo(
          year.totalExpenses().subtract(year.totalIncome()).max(BigDecimal.ZERO));
      assertThat(year.failed()).isEqualTo(year.unfundedAmount().signum() > 0);
      var f = year.funding();
      assertThat(f.reserveEnd()).isEqualByComparingTo(f.reserveStart()
          .subtract(f.reserveWithdrawal()));
      assertThat(year.fixedIncomeEnd()).isEqualByComparingTo(year.fixedIncomeStart()
          .add(f.capitalizedBondReturn()).add(f.equityHarvestToReserve())
          .subtract(f.longTermFunding()));
      assertThat(year.equityEnd()).isEqualByComparingTo(year.equityStart()
          .add(year.preRetirementContribution()).add(f.investmentReturn())
          .subtract(f.investmentWithdrawal()).subtract(f.equityHarvestToReserve()));
      assertThat(f.reserveEnd()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
      assertThat(f.investmentEnd()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
      assertThat(f.longTermCapitalEnd()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }
    for (int i = 0; i < result.years().size() - 1; i++) {
      SimulationYear previous = result.years().get(i);
      SimulationYear next = result.years().get(i + 1);
      assertThat(next.cashStart()).isEqualByComparingTo(previous.cashEnd());
      assertThat(next.fixedIncomeStart()).isEqualByComparingTo(previous.fixedIncomeEnd());
      assertThat(next.equityStart()).isEqualByComparingTo(previous.equityEnd());
      assertThat(next.realEstateStart()).isEqualByComparingTo(previous.realEstateEnd());
    }
    var maturityYear = result.years().stream().filter(y -> y.year() == 2028).findFirst().orElseThrow();
    assertThat(money(maturityYear.bondIncome())).isEqualByComparingTo("38880");
    var postMaturity = result.years().stream().filter(y -> y.year() == 2029).findFirst().orElseThrow();
    assertThat(postMaturity.bondIncome()).isZero();
  }

  private static void assertYear(SimulationResult result, int year, int age, String costs,
      String income, String rental, String bond) {
    var actual = result.years().stream().filter(y -> y.year() == year).findFirst().orElseThrow();
    assertThat(actual.age()).isEqualTo(age);
    assertThat(money(actual.totalExpenses())).isEqualByComparingTo(costs);
    assertThat(money(actual.totalIncome())).isEqualByComparingTo(income);
    assertThat(money(actual.rentalIncome())).isEqualByComparingTo(rental);
    assertThat(money(actual.bondIncome())).isEqualByComparingTo(bond);
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private static SimulationAssumptions assumptions(int endAge) {
    return SimulationAssumptions.defaults(fixture(), 40, endAge, 2025)
        .withRecurringSpending(new BigDecimal("240000"))
        .withInflationRate(new BigDecimal("0.025"))
        .withSpendingGrowthSpread(new BigDecimal("-0.010"))
        .withRentalIncomeGrowthSpread(new BigDecimal("-0.015"))
        .withEquityReturnRate(new BigDecimal("0.085"))
        .withAnnualEmploymentIncome(new BigDecimal("120000"))
        .withAnnualPreRetirementContribution(new BigDecimal("24000"))
        .withAnnualPension(new BigDecimal("7000"))
        .withRetirementAge(42)
        .withPensionStartAge(67)
        .withExpenseProfile(new ExpenseProfile(List.of(
            new ExpenseProfileStep(0, new BigDecimal("1.00")),
            new ExpenseProfileStep(10, new BigDecimal("1.00")),
            new ExpenseProfileStep(20, new BigDecimal("0.85")),
            new ExpenseProfileStep(30, new BigDecimal("0.75")))));
  }

  private static InvestmentProfile fixture() {
    var bond = new ProjectedLongTermAsset(10L, "Golden bond", LongTermAssetTypeModel.BOND,
        EconomicBucket.FIXED_INCOME, CurrencyType.PLN, new BigDecimal("486000"), Liquidity.LIQUID,
        List.of(new ProjectedLongTermAsset.Period(LocalDate.of(2020, 1, 1), null,
            new BigDecimal("38880"), BigDecimal.ZERO, new BigDecimal("0.10"))),
        List.of(), LocalDate.of(2028, 12, 31), new BigDecimal("486000"),
        InterestTreatmentModel.PAY_OUT, new BigDecimal("0.20"), null, false);
    var rental = new ProjectedLongTermAsset(11L, "Golden rental", LongTermAssetTypeModel.REAL_ESTATE,
        EconomicBucket.REAL_ESTATE, CurrencyType.PLN, new BigDecimal("3000000"), Liquidity.ILLIQUID,
        List.of(new ProjectedLongTermAsset.Period(LocalDate.of(2020, 1, 1), null,
            new BigDecimal("174803.62"), BigDecimal.ZERO, BigDecimal.ZERO)),
        List.of(), null, null, InterestTreatmentModel.PAY_OUT, BigDecimal.ZERO, null, false);
    return new InvestmentProfile(1L, CurrencyType.PLN, new BigDecimal("550000"),
        new BigDecimal("3486000"), new BigDecimal("4036000"), BigDecimal.ZERO,
        new BigDecimal("213683.62"), BigDecimal.ZERO, new BigDecimal("100000"),
        new BigDecimal("3000000"), List.of(), List.of(rental, bond),
        new BigDecimal("174803.62"), new BigDecimal("38880"));
  }
}
