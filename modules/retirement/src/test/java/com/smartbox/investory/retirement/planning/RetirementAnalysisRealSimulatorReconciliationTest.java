package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.RetirementAgeAnalysis;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationEvaluation;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.api.model.SimulationSensitivityAnalysis;
import com.smartbox.investory.retirement.api.model.SustainableSpendingAnalysis;
import com.smartbox.investory.retirement.simulation.DeterministicAnalysisContext;
import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysisService;
import com.smartbox.investory.retirement.simulation.RetirementSimulationService;
import com.smartbox.investory.retirement.simulation.SimulationEvaluationService;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysisService;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysisService;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retirement Analysis Real Simulator Reconciliation")
class RetirementAnalysisRealSimulatorReconciliationTest {

  @DisplayName(
      "every Analysis Consumer Reconciles To One Real Canonical Base Across Display Currency")
  @Test
  void everyAnalysisConsumerReconcilesToOneRealCanonicalBaseAcrossDisplayCurrency() {
    InvestmentProfile profile = profile();
    SimulationAssumptions assumptions = assumptions(profile);
    SimulationEvaluationService evaluations =
        new SimulationEvaluationService(new RetirementSimulationService());
    SimulationEvaluation canonical =
        evaluations.evaluate(profile, assumptions, SimulationScenario.BASE, 2026);
    DeterministicAnalysisContext context =
        new DeterministicAnalysisContext(profile, assumptions, 2026, canonical);

    SimulationSensitivityAnalysis sensitivity =
        new SimulationSensitivityAnalysisService(evaluations).analyze(context);
    SustainableSpendingAnalysis spending =
        new SustainableSpendingAnalysisService(evaluations).analyze(context);
    RetirementAgeAnalysis retirement =
        new RetirementAgeAnalysisService(evaluations).analyze(context);

    assertThat(sensitivity.baseline()).isSameAs(canonical);
    assertThat(sensitivity.drivers()).allMatch(driver -> driver.baseline() == canonical);
    assertThat(spending.base().currentSpendingAboveLimit()).isEqualTo(!canonical.sustainable());
    assertThat(retirement.base().plannedRetirementSustainable()).isEqualTo(canonical.sustainable());

    PlanningCurrencyPresentationService presentation = presentationAtFourToOneRate();
    SustainableSpendingAnalysisMoney spendingMoney =
        presentation.displaySustainableSpending(spending, CurrencyType.PLN);
    RetirementAgeAnalysisMoney retirementMoney =
        presentation.displayRetirementAgeAnalysis(retirement);
    SimulationSensitivityAnalysisMoney sensitivityMoney =
        presentation.displaySensitivity(sensitivity, CurrencyType.PLN);

    assertThat(spendingMoney.baseLimit())
        .isEqualTo(
            PlanningPresentation.wholeNumber(
                spending.base().sustainableSpending().multiply(new BigDecimal("4"))));
    assertThat(spendingMoney.baseHeadroom())
        .contains(
            PlanningPresentation.wholeNumber(
                spending.base().headroom().multiply(new BigDecimal("4")).abs()));
    assertThat(retirementMoney.base().planned())
        .isEqualTo(
            "Age "
                + retirement.base().plannedRetirementAge()
                + " · "
                + retirement.base().plannedRetirementYear());
    assertThat(sensitivityMoney.drivers()).isNotEmpty();
    assertThat(sensitivityMoney.drivers().getFirst().base().minimumLiquidAssets())
        .isEqualTo(
            canonical
                    .sustainability()
                    .minimumSpendableAssets()
                    .multiply(new BigDecimal("4"))
                    .stripTrailingZeros()
                    .toPlainString()
                + " PLN");
  }

  private static PlanningCurrencyPresentationService presentationAtFourToOneRate() {
    CurrencyConversion rates = mock(CurrencyConversion.class);
    when(rates.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.PLN),
            eq(CurrencyType.USD),
            any(LocalDate.class)))
        .thenAnswer(
            invocation -> ((BigDecimal) invocation.getArgument(0)).multiply(new BigDecimal("4")));
    return new PlanningCurrencyPresentationService(
        rates, Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC));
  }

  private static SimulationAssumptions assumptions(InvestmentProfile profile) {
    return SimulationAssumptions.defaults(profile, 40, 55, 2026).toBuilder()
        .recurringSpending(new BigDecimal("60000"))
        .retirementAge(45)
        .annualEmploymentIncome(new BigDecimal("120000"))
        .annualPreRetirementContribution(new BigDecimal("20000"))
        .pensionStartAge(50)
        .annualPension(new BigDecimal("30000"))
        .build();
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.USD,
        new BigDecimal("1000000"),
        BigDecimal.ZERO,
        new BigDecimal("1000000"),
        new BigDecimal("100000"),
        BigDecimal.ZERO,
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH,
                new BigDecimal("100000"),
                BigDecimal.ONE,
                Liquidity.LIQUID,
                Liquidity.LIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM),
            new ProfileAllocation(
                EconomicBucket.EQUITY,
                new BigDecimal("900000"),
                BigDecimal.ONE,
                Liquidity.LIQUID,
                Liquidity.LIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM)),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (new BigDecimal("100000") == null ? java.math.BigDecimal.ZERO : new BigDecimal("100000")),
        new BigDecimal("1000000")
            .subtract(
                (new BigDecimal("100000") == null
                    ? java.math.BigDecimal.ZERO
                    : new BigDecimal("100000")))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            new BigDecimal("1000000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("1000000")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }
}
