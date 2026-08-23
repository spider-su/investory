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

/** End-to-end bond treatment contract through the canonical retirement simulator. */
class RetirementBondTreatmentIntegrationTest {

  @Test
  void capitalizeProducesNoCashIncomeAndIncreasesBondCapitalByNetInterest() {
    var service = new RetirementSimulationService();
    var profile = profile(InterestTreatmentModel.CAPITALIZE);
    var year = service.simulate(profile, assumptions(profile), SimulationScenario.BASE)
        .years().getFirst();

    assertThat(year.bondIncome()).isZero();
    assertThat(year.funding().capitalizedBondReturn()).isEqualByComparingTo("80");
    assertThat(year.fixedIncomeStart()).isEqualByComparingTo("1000");
    assertThat(year.fixedIncomeEnd()).isEqualByComparingTo("1080");
  }

  @Test
  void payOutProducesCashIncomeAndDoesNotAlsoIncreaseBondPrincipal() {
    var service = new RetirementSimulationService();
    var profile = profile(InterestTreatmentModel.PAY_OUT);
    var year = service.simulate(profile, assumptions(profile), SimulationScenario.BASE)
        .years().getFirst();

    assertThat(year.bondIncome()).isEqualByComparingTo("80");
    assertThat(year.totalIncome()).isEqualByComparingTo("80");
    assertThat(year.funding().capitalizedBondReturn()).isZero();
    assertThat(year.fixedIncomeStart()).isEqualByComparingTo("1000");
    assertThat(year.fixedIncomeEnd()).isEqualByComparingTo("1000");
  }

  private static SimulationAssumptions assumptions(InvestmentProfile profile) {
    return SimulationAssumptions.defaults(profile, 65, 65, 2026)
        .withRecurringSpending(BigDecimal.ZERO)
        .withInflationRate(BigDecimal.ZERO)
        .withSpendingGrowthSpread(BigDecimal.ZERO)
        .withRentalIncomeGrowthSpread(BigDecimal.ZERO)
        .withFixedIncomeReturnRate(new BigDecimal("0.04"))
        .withEquityReturnRate(BigDecimal.ZERO)
        .withRetirementAge(65);
  }

  private static InvestmentProfile profile(InterestTreatmentModel treatment) {
    var bond = new ProjectedLongTermAsset(
        1L,
        "Bond",
        LongTermAssetTypeModel.BOND,
        EconomicBucket.FIXED_INCOME,
        CurrencyType.PLN,
        new BigDecimal("1000"),
        Liquidity.LIQUID,
        List.of(new ProjectedLongTermAsset.Period(
            LocalDate.of(2020, 1, 1), null, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("0.10"))),
        List.of(),
        null,
        new BigDecimal("1000"),
        treatment,
        new BigDecimal("0.20"),
        null,
        false);
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        BigDecimal.ZERO,
        new BigDecimal("1000"),
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("1000"),
        List.of(),
        List.of(bond),
        BigDecimal.ZERO,
        treatment == InterestTreatmentModel.PAY_OUT ? new BigDecimal("80") : BigDecimal.ZERO);
  }
}
