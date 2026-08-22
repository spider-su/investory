package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.smartbox.investory.investment.application.InvestmentAnnualProjectionService;
import com.smartbox.investory.longterm.application.service.LongTermAnnualProjectionService;
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
  void supportsAnnualRentalIncomeThatDoesNotDivideEvenlyIntoMonths() {
    var service =
        new RetirementSimulationService(
            new LongTermAnnualProjectionService(), new InvestmentAnnualProjectionService());
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
    assertThat(result.years().get(1).rentalIncome()).isEqualByComparingTo("1.02");
  }

  @Test
  void keepsCanonicalRentalAndBondIncomeSeparate() {
    var service =
        new RetirementSimulationService(
            new LongTermAnnualProjectionService(), new InvestmentAnnualProjectionService());
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
            .withRentalIncomeGrowthRate(new BigDecimal("0.005"));

    var years = service.simulate(profile, assumptions, SimulationScenario.BASE).years();

    assertThat(years.get(0).rentalIncome()).isEqualByComparingTo("174804");
    assertThat(years.get(0).bondIncome()).isEqualByComparingTo("38880");
    assertThat(years.get(1).rentalIncome()).isEqualByComparingTo("175678.02");
    assertThat(years.get(1).rentalIncome()).isNotEqualByComparingTo("213684");
    assertThat(years.get(1).bondIncome()).isEqualByComparingTo("38880");
  }

  @Test
  void compoundsRentalIncomeThroughLongTermProjectionBoundary() {
    var service =
        new RetirementSimulationService(
            new LongTermAnnualProjectionService(), new InvestmentAnnualProjectionService());
    var profile = profile(BigDecimal.ZERO, List.of(), new BigDecimal("100"));
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 67, 2026)
            .withRentalIncomeGrowthRate(new BigDecimal("0.005"));

    var years = service.simulate(profile, assumptions, SimulationScenario.BASE).years();

    assertThat(years.get(0).rentalIncome()).isEqualByComparingTo("100");
    assertThat(years.get(1).rentalIncome()).isEqualByComparingTo("100.5");
    assertThat(years.get(2).rentalIncome()).isEqualByComparingTo("101.0025");
  }

  @Test
  void preservesBondPayoutIncomeAndNetRenewalRate() {
    var service = new RetirementSimulationService(new LongTermAnnualProjectionService(), new InvestmentAnnualProjectionService());
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
  void recordsPreRetirementContributionAndAppliesItBeforeInvestmentWithdrawal() {
    var service = new RetirementSimulationService(new LongTermAnnualProjectionService(), new InvestmentAnnualProjectionService());
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
