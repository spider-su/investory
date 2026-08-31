package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

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

/** End-to-end bond treatment contract through the canonical retirement simulator. */
@DisplayName("Retirement Bond Treatment Integration")
class RetirementBondTreatmentIntegrationTest {

  @DisplayName("capitalize Produces No Cash Income And Increases Bond Capital By Plan Return")
  @Test
  void capitalizeProducesNoCashIncomeAndIncreasesBondCapitalByPlanReturn() {
    var service = new RetirementSimulationService();
    var profile = profile(InterestTreatment.CAPITALIZE);
    var year =
        service.simulate(profile, assumptions(profile), SimulationScenario.BASE).years().getFirst();

    assertThat(year.bondIncome()).isZero();
    assertThat(year.funding().capitalizedBondReturn()).isEqualByComparingTo("40");
    assertThat(year.fixedIncomeStart()).isEqualByComparingTo("1000");
    assertThat(year.fixedIncomeEnd()).isEqualByComparingTo("1040");
  }

  @DisplayName("pay Out Produces Cash Income And Does Not Also Increase Bond Principal")
  @Test
  void payOutProducesCashIncomeAndDoesNotAlsoIncreaseBondPrincipal() {
    var service = new RetirementSimulationService();
    var profile = profile(InterestTreatment.PAY_OUT);
    var year =
        service.simulate(profile, assumptions(profile), SimulationScenario.BASE).years().getFirst();

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

  private static InvestmentProfile profile(InterestTreatment treatment) {
    var bond =
        new ProjectedLongTermAsset(
            1L,
            "Bond",
            LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
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
        new BigDecimal("1000"),
        List.of(),
        BigDecimal.ZERO,
        treatment == InterestTreatment.PAY_OUT ? new BigDecimal("80") : BigDecimal.ZERO,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(bond),
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
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            new BigDecimal("1000")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }
}
