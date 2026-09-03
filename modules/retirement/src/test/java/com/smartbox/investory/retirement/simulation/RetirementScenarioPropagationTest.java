package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.planning.PlanningProfileBaseline;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retirement Scenario Propagation")
class RetirementScenarioPropagationTest {
  @DisplayName("plan Bond Return Is The Scenario Baseline For Every Scenario")
  @Test
  void planBondReturnIsTheScenarioBaselineForEveryScenario() {
    SimulationAssumptions assumptions =
        assumptions(emptyProfile()).withFixedIncomeReturnRate(bd("0.043"));

    assertThat(
            ScenarioEffectiveAssumptions.forScenario(
                    emptyProfile(), assumptions, SimulationScenario.BASE, assumptions.startYear())
                .planBondReturnRate())
        .isEqualByComparingTo("0.043");
    assertThat(
            ScenarioEffectiveAssumptions.forScenario(
                    emptyProfile(), assumptions, SimulationScenario.BASE, assumptions.startYear())
                .bondReturnRate())
        .isEqualByComparingTo("0.043");
    assertThat(
            ScenarioEffectiveAssumptions.forScenario(
                    emptyProfile(),
                    assumptions,
                    SimulationScenario.CONSERVATIVE,
                    assumptions.startYear())
                .bondReturnRate())
        .isEqualByComparingTo("0.033");
    assertThat(
            ScenarioEffectiveAssumptions.forScenario(
                    emptyProfile(),
                    assumptions,
                    SimulationScenario.OPTIMISTIC,
                    assumptions.startYear())
                .bondReturnRate())
        .isEqualByComparingTo("0.043");
  }

  @DisplayName("derives Base Bond Yield From Frozen Capitalized Return And Applies Scenario Delta")
  @Test
  void derivesBaseBondYieldFromFrozenCapitalizedReturnAndAppliesScenarioDelta() {
    InvestmentProfile profile = profileWithCapitalizedBond("900000", "36000");
    SimulationAssumptions assumptions = assumptions(profile).withFixedIncomeReturnRate(bd("0.040"));
    BigDecimal baseYield =
        PlanningBuckets.baseBondYield(
            profile, assumptions.fixedIncomeReturnRate(), assumptions.startYear());

    assertThat(baseYield).isEqualByComparingTo("0.040");
    assertThat(
            PlanningBuckets.fromProfile(profile, bd("0.08"), baseYield, assumptions.startYear())
                .bonds()
                .plannedYieldRate())
        .isEqualByComparingTo("0.040");

    SimulationScenarioSettings conservative =
        SimulationScenarioSettings.forScenario(SimulationScenario.CONSERVATIVE, assumptions);
    SimulationScenarioSettings optimistic =
        SimulationScenarioSettings.forScenario(SimulationScenario.OPTIMISTIC, assumptions);
    assertThat(
            baseYield.add(
                conservative.fixedIncomeReturnRate().subtract(assumptions.fixedIncomeReturnRate())))
        .isEqualByComparingTo("0.030");
    assertThat(
            baseYield.add(
                optimistic.fixedIncomeReturnRate().subtract(assumptions.fixedIncomeReturnRate())))
        .isEqualByComparingTo("0.040");
  }

  @DisplayName("fallback Bond Yield Uses Selected Scenario Rate When Source Yield Is Unavailable")
  @Test
  void fallbackBondYieldUsesSelectedScenarioRateWhenSourceYieldIsUnavailable() {
    InvestmentProfile profile = emptyProfile();
    SimulationAssumptions assumptions = assumptions(profile).withFixedIncomeReturnRate(bd("0.040"));
    SimulationScenarioSettings conservative =
        SimulationScenarioSettings.forScenario(SimulationScenario.CONSERVATIVE, assumptions);

    assertThat(PlanningBuckets.hasSourceBondYield(profile, assumptions.startYear())).isFalse();
    assertThat(
            PlanningBuckets.fromProfile(
                    profile,
                    bd("0.08"),
                    conservative.fixedIncomeReturnRate(),
                    assumptions.startYear())
                .bonds()
                .plannedYieldRate())
        .isEqualByComparingTo("0.030");
  }

  @DisplayName("scenario Rates Reach Projected Bond And Equity Returns")
  @Test
  void scenarioRatesReachProjectedBondAndEquityReturns() {
    InvestmentProfile profile =
        PlanningProfileBaseline.apply(
            profileWithCapitalizedBond("900000", "36000"),
            bd("0"),
            bd("100000"),
            bd("900000"),
            bd("0"),
            bd("0"));
    SimulationAssumptions assumptions =
        assumptions(profile)
            .withFixedIncomeReturnRate(bd("0.040"))
            .withEquityReturnRate(bd("0.080"));
    RetirementSimulationService service = new RetirementSimulationService();

    BigDecimal conservativeBond =
        bondReturn(service.simulate(profile, assumptions, SimulationScenario.CONSERVATIVE));
    BigDecimal baseBond =
        bondReturn(service.simulate(profile, assumptions, SimulationScenario.BASE));
    BigDecimal optimisticBond =
        bondReturn(service.simulate(profile, assumptions, SimulationScenario.OPTIMISTIC));
    BigDecimal conservativeEquity =
        equityReturn(service.simulate(profile, assumptions, SimulationScenario.CONSERVATIVE));
    BigDecimal baseEquity =
        equityReturn(service.simulate(profile, assumptions, SimulationScenario.BASE));
    BigDecimal optimisticEquity =
        equityReturn(service.simulate(profile, assumptions, SimulationScenario.OPTIMISTIC));

    assertThat(conservativeBond).isEqualByComparingTo("27000");
    assertThat(baseBond).isEqualByComparingTo("36000");
    assertThat(conservativeEquity).isEqualByComparingTo("6000");
    assertThat(baseEquity).isEqualByComparingTo("8000");
    assertThat(optimisticBond).isEqualByComparingTo("36000");
    assertThat(optimisticEquity).isEqualByComparingTo("9000");
  }

  @DisplayName("scenario Deltas Use Bond Period Active At Explicit Baseline Year")
  @Test
  void scenarioDeltasUseBondPeriodActiveAtExplicitBaselineYear() {
    InvestmentProfile profile = profileWithCapitalizedBondPeriods();
    SimulationAssumptions assumptions = assumptions(profile).withFixedIncomeReturnRate(bd("0.040"));
    RetirementSimulationService service = new RetirementSimulationService();

    assertThat(
            bondReturn(
                service.simulate(profile, assumptions, SimulationScenario.CONSERVATIVE, 2027)))
        .isEqualByComparingTo("27000");
    assertThat(bondReturn(service.simulate(profile, assumptions, SimulationScenario.BASE, 2027)))
        .isEqualByComparingTo("36000");
    SimulationResult base = service.simulate(profile, assumptions, SimulationScenario.BASE, 2027);
    assertThat(base.years().getFirst().fixedIncomeEnd()).isEqualByComparingTo("936000");
    assertThat(
            bondReturn(service.simulate(profile, assumptions, SimulationScenario.OPTIMISTIC, 2027)))
        .isEqualByComparingTo("36000");
  }

  @DisplayName("scenario Overlay Changes Projected Rental And Spending Values")
  @Test
  void scenarioOverlayChangesProjectedRentalAndSpendingValues() {
    InvestmentProfile profile =
        PlanningProfileBaseline.apply(
            profileWithPayOutBond("900000", "38880"),
            bd("0"),
            bd("100000"),
            bd("900000"),
            bd("100"),
            bd("38880"));
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(profile, 65, 67, 2027)
            .withRecurringSpending(bd("240000"))
            .withInflationRate(bd("0.030"))
            .withRentalIncomeGrowthSpread(bd("-0.020"))
            .withSpendingGrowthSpread(bd("-0.015"))
            .withRetirementAge(65);
    RetirementSimulationService service = new RetirementSimulationService();

    SimulationResult conservative =
        service.simulate(profile, assumptions, SimulationScenario.CONSERVATIVE, 2026);
    SimulationResult base = service.simulate(profile, assumptions, SimulationScenario.BASE, 2026);
    SimulationResult optimistic =
        service.simulate(profile, assumptions, SimulationScenario.OPTIMISTIC, 2026);

    assertThat(conservative.years().get(1).rentalIncome()).isEqualByComparingTo("101.0025");
    assertThat(base.years().get(1).rentalIncome()).isEqualByComparingTo("102.01");
    assertThat(optimistic.years().get(1).rentalIncome()).isEqualByComparingTo("103.0225");
    assertThat(conservative.years().get(1).totalExpenses()).isEqualByComparingTo("243600");
    assertThat(base.years().get(1).totalExpenses()).isEqualByComparingTo("243600");
    assertThat(optimistic.years().get(1).totalExpenses()).isEqualByComparingTo("242400");
  }

  @DisplayName("scenario Comparison Uses The Same Canonical Simulation Path")
  @Test
  void scenarioComparisonUsesTheSameCanonicalSimulationPath() {
    InvestmentProfile profile = profileWithCapitalizedBond("900000", "36000");
    SimulationAssumptions assumptions = assumptions(profile);
    RetirementSimulationService service = new RetirementSimulationService();

    var compared = service.compareScenarios(profile, assumptions);
    for (SimulationScenario scenario : SimulationScenario.values()) {
      var direct = service.simulate(profile, assumptions, scenario);
      assertThat(compared.get(scenario).years().getFirst().funding().capitalizedBondReturn())
          .isEqualByComparingTo(direct.years().getFirst().funding().capitalizedBondReturn());
      assertThat(compared.get(scenario).years().getFirst().equityGain())
          .isEqualByComparingTo(direct.years().getFirst().equityGain());
    }
  }

  private static BigDecimal bondReturn(SimulationResult result) {
    return result.years().getFirst().funding().capitalizedBondReturn();
  }

  private static BigDecimal equityReturn(SimulationResult result) {
    return result.years().getFirst().equityGain();
  }

  private static SimulationAssumptions assumptions(InvestmentProfile profile) {
    return SimulationAssumptions.defaults(profile, 65, 65, 2027)
        .withRecurringSpending(BigDecimal.ZERO)
        .withRetirementAge(65)
        .withEquityReturnRate(bd("0.080"));
  }

  private static InvestmentProfile profileWithCapitalizedBond(String capital, String annualReturn) {
    return profileWithBond(capital, annualReturn, InterestTreatment.CAPITALIZE);
  }

  private static InvestmentProfile profileWithCapitalizedBondPeriods() {
    ProjectedLongTermAsset bond =
        new ProjectedLongTermAsset(
            1L,
            "Bond",
            LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            bd("900000"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2026, 12, 31),
                    null,
                    BigDecimal.ZERO,
                    bd("0.03"),
                    null,
                    false),
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2030, 12, 31),
                    null,
                    BigDecimal.ZERO,
                    bd("0.05"),
                    null,
                    false)),
            List.of(),
            null,
            null,
            InterestTreatment.CAPITALIZE,
            BigDecimal.ZERO,
            null,
            false);
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        bd("100000"),
        bd("900000"),
        bd("1000000"),
        BigDecimal.ZERO,
        bd("900000"),
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(bond),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
        bd("100000")
            .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            bd("100000"),
            BigDecimal.ZERO,
            bd("900000"),
            BigDecimal.ZERO,
            bd("1000000")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static InvestmentProfile profileWithPayOutBond(String capital, String income) {
    return profileWithBond(capital, income, InterestTreatment.PAY_OUT);
  }

  private static InvestmentProfile profileWithBond(
      String capital, String annualIncome, InterestTreatment treatment) {
    ProjectedLongTermAsset bond =
        new ProjectedLongTermAsset(
            1L,
            "Bond",
            LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            bd(capital),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2020, 1, 1),
                    null,
                    bd(annualIncome),
                    BigDecimal.ZERO,
                    bd("0.04"),
                    null,
                    false)),
            List.of(),
            null,
            null,
            treatment,
            BigDecimal.ZERO,
            null,
            false);
    BigDecimal cashIncome =
        treatment == InterestTreatment.PAY_OUT ? bd(annualIncome) : BigDecimal.ZERO;
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        bd("100000"),
        bd(capital),
        bd(capital).add(bd("100000")),
        BigDecimal.ZERO,
        bd(capital),
        List.of(),
        BigDecimal.ZERO,
        cashIncome,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(bond),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
        bd("100000")
            .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            bd("100000"),
            BigDecimal.ZERO,
            bd(capital),
            BigDecimal.ZERO,
            bd(capital).add(bd("100000"))),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static InvestmentProfile emptyProfile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
        BigDecimal.ZERO
            .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
