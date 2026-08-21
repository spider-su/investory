package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.retirement.planning.ForwardSimulationInput;
import com.smartbox.investory.retirement.planning.ForwardSimulationInputService;
import com.smartbox.investory.retirement.planning.PlanningCurrencyPresentationService;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read-only developer projection over the same forward simulation boundary used by Simulation. */
@Service
public class PlanEditorPreviewService {
  private final ForwardSimulationInputService forwardInputs;
  private final RetirementSimulationService simulations;
  private final LongTermAssetAnnualSnapshotReader longTermAssets;
  private final PlanningCurrencyPresentationService presentation;
  private final Clock clock;

  public PlanEditorPreviewService(
      ForwardSimulationInputService forwardInputs,
      RetirementSimulationService simulations,
      LongTermAssetAnnualSnapshotReader longTermAssets,
      PlanningCurrencyPresentationService presentation,
      Clock clock) {
    this.forwardInputs = forwardInputs;
    this.simulations = simulations;
    this.longTermAssets = longTermAssets;
    this.presentation = presentation;
    this.clock = clock;
  }

  public PlanEditorPreview preview(
      InvestmentProfile profile, SimulationAssumptions assumptions, CurrencyType displayCurrency) {
    ForwardSimulationInput forward = forwardInputs.prepare(profile, assumptions);
    SimulationAssumptions projected = forward.forwardAssumptions().orElse(assumptions);
    SimulationResult result =
        forward.forwardAssumptions().isPresent()
            ? simulations.simulate(forward.bridgedProfile(), projected, SimulationScenario.BASE)
            : new SimulationResult(
                SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of());
    LongTermAssetAnnualSnapshotModel facts = currentFacts(profile);
    int currentYear = Year.now(clock).getValue();
    int retirementYear = ForwardSimulationContextFactory.retirementYear(assumptions);
    SimulationYear first = result.years().isEmpty() ? null : result.years().get(0);
    BigDecimal nextYearCosts =
        first == null ? null : displayCanonical(first.totalExpenses(), displayCurrency);
    return new PlanEditorPreview(
        ForwardSimulationContextFactory.currentPlanningAge(assumptions, currentYear),
        retirementYear,
        Math.max(retirementYear - currentYear, 0),
        Math.max(assumptions.endAge() - assumptions.currentAge() + 1, 0),
        displayCanonical(
            assumptions
                .annualLivingExpenses()
                .divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP),
            displayCurrency),
        displayCanonical(assumptions.annualLivingExpenses(), displayCurrency),
        displayCanonical(assumptions.annualDiscretionaryExpenses(), displayCurrency),
        displayCanonical(
            assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
            displayCurrency),
        nextYearCosts,
        displayCanonical(facts.rentalIncome(), displayCurrency),
        displayCanonical(facts.bondIncome(), displayCurrency),
        assumptions,
        result.failureAge(),
        result.years().stream()
            .map(row -> year(row, projected.startYear(), displayCurrency))
            .toList(),
        first == null ? null : year(first, projected.startYear(), displayCurrency));
  }

  /** Current Long-term Assets facts used by the editor's read-only fields. */
  public LongTermAssetAnnualSnapshotModel currentFacts(InvestmentProfile profile) {
    return longTermAssets.currentAnnualSnapshot(profile.portfolioId(), LocalDate.now(clock));
  }

  private PreviewYear year(
      SimulationYear row, int projectedStartYear, CurrencyType displayCurrency) {
    return new PreviewYear(
        projectedStartYear + row.year(),
        row.age(),
        row.lifecyclePhase().name(),
        displayCanonical(row.totalExpenses(), displayCurrency),
        displayCanonical(row.employmentIncome(), displayCurrency),
        displayCanonical(row.rentalIncome(), displayCurrency),
        displayCanonical(row.bondIncome(), displayCurrency),
        displayCanonical(row.pensionIncome(), displayCurrency),
        displayCanonical(row.preRetirementContribution(), displayCurrency),
        displayCanonical(row.totalIncome(), displayCurrency),
        displayCanonical(row.recurringFundingGap(), displayCurrency),
        displayCanonical(row.safeReserveTarget(), displayCurrency),
        displayCanonical(row.safeReserveStart(), displayCurrency),
        displayCanonical(row.safeReserveEnd(), displayCurrency),
        displayCanonical(row.equityToFixedIncomeTransfer(), displayCurrency),
        displayCanonical(row.emergencyEquityWithdrawal(), displayCurrency),
        displayCanonical(row.cashStart(), displayCurrency),
        displayCanonical(row.cashEnd(), displayCurrency),
        displayCanonical(row.fixedIncomeStart(), displayCurrency),
        displayCanonical(row.fixedIncomeEnd(), displayCurrency),
        displayCanonical(row.equityStart(), displayCurrency),
        displayCanonical(row.equityEnd(), displayCurrency),
        displayCanonical(row.equityGain(), displayCurrency),
        row.equityReturnRate());
  }

  private BigDecimal displayCanonical(BigDecimal value, CurrencyType displayCurrency) {
    return presentation.toDisplay(value, displayCurrency);
  }

  public record PlanEditorPreview(
      int currentPlanningAge,
      int retirementYear,
      int yearsUntilRetirement,
      int planHorizonYears,
      BigDecimal monthlyLivingCosts,
      BigDecimal annualLivingCosts,
      BigDecimal annualExtras,
      BigDecimal totalAnnualCosts,
      BigDecimal nextYearCosts,
      BigDecimal rentalIncome,
      BigDecimal bondIncome,
      SimulationAssumptions assumptions,
      Integer firstFailureAge,
      List<PreviewYear> years,
      PreviewYear firstProjectedYear) {}

  public record PreviewYear(
      int year,
      int age,
      String lifecycle,
      BigDecimal annualCosts,
      BigDecimal employmentIncome,
      BigDecimal rentalIncome,
      BigDecimal bondIncome,
      BigDecimal pension,
      BigDecimal contribution,
      BigDecimal totalIncome,
      BigDecimal portfolioNeed,
      BigDecimal reserveTarget,
      BigDecimal reserveBefore,
      BigDecimal reserveAfter,
      BigDecimal equityHarvest,
      BigDecimal emergencyEquityWithdrawal,
      BigDecimal cashStart,
      BigDecimal cashEnd,
      BigDecimal fixedIncomeStart,
      BigDecimal fixedIncomeEnd,
      BigDecimal equityStart,
      BigDecimal equityEnd,
      BigDecimal equityGain,
      BigDecimal equityReturn) {}
}
