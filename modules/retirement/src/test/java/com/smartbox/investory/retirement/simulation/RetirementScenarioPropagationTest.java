package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetirementScenarioPropagationTest {
  @Test
  void derivesBaseBondYieldFromFrozenCapitalizedReturnAndAppliesScenarioDelta() {
    InvestmentProfile profile = profileWithCapitalizedBond("900000", "36000");
    SimulationAssumptions assumptions = assumptions(profile).withFixedIncomeReturnRate(bd("0.040"));
    BigDecimal baseYield = PlanningBuckets.baseBondYield(profile, assumptions.fixedIncomeReturnRate());

    assertThat(baseYield).isEqualByComparingTo("0.040");
    assertThat(PlanningBuckets.fromProfile(profile, bd("0.08"), baseYield).bonds().plannedYieldRate())
        .isEqualByComparingTo("0.040");

    SimulationScenarioSettings conservative =
        SimulationScenarioSettings.forScenario(SimulationScenario.CONSERVATIVE, assumptions);
    SimulationScenarioSettings optimistic =
        SimulationScenarioSettings.forScenario(SimulationScenario.OPTIMISTIC, assumptions);
    assertThat(baseYield.add(conservative.fixedIncomeReturnRate().subtract(assumptions.fixedIncomeReturnRate())))
        .isEqualByComparingTo("0.030");
    assertThat(baseYield.add(optimistic.fixedIncomeReturnRate().subtract(assumptions.fixedIncomeReturnRate())))
        .isEqualByComparingTo("0.050");
  }

  @Test
  void fallbackBondYieldUsesSelectedScenarioRateWhenSourceYieldIsUnavailable() {
    InvestmentProfile profile = emptyProfile();
    SimulationAssumptions assumptions = assumptions(profile).withFixedIncomeReturnRate(bd("0.040"));
    SimulationScenarioSettings conservative =
        SimulationScenarioSettings.forScenario(SimulationScenario.CONSERVATIVE, assumptions);

    assertThat(PlanningBuckets.hasSourceBondYield(profile)).isFalse();
    assertThat(PlanningBuckets.fromProfile(profile, bd("0.08"), conservative.fixedIncomeReturnRate())
        .bonds().plannedYieldRate()).isEqualByComparingTo("0.030");
  }

  @Test
  void scenarioRatesReachProjectedBondAndEquityReturns() {
    InvestmentProfile profile = profileWithCapitalizedBond("900000", "36000").withPlanningBaseline(
        bd("0"), bd("100000"), bd("900000"), bd("0"), bd("0"));
    SimulationAssumptions assumptions = assumptions(profile)
        .withFixedIncomeReturnRate(bd("0.040"))
        .withEquityReturnRate(bd("0.080"));
    RetirementSimulationService service = new RetirementSimulationService();

    BigDecimal conservativeBond = bondReturn(service.simulate(profile, assumptions, SimulationScenario.CONSERVATIVE));
    BigDecimal baseBond = bondReturn(service.simulate(profile, assumptions, SimulationScenario.BASE));
    BigDecimal optimisticBond = bondReturn(service.simulate(profile, assumptions, SimulationScenario.OPTIMISTIC));
    BigDecimal conservativeEquity = equityReturn(service.simulate(profile, assumptions, SimulationScenario.CONSERVATIVE));
    BigDecimal baseEquity = equityReturn(service.simulate(profile, assumptions, SimulationScenario.BASE));
    BigDecimal optimisticEquity = equityReturn(service.simulate(profile, assumptions, SimulationScenario.OPTIMISTIC));

    assertThat(conservativeBond).isEqualByComparingTo("27000");
    assertThat(baseBond).isEqualByComparingTo("36000");
    assertThat(optimisticBond).isEqualByComparingTo("45000");
    assertThat(conservativeEquity).isEqualByComparingTo("6000");
    assertThat(baseEquity).isEqualByComparingTo("8000");
    assertThat(optimisticEquity).isEqualByComparingTo("10000");
  }

  @Test
  void scenarioOverlayChangesProjectedRentalAndSpendingValues() {
    InvestmentProfile profile = profileWithPayOutBond("900000", "38880").withPlanningBaseline(
        bd("0"), bd("100000"), bd("900000"), bd("100"), bd("38880"));
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 65, 67, 2027)
        .withRecurringSpending(bd("240000"))
        .withInflationRate(bd("0.030"))
        .withRentalIncomeGrowthSpread(bd("-0.020"))
        .withSpendingGrowthSpread(bd("-0.015"))
        .withRetirementAge(65);
    RetirementSimulationService service = new RetirementSimulationService();

    SimulationResult conservative = service.simulate(profile, assumptions, SimulationScenario.CONSERVATIVE, 2026);
    SimulationResult base = service.simulate(profile, assumptions, SimulationScenario.BASE, 2026);
    SimulationResult optimistic = service.simulate(profile, assumptions, SimulationScenario.OPTIMISTIC, 2026);

    assertThat(conservative.years().get(1).rentalIncome())
        .isEqualByComparingTo("103.0225");
    assertThat(base.years().get(1).rentalIncome()).isEqualByComparingTo("102.01");
    assertThat(optimistic.years().get(1).rentalIncome()).isEqualByComparingTo("102.01");
    assertThat(conservative.years().get(1).totalExpenses()).isEqualByComparingTo("247200");
    assertThat(base.years().get(1).totalExpenses()).isEqualByComparingTo("243600");
    assertThat(optimistic.years().get(1).totalExpenses()).isEqualByComparingTo("241200");
  }

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
    return profileWithBond(capital, annualReturn, InterestTreatmentModel.CAPITALIZE);
  }

  private static InvestmentProfile profileWithPayOutBond(String capital, String income) {
    return profileWithBond(capital, income, InterestTreatmentModel.PAY_OUT);
  }

  private static InvestmentProfile profileWithBond(
      String capital, String annualIncome, InterestTreatmentModel treatment) {
    ProjectedLongTermAsset bond = new ProjectedLongTermAsset(
        1L, "Bond", LongTermAssetTypeModel.BOND, EconomicBucket.FIXED_INCOME, CurrencyType.PLN,
        bd(capital), Liquidity.LIQUID,
        List.of(new ProjectedLongTermAsset.Period(LocalDate.of(2020, 1, 1), null,
            bd(annualIncome), BigDecimal.ZERO, bd("0.04"))), List.of(), null, null,
        treatment, BigDecimal.ZERO, null, false);
    BigDecimal cashIncome = treatment == InterestTreatmentModel.PAY_OUT ? bd(annualIncome) : BigDecimal.ZERO;
    return new InvestmentProfile(1L, CurrencyType.PLN, bd("100000"), bd(capital),
        bd(capital).add(bd("100000")), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, bd(capital), List.of(), List.of(bond), BigDecimal.ZERO, cashIncome);
  }

  private static InvestmentProfile emptyProfile() {
    return new InvestmentProfile(1L, CurrencyType.PLN, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
