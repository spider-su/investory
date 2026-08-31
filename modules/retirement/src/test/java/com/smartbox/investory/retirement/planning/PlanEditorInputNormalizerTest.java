package com.smartbox.investory.retirement.planning;

import static com.smartbox.investory.retirement.planning.PlanEditorInputTestFactory.input;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Plan Editor Input Normalizer")
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
              List.of(),
              null,
              null,
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
              com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY),
          40,
          95,
          2026);

  @DisplayName("converts Semantic Percentage Points And Expense Levels On The Backend")
  @Test
  void convertsSemanticPercentagePointsAndExpenseLevelsOnTheBackend() {
    var normalized =
        normalizer.normalize(
            input(
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

  @DisplayName("returns Warnings But Rejects Hard Invalid Values")
  @Test
  void returnsWarningsButRejectsHardInvalidValues() {
    var normalized =
        normalizer.normalize(
            input(Map.of("inflation", "35", "equityReturn", "30")), base, CurrencyType.USD);
    assertEquals(4, normalized.warnings().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> normalizer.normalize(input(Map.of("inflation", "-100")), base, CurrencyType.USD));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            normalizer.normalize(
                input(Map.of("expenseProfile", "40:100;40:85")), base, CurrencyType.USD));
  }

  @DisplayName("sorts Semantic Ages And Warns For Unusual Valid Stages")
  @Test
  void sortsSemanticAgesAndWarnsForUnusualValidStages() {
    var normalized =
        normalizer.normalize(
            input(
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

  @DisplayName("rejects Invalid Semantic Stage Ages And Levels")
  @Test
  void rejectsInvalidSemanticStageAgesAndLevels() {
    for (String profile :
        List.of("39:100", "96:100", "40:100;40:85", "40:-1", "40:201", "40.5:100")) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              normalizer.normalize(
                  input(Map.of("expenseProfile", profile)), base, CurrencyType.USD),
          profile);
    }
  }

  @DisplayName("accepts Zero Percent As The Lower Spending Level Boundary")
  @Test
  void acceptsZeroPercentAsTheLowerSpendingLevelBoundary() {
    var normalized =
        normalizer.normalize(input(Map.of("expenseProfile", "40:0")), base, CurrencyType.USD);

    assertEquals(
        BigDecimal.ZERO, normalized.assumptions().expenseProfile().steps().getFirst().factor());
  }

  @DisplayName("requires And Preserves Manual Projected Income Values")
  @Test
  void requiresAndPreservesManualProjectedIncomeValues() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            normalizer.normalize(
                input(Map.of("rentalIncomeMode", "MANUAL")), base, CurrencyType.USD));

    var normalized =
        normalizer.normalize(
            input(
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
