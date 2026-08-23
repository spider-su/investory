package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

class RetirementSimulationAnnualRentalIncomeTest {

  @Test
  void firstProjectedYearAdvancesCurrentRentalBaseline() {
    var service = new RetirementSimulationService();
    var profile = profile(BigDecimal.ZERO, List.of(), new BigDecimal("174803.62"));
    int firstProjectedYear = 2027;
    var assumptions = SimulationAssumptions.defaults(profile, 65, 65, firstProjectedYear)
        .withInflationRate(new BigDecimal("0.030"))
        .withRentalIncomeGrowthSpread(new BigDecimal("-0.020"));

    var year = service.simulate(profile, assumptions, SimulationScenario.BASE, 2026).years().getFirst();

    assertThat(year.year()).isEqualTo(firstProjectedYear);
    assertThat(year.rentalIncome()).isEqualByComparingTo("176551.6562");
  }

  @Test
  void explicitBaselineYearMakesSimulationIndependentOfWallClock() {
    var service = new RetirementSimulationService();
    var profile = profile(BigDecimal.ZERO, List.of(), new BigDecimal("100"));
    var assumptions = SimulationAssumptions.defaults(profile, 65, 65, 2027)
        .withInflationRate(new BigDecimal("0.030"))
        .withRentalIncomeGrowthSpread(new BigDecimal("-0.020"));

    var first = service.simulate(profile, assumptions, SimulationScenario.BASE, 2026);
    var repeated = service.simulate(profile, assumptions, SimulationScenario.BASE, 2026);
    var olderBaseline = service.simulate(profile, assumptions, SimulationScenario.BASE, 2025);

    assertThat(repeated).isEqualTo(first);
    assertThat(first.years().getFirst().rentalIncome()).isEqualByComparingTo("101");
    assertThat(olderBaseline.years().getFirst().rentalIncome()).isEqualByComparingTo("102.01");
  }
  @Test
  void supportsAnnualRentalIncomeThatDoesNotDivideEvenlyIntoMonths() {
    var service =
        new RetirementSimulationService();
    var profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            List.of(),
            BigDecimal.ONE,
            BigDecimal.ZERO);
    var assumptions = SimulationAssumptions.defaults(profile, 65, 66, 2026);

    var result = service.simulate(profile, assumptions, SimulationScenario.BASE);

    assertThatCode(() -> service.simulate(profile, assumptions, SimulationScenario.BASE))
        .doesNotThrowAnyException();
    assertThat(result.years().get(0).rentalIncome()).isEqualByComparingTo("1");
    assertThat(result.years().get(1).rentalIncome()).isEqualByComparingTo("1.045");
  }

  @Test
  void keepsCanonicalRentalAndBondIncomeSeparate() {
    var service =
        new RetirementSimulationService();
    var bond =
        new ProjectedLongTermAsset(
            10L,
            "Bond",
            LongTermAssetTypeModel.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.USD,
            new BigDecimal("486000"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2020, 1, 1),
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("0.10"))),
            List.of(),
            LocalDate.of(2028, 12, 31),
            new BigDecimal("486000"),
            InterestTreatmentModel.PAY_OUT,
            new BigDecimal("0.20"),
            null,
            false);
    var profile = profile(BigDecimal.ZERO, List.of(bond), new BigDecimal("174804"));
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 67, 2026)
            .withRentalIncomeGrowthSpread(new BigDecimal("0.005"));

    var years = service.simulate(profile, assumptions, SimulationScenario.BASE).years();

    assertThat(years.get(0).rentalIncome()).isEqualByComparingTo("174804");
    assertThat(years.get(0).bondIncome()).isEqualByComparingTo("38880");
    assertThat(years.get(1).rentalIncome()).isEqualByComparingTo("180048.12");
    assertThat(years.get(1).rentalIncome()).isNotEqualByComparingTo("213684");
    assertThat(years.get(1).bondIncome()).isEqualByComparingTo("38880");
    assertThat(years.get(2).rentalIncome()).isEqualByComparingTo("185449.5636");
  }

  @Test
  void compoundsRentalIncomeThroughLongTermProjectionBoundary() {
    var service =
        new RetirementSimulationService();
    var profile = profile(BigDecimal.ZERO, List.of(), new BigDecimal("100"));
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 67, 2026)
            .withRentalIncomeGrowthSpread(new BigDecimal("0.005"));

    var years = service.simulate(profile, assumptions, SimulationScenario.BASE).years();

    assertThat(years.get(0).rentalIncome()).isEqualByComparingTo("100");
    assertThat(years.get(1).rentalIncome()).isEqualByComparingTo("103");
    assertThat(years.get(2).rentalIncome()).isEqualByComparingTo("106.09");
  }

  @Test
  void compoundsSpendingAtTheEffectiveRateAndAppliesExpenseProfileLevelsOnce() {
    var service =
        new RetirementSimulationService();
    var assumptions =
        SimulationAssumptions.defaults(profile(BigDecimal.ZERO, List.of()), 65, 67, 2026)
            .withRecurringSpending(new BigDecimal("240000"))
            .withInflationRate(new BigDecimal("0.025"))
            .withSpendingGrowthSpread(new BigDecimal("0.015"))
            .withExpenseProfile(
                new ExpenseProfile(List.of(new ExpenseProfileStep(1, new BigDecimal("0.85")))));

    var years =
        service.simulate(profile(BigDecimal.ZERO, List.of()), assumptions, SimulationScenario.BASE).years();

    assertThat(years.get(0).totalExpenses()).isEqualByComparingTo("240000");
    assertThat(years.get(1).totalExpenses()).isEqualByComparingTo("212160");
    assertThat(years.get(2).totalExpenses()).isEqualByComparingTo("220646.4");
  }

  @Test
  void preservesBondPayoutIncomeAndNetRenewalRate() {
    var service = new RetirementSimulationService();
    var bond =
        new ProjectedLongTermAsset(
            10L,
            "Bond",
            LongTermAssetTypeModel.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.USD,
            new BigDecimal("1000"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2020, 1, 1), null, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.10"))),
            List.of(),
            LocalDate.of(2028, 12, 31),
            new BigDecimal("1000"),
            InterestTreatmentModel.PAY_OUT,
            new BigDecimal("0.20"),
            null,
            false);
    var profile = profile(BigDecimal.ZERO, List.of(bond));
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 65, 2026).withRecurringSpending(new BigDecimal("80"));

    var year = service.simulate(profile, assumptions, SimulationScenario.BASE).years().getFirst();

    assertThat(year.bondIncome()).isEqualByComparingTo("80");
    assertThat(year.requiredPortfolioFunding()).isZero();
    assertThat(year.unfundedAmount()).isZero();
  }

  @Test
  void capitalizedBondIncomeDoesNotEnterCashIncome() {
    var service = new RetirementSimulationService();
    var bond = new ProjectedLongTermAsset(
        11L, "Capitalized bond", LongTermAssetTypeModel.BOND,
        EconomicBucket.FIXED_INCOME, CurrencyType.USD, new BigDecimal("1000"), Liquidity.LIQUID,
        List.of(new ProjectedLongTermAsset.Period(
            LocalDate.of(2020, 1, 1), null, null, BigDecimal.ZERO, new BigDecimal("0.10"))),
        List.of(), null, new BigDecimal("1000"), InterestTreatmentModel.CAPITALIZE,
        new BigDecimal("0.20"), null, false);
    var profile = profile(BigDecimal.ZERO, List.of(bond));
    var year = service.simulate(profile,
        SimulationAssumptions.defaults(profile, 65, 65, 2026)
            .withRecurringSpending(new BigDecimal("80")),
        SimulationScenario.BASE).years().getFirst();

    assertThat(year.bondIncome()).isZero();
    assertThat(year.capitalizedBondReturn()).isEqualByComparingTo("80");
    assertThat(year.requiredPortfolioFunding()).isEqualByComparingTo("80");
  }

  @Test
  void recordsPreRetirementContributionAndAppliesItBeforeInvestmentWithdrawal() {
    var service = new RetirementSimulationService();
    var profile = profile(new BigDecimal("100"), List.of());
    var assumptions =
        SimulationAssumptions.defaults(profile, 60, 61, 2026)
            .withRetirementAge(61)
            .withAnnualPreRetirementContribution(new BigDecimal("100"));

    var workingYear = service.simulate(profile, assumptions, SimulationScenario.BASE).years().getFirst();

    assertThat(workingYear.livingExpenses()).isZero();
    assertThat(workingYear.preRetirementContribution()).isEqualByComparingTo("100");
    assertThat(workingYear.equityEnd()).isEqualByComparingTo("206");
  }

  private static InvestmentProfile profile(
      BigDecimal marketPortfolioValue, List<ProjectedLongTermAsset> longTermAssets) {
    return profile(marketPortfolioValue, longTermAssets, BigDecimal.ZERO);
  }

  private static InvestmentProfile profile(
      BigDecimal marketPortfolioValue,
      List<ProjectedLongTermAsset> longTermAssets,
      BigDecimal rentalIncome) {
    return new InvestmentProfile(
        1L,
        CurrencyType.USD,
        marketPortfolioValue,
        longTermAssets.stream()
            .map(ProjectedLongTermAsset::currentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        marketPortfolioValue,
        BigDecimal.ZERO,
        rentalIncome,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        List.of(),
        longTermAssets,
        rentalIncome,
        BigDecimal.ZERO);
  }
}
