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
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read-only developer projection over the same forward simulation boundary used by Simulation. */
@Service
public class PlanEditorPreviewService {
  private static final LongTermAssetAnnualSnapshotModel NO_LONG_TERM_FACTS =
      new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null);
  private final ForwardSimulationInputService forwardInputs;
  private final RetirementSimulation simulations;
  private final LongTermAssetAnnualSnapshotReader longTermAssets;
  private final PlanningCurrencyPresentationService presentation;
  private final Clock clock;

  public PlanEditorPreviewService(
      ForwardSimulationInputService forwardInputs,
      RetirementSimulation simulations,
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
    LongTermAssetAnnualSnapshotModel facts = facts(currentFacts(profile));
    int currentYear = Year.now(clock).getValue();
    int retirementYear = ForwardSimulationContextFactory.retirementYear(assumptions);
    SimulationYear first = result.years().isEmpty() ? null : result.years().get(0);
    int currentPlanningAge =
        ForwardSimulationContextFactory.currentPlanningAge(assumptions, currentYear);
    List<PreviewYear> previewYears = new ArrayList<>();
    for (int year = assumptions.planStartYear(); year < currentYear; year++) {
      previewYears.add(
          historicalYear(
              year,
              assumptions.ageAtPlanStart() + year - assumptions.planStartYear(),
              facts(longTermAssets.historicalAnnualSnapshot(profile.portfolioId(), year)),
              assumptions,
              displayCurrency));
    }
    previewYears.add(
        currentYear(
            currentYear,
            currentPlanningAge,
            facts,
            assumptions,
            forward.currentYearBridge(),
            displayCurrency));
    previewYears.addAll(
        result.years().stream()
            .filter(row -> row.year() > currentYear)
            .map(row -> year(row, displayCurrency))
            .toList());
    BigDecimal nextYearCosts =
        first == null ? null : displayCanonical(first.totalExpenses(), displayCurrency);
    return new PlanEditorPreview(
        assumptions.planStartYear(),
        assumptions.ageAtPlanStart(),
        currentYear,
        currentPlanningAge,
        retirementYear,
        Math.max(retirementYear - currentYear, 0),
        Math.max(assumptions.endAge() - assumptions.ageAtPlanStart() + 1, 0),
        assumptions.inflationRate(),
        assumptions.rentalIncomeGrowthSpread(),
        assumptions.effectiveRentalIncomeGrowthRate(),
        assumptions.spendingGrowthSpread(),
        assumptions.effectiveSpendingGrowthRate(),
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
        List.copyOf(previewYears),
        first == null ? null : year(first, displayCurrency));
  }

  /** Current Long-term Assets facts used by the editor's read-only fields. */
  public LongTermAssetAnnualSnapshotModel currentFacts(InvestmentProfile profile) {
    return longTermAssets.currentAnnualSnapshot(profile.portfolioId(), LocalDate.now(clock));
  }

  private PreviewYear year(
      SimulationYear row, CurrencyType displayCurrency) {
    return new PreviewYear(
        row.year(),
        row.age(),
        "PROJECTED",
        row.lifecyclePhase().name(),
        displayCanonical(row.totalExpenses(), displayCurrency),
        displayCanonical(row.employmentIncome(), displayCurrency),
        displayCanonical(row.rentalIncome(), displayCurrency),
        displayCanonical(row.bondIncome(), displayCurrency),
        displayCanonical(row.pensionIncome(), displayCurrency),
        displayCanonical(row.eventIncome(), displayCurrency),
        displayCanonical(row.eventExpenses(), displayCurrency),
        displayCanonical(row.preRetirementContribution(), displayCurrency),
        displayCanonical(row.totalIncome(), displayCurrency),
        displayCanonical(row.incomeGap(), displayCurrency),
        displayCanonical(row.safeReserveStart(), displayCurrency),
        displayCanonical(row.manualLiquidReserveWithdrawal(), displayCurrency),
        displayCanonical(row.safeReserveEnd(), displayCurrency),
        displayCanonical(row.contractualAssetsEnd(), displayCurrency),
        displayCanonical(row.actualPortfolioWithdrawal().subtract(row.manualLiquidReserveWithdrawal()).max(BigDecimal.ZERO), displayCurrency),
        displayCanonical(row.equityStart(), displayCurrency),
        displayCanonical(row.equityGain(), displayCurrency),
        displayCanonical(row.actualPortfolioWithdrawal().subtract(row.manualLiquidReserveWithdrawal()).max(BigDecimal.ZERO), displayCurrency),
        displayCanonical(row.equityEnd(), displayCurrency),
        displayCanonical(row.unfundedAmount(), displayCurrency));
  }

  private PreviewYear currentYear(
      int year,
      int age,
      LongTermAssetAnnualSnapshotModel facts,
      SimulationAssumptions assumptions,
      com.smartbox.investory.retirement.planning.CurrentYearBridgeResult bridge,
      CurrencyType displayCurrency) {
    boolean retired = age >= assumptions.retirementAge();
    BigDecimal employment = retired ? BigDecimal.ZERO : assumptions.annualEmploymentIncome();
    BigDecimal contribution =
        retired ? BigDecimal.ZERO : assumptions.annualPreRetirementContribution();
    BigDecimal pension =
        age >= assumptions.pensionStartAge() ? assumptions.annualPension() : BigDecimal.ZERO;
    BigDecimal rental = zeroIfNull(facts.rentalIncome());
    BigDecimal bond = zeroIfNull(facts.bondIncome());
    BigDecimal totalIncome = employment.add(rental).add(bond).add(pension);
    BigDecimal expenses = retired
        ? assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses())
        : BigDecimal.ZERO;
    BigDecimal zero = displayCanonical(BigDecimal.ZERO, displayCurrency);
    return new PreviewYear(
        year,
        age,
        "CURRENT",
        retired ? SimulationLifecyclePhase.RETIRED.name() : SimulationLifecyclePhase.WORKING.name(),
        displayCanonical(
            expenses,
            displayCurrency),
        displayCanonical(employment, displayCurrency),
        displayCanonical(rental, displayCurrency),
        displayCanonical(bond, displayCurrency),
        displayCanonical(pension, displayCurrency),
        zero,
        zero,
        displayCanonical(contribution, displayCurrency),
        displayCanonical(totalIncome, displayCurrency),
        zero,
        zero,
        zero,
        zero,
        zero,
        zero,
        zero,
        bridge == null ? null : displayCanonical(bridge.investmentAnnualReturn(), displayCurrency),
        zero,
        zero,
        zero,
        zero);
  }

  private PreviewYear historicalYear(
      int year,
      int age,
      LongTermAssetAnnualSnapshotModel facts,
      SimulationAssumptions assumptions,
      CurrencyType displayCurrency) {
    BigDecimal rental = facts.rentalIncome();
    BigDecimal bond = facts.bondIncome();
    BigDecimal totalIncome =
        rental == null && bond == null
            ? null
            : zeroIfNull(rental).add(zeroIfNull(bond));
    boolean retired = age >= assumptions.retirementAge();
    return new PreviewYear(
        year,
        age,
        "HISTORICAL",
        retired ? SimulationLifecyclePhase.RETIRED.name() : SimulationLifecyclePhase.WORKING.name(),
        null,
        null,
        displayCanonical(rental, displayCurrency),
        displayCanonical(bond, displayCurrency),
        null,
        null,
        null,
        null,
        displayCanonical(totalIncome, displayCurrency),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static LongTermAssetAnnualSnapshotModel facts(LongTermAssetAnnualSnapshotModel facts) {
    return facts == null ? NO_LONG_TERM_FACTS : facts;
  }

  private static BigDecimal zeroIfNull(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private BigDecimal displayCanonical(BigDecimal value, CurrencyType displayCurrency) {
    return value == null ? null : presentation.toDisplay(value, displayCurrency);
  }

  public record PlanEditorPreview(
      int planStartYear,
      int ageAtPlanStart,
      int currentPlanningYear,
      int currentPlanningAge,
      int retirementYear,
      int yearsUntilRetirement,
      int planHorizonYears,
      BigDecimal inflationRate,
      BigDecimal rentalIncomeGrowthSpread,
      BigDecimal effectiveRentalIncomeGrowthRate,
      BigDecimal spendingGrowthSpread,
      BigDecimal effectiveSpendingGrowthRate,
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
      String state,
      String lifecycle,
      BigDecimal annualCosts,
      BigDecimal employmentIncome,
      BigDecimal rentalIncome,
      BigDecimal bondIncome,
      BigDecimal pension,
      BigDecimal eventIncome,
      BigDecimal eventExpenses,
      BigDecimal contribution,
      BigDecimal totalIncome,
      BigDecimal fundingGap,
      BigDecimal reserveStart,
      BigDecimal reserveWithdrawal,
      BigDecimal reserveEnd,
      BigDecimal longTermAvailable,
      BigDecimal longTermWithdrawal,
      BigDecimal investmentStart,
      BigDecimal investmentReturn,
      BigDecimal investmentWithdrawal,
      BigDecimal investmentEnd,
      BigDecimal unfunded) {}
}
