package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retirement Simulation Annual Rental Income")
class RetirementSimulationAnnualRentalIncomeTest {

  @DisplayName("manual Rental Income Replaces The Frozen Source Value")
  @Test
  void manualRentalIncomeReplacesTheFrozenSourceValue() {
    var service = new RetirementSimulationService();
    var profile = profile(BigDecimal.ZERO, List.of(), new BigDecimal("100"));
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 65, 2026)
            .withProjectedIncomePolicy(
                new ProjectedIncomePolicy(
                    ProjectedIncomePolicy.IncomeMode.MANUAL,
                    new BigDecimal("250"),
                    ProjectedIncomePolicy.IncomeMode.SOURCE,
                    null));

    var year = service.simulate(profile, assumptions, SimulationScenario.BASE).years().getFirst();

    assertThat(year.rentalIncome()).isEqualByComparingTo("250");
    assertThat(year.totalIncome()).isEqualByComparingTo("250");
  }

  @DisplayName("first Projected Year Advances Current Rental Baseline")
  @Test
  void firstProjectedYearAdvancesCurrentRentalBaseline() {
    var service = new RetirementSimulationService();
    var profile = profile(BigDecimal.ZERO, List.of(), new BigDecimal("174803.62"));
    int firstProjectedYear = 2027;
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 65, firstProjectedYear)
            .withInflationRate(new BigDecimal("0.030"))
            .withRentalIncomeGrowthSpread(new BigDecimal("-0.020"));

    var year =
        service.simulate(profile, assumptions, SimulationScenario.BASE, 2026).years().getFirst();

    assertThat(year.year()).isEqualTo(firstProjectedYear);
    assertThat(year.rentalIncome()).isEqualByComparingTo("176551.6562");
  }

  @DisplayName("explicit Baseline Year Makes Simulation Independent Of Wall Clock")
  @Test
  void explicitBaselineYearMakesSimulationIndependentOfWallClock() {
    var service = new RetirementSimulationService();
    var profile = profile(BigDecimal.ZERO, List.of(), new BigDecimal("100"));
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 65, 2027)
            .withInflationRate(new BigDecimal("0.030"))
            .withRentalIncomeGrowthSpread(new BigDecimal("-0.020"));

    var first = service.simulate(profile, assumptions, SimulationScenario.BASE, 2026);
    var repeated = service.simulate(profile, assumptions, SimulationScenario.BASE, 2026);
    var olderBaseline = service.simulate(profile, assumptions, SimulationScenario.BASE, 2025);

    assertThat(repeated).isEqualTo(first);
    assertThat(first.years().getFirst().rentalIncome()).isEqualByComparingTo("101");
    assertThat(olderBaseline.years().getFirst().rentalIncome()).isEqualByComparingTo("102.01");
  }

  @DisplayName("supports Annual Rental Income That Does Not Divide Evenly Into Months")
  @Test
  void supportsAnnualRentalIncomeThatDoesNotDivideEvenlyIntoMonths() {
    var service = new RetirementSimulationService();
    var profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            BigDecimal.ONE,
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
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    var assumptions = SimulationAssumptions.defaults(profile, 65, 66, 2026);

    var result = service.simulate(profile, assumptions, SimulationScenario.BASE);

    assertThatCode(() -> service.simulate(profile, assumptions, SimulationScenario.BASE))
        .doesNotThrowAnyException();
    assertThat(result.years().get(0).rentalIncome()).isEqualByComparingTo("1");
    assertThat(result.years().get(1).rentalIncome()).isEqualByComparingTo("1.045");
  }

  @DisplayName("keeps Canonical Rental And Bond Income Separate")
  @Test
  void keepsCanonicalRentalAndBondIncomeSeparate() {
    var service = new RetirementSimulationService();
    var bond =
        new ProjectedLongTermAsset(
            10L,
            "Bond",
            LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.USD,
            new BigDecimal("486000"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2020, 1, 1),
                    null,
                    new BigDecimal("38880"),
                    BigDecimal.ZERO,
                    new BigDecimal("0.10"),
                    null,
                    false)),
            List.of(),
            LocalDate.of(2028, 12, 31),
            new BigDecimal("486000"),
            InterestTreatment.PAY_OUT,
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

  @DisplayName("compounds Rental Income Through Long Term Projection Boundary")
  @Test
  void compoundsRentalIncomeThroughLongTermProjectionBoundary() {
    var service = new RetirementSimulationService();
    var profile = profile(BigDecimal.ZERO, List.of(), new BigDecimal("100"));
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 67, 2026)
            .withRentalIncomeGrowthSpread(new BigDecimal("0.005"));

    var years = service.simulate(profile, assumptions, SimulationScenario.BASE).years();

    assertThat(years.get(0).rentalIncome()).isEqualByComparingTo("100");
    assertThat(years.get(1).rentalIncome()).isEqualByComparingTo("103");
    assertThat(years.get(2).rentalIncome()).isEqualByComparingTo("106.09");
  }

  @DisplayName("compounds Spending At The Effective Rate And Applies Expense Profile Levels Once")
  @Test
  void compoundsSpendingAtTheEffectiveRateAndAppliesExpenseProfileLevelsOnce() {
    var service = new RetirementSimulationService();
    var assumptions =
        SimulationAssumptions.defaults(profile(BigDecimal.ZERO, List.of()), 65, 67, 2026)
            .withRecurringSpending(new BigDecimal("240000"))
            .withInflationRate(new BigDecimal("0.025"))
            .withSpendingGrowthSpread(new BigDecimal("0.015"))
            .withExpenseProfile(
                new ExpenseProfile(List.of(new ExpenseProfileStep(1, new BigDecimal("0.85")))));

    var years =
        service
            .simulate(profile(BigDecimal.ZERO, List.of()), assumptions, SimulationScenario.BASE)
            .years();

    assertThat(years.get(0).totalExpenses()).isEqualByComparingTo("240000");
    assertThat(years.get(1).totalExpenses()).isEqualByComparingTo("212160");
    assertThat(years.get(2).totalExpenses()).isEqualByComparingTo("220646.4");
  }

  @DisplayName("preserves Bond Payout Income And Net Renewal Rate")
  @Test
  void preservesBondPayoutIncomeAndNetRenewalRate() {
    var service = new RetirementSimulationService();
    var bond =
        new ProjectedLongTermAsset(
            10L,
            "Bond",
            LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.USD,
            new BigDecimal("1000"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2020, 1, 1),
                    null,
                    new BigDecimal("80"),
                    BigDecimal.ZERO,
                    new BigDecimal("0.10"),
                    null,
                    false)),
            List.of(),
            LocalDate.of(2028, 12, 31),
            new BigDecimal("1000"),
            InterestTreatment.PAY_OUT,
            new BigDecimal("0.20"),
            null,
            false);
    var profile = profile(BigDecimal.ZERO, List.of(bond));
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 65, 2026)
            .withRecurringSpending(new BigDecimal("80"));

    var year = service.simulate(profile, assumptions, SimulationScenario.BASE).years().getFirst();

    assertThat(year.bondIncome()).isEqualByComparingTo("80");
    assertThat(year.requiredPortfolioFunding()).isZero();
    assertThat(year.unfundedAmount()).isZero();
  }

  @DisplayName("capitalized Bond Income Does Not Enter Cash Income")
  @Test
  void capitalizedBondIncomeDoesNotEnterCashIncome() {
    var service = new RetirementSimulationService();
    var bond =
        new ProjectedLongTermAsset(
            11L,
            "Capitalized bond",
            LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.USD,
            new BigDecimal("1000"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2020, 1, 1),
                    null,
                    new BigDecimal("40"),
                    BigDecimal.ZERO,
                    new BigDecimal("0.10"),
                    null,
                    false)),
            List.of(),
            null,
            new BigDecimal("1000"),
            InterestTreatment.CAPITALIZE,
            new BigDecimal("0.20"),
            null,
            false);
    var profile = profile(BigDecimal.ZERO, List.of(bond));
    var year =
        service
            .simulate(
                profile,
                SimulationAssumptions.defaults(profile, 65, 65, 2026)
                    .withRecurringSpending(new BigDecimal("80")),
                SimulationScenario.BASE)
            .years()
            .getFirst();

    assertThat(year.bondIncome()).isZero();
    // Capitalized source yield remains observed data; projected capital uses the plan Bond return
    // assumption (the default here is 4%), while the full 80 spending gap remains unfunded by cash.
    assertThat(year.capitalizedBondReturn()).isEqualByComparingTo("40");
    assertThat(year.requiredPortfolioFunding()).isEqualByComparingTo("80");
  }

  @DisplayName("records Pre Retirement Contribution And Applies It Before Investment Withdrawal")
  @Test
  void recordsPreRetirementContributionAndAppliesItBeforeInvestmentWithdrawal() {
    var service = new RetirementSimulationService();
    var profile = profile(new BigDecimal("100"), List.of());
    var assumptions =
        SimulationAssumptions.defaults(profile, 60, 61, 2026)
            .withRetirementAge(61)
            .withAnnualPreRetirementContribution(new BigDecimal("100"));

    var workingYear =
        service.simulate(profile, assumptions, SimulationScenario.BASE).years().getFirst();

    assertThat(workingYear.coreExpenses()).isZero();
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
        BigDecimal.ZERO,
        List.of(),
        rentalIncome,
        BigDecimal.ZERO,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            longTermAssets,
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
        marketPortfolioValue
            .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            marketPortfolioValue,
            rentalIncome,
            longTermAssets.stream()
                .map(ProjectedLongTermAsset::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add),
            BigDecimal.ZERO,
            marketPortfolioValue),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }
}
