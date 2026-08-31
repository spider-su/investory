package com.smartbox.investory.ui.retirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.retirement.planning.PlanningCurrencyPresentationService;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanEditorInputNormalizerTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private final PlanEditorInputNormalizer normalizer =
      new PlanEditorInputNormalizer(
          new PlanningCurrencyPresentationService(
              org.mockito.Mockito.mock(CurrencyConversion.class), clock),
          clock);
  private final SimulationAssumptions base =
      SimulationAssumptions.defaults(
          new InvestmentProfile(
              1L,
              CurrencyType.USD,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              List.of(),
              List.of()),
          40,
          95,
          2026);

  @Test
  void convertsSemanticPercentagePointsAndExpenseLevelsOnTheBackend() {
    var normalized =
        normalizer.normalize(
            PlanEditorInput.from(
                Map.of(
                    "inflation",
                    "3.0",
                    "fixedIncomeReturn",
                    "4.3",
                    "equityReturn",
                    "8.5",
                    "rentalIncomeGrowthSpread",
                    "-2.0",
                    "spendingGrowthSpread",
                    "-1.5",
                    "expenseProfile",
                    "40:100;60:85")),
            base,
            CurrencyType.USD);
    assertEquals(new BigDecimal("0.03"), normalized.assumptions().inflationRate());
    assertEquals(new BigDecimal("0.085"), normalized.assumptions().equityReturnRate());
    assertEquals(new BigDecimal("0.043"), normalized.assumptions().fixedIncomeReturnRate());
    assertEquals(new BigDecimal("-0.02"), normalized.assumptions().rentalIncomeGrowthSpread());
    assertEquals(
        new BigDecimal("0.01"), normalized.assumptions().effectiveRentalIncomeGrowthRate());
    assertEquals(new BigDecimal("0.015"), normalized.assumptions().effectiveSpendingGrowthRate());
    var stage = normalized.assumptions().expenseProfile().steps().get(1);
    assertEquals(20, stage.fromYear());
    assertEquals(new BigDecimal("0.85"), stage.factor());
    assertEquals(2045, 2025 + stage.fromYear());
  }

  @Test
  void returnsWarningsButRejectsHardInvalidValues() {
    var normalized =
        normalizer.normalize(
            PlanEditorInput.from(Map.of("inflation", "35", "equityReturn", "30")),
            base,
            CurrencyType.USD);
    assertEquals(4, normalized.warnings().size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            normalizer.normalize(
                PlanEditorInput.from(Map.of("inflation", "-100")), base, CurrencyType.USD));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            normalizer.normalize(
                PlanEditorInput.from(Map.of("expenseProfile", "40:100;40:85")),
                base,
                CurrencyType.USD));
  }

  @Test
  void sortsSemanticAgesAndWarnsForUnusualValidStages() {
    var normalized =
        normalizer.normalize(
            PlanEditorInput.from(
                Map.of(
                    "startYear",
                    "2025",
                    "ageAtPlanStart",
                    "40",
                    "retirementAge",
                    "65",
                    "expenseProfile",
                    "60:150;50:40")),
            base,
            CurrencyType.USD);

    assertEquals(
        List.of(10, 20),
        normalized.assumptions().expenseProfile().steps().stream()
            .map(step -> step.fromYear())
            .toList());
    assertEquals(4, normalized.warnings().size());
  }

  @Test
  void rejectsInvalidSemanticStageAgesAndLevels() {
    for (String profile :
        List.of("39:100", "96:100", "40:100;40:85", "40:-1", "40:201", "40.5:100")) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              normalizer.normalize(
                  PlanEditorInput.from(Map.of("expenseProfile", profile)), base, CurrencyType.USD),
          profile);
    }
  }

  @Test
  void acceptsZeroPercentAsTheLowerSpendingLevelBoundary() {
    var normalized =
        normalizer.normalize(
            PlanEditorInput.from(Map.of("expenseProfile", "40:0")), base, CurrencyType.USD);

    assertEquals(
        BigDecimal.ZERO, normalized.assumptions().expenseProfile().steps().getFirst().factor());
  }

  @Test
  void requiresAndPreservesManualProjectedIncomeValues() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            normalizer.normalize(
                PlanEditorInput.from(Map.of("rentalIncomeMode", "MANUAL")),
                base,
                CurrencyType.USD));

    var normalized =
        normalizer.normalize(
            PlanEditorInput.from(
                Map.of(
                    "rentalIncomeMode",
                    "MANUAL",
                    "manualRentalIncome",
                    "120000",
                    "bondCashIncomeMode",
                    "MANUAL",
                    "manualBondCashIncome",
                    "24000")),
            base,
            CurrencyType.USD);

    assertEquals(
        new BigDecimal("120000"),
        normalized.assumptions().projectedIncomePolicy().manualRentalIncome());
    assertEquals(
        new BigDecimal("24000"),
        normalized.assumptions().projectedIncomePolicy().manualBondCashIncome());
  }
}
