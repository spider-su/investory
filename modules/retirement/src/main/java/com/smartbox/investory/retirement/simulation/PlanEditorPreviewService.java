package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.retirement.planning.CurrentYearBridgeResult;
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
    SimulationResult result = forward.forwardAssumptions().isPresent()
        ? simulations.simulate(forward.bridgedProfile(), projected, SimulationScenario.BASE)
        : new SimulationResult(SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of());
    LongTermAssetAnnualSnapshotModel facts = facts(currentFacts(profile));
    int currentYear = Year.now(clock).getValue();
    int retirementYear = ForwardSimulationContextFactory.retirementYear(assumptions);
    SimulationYear first = result.years().isEmpty() ? null : result.years().get(0);
    SimulationYear plannedIncomeYear = result.years().stream()
        .filter(row -> row.year() > currentYear && row.lifecyclePhase() == SimulationLifecyclePhase.RETIRED)
        .findFirst().orElse(first);
    int currentPlanningAge = ForwardSimulationContextFactory.currentPlanningAge(assumptions, currentYear);
    List<PreviewYear> previewYears = new ArrayList<>();
    for (int year = assumptions.planStartYear(); year < currentYear; year++) {
      previewYears.add(historicalYear(year, assumptions.ageAtPlanStart() + year - assumptions.planStartYear(),
          facts(longTermAssets.historicalAnnualSnapshot(profile.portfolioId(), year)), assumptions, displayCurrency));
    }
    previewYears.add(currentYear(currentYear, currentPlanningAge, facts, assumptions,
        forward.currentYearBridge(), displayCurrency));
    previewYears.addAll(result.years().stream().filter(row -> row.year() > currentYear)
        .map(row -> year(row, assumptions, displayCurrency)).toList());
    return new PlanEditorPreview(
        assumptions.planStartYear(), assumptions.ageAtPlanStart(), currentYear, currentPlanningAge,
        currentYear + 1, retirementYear, Math.max(retirementYear - currentYear, 0),
        assumptions.planStartYear() + assumptions.endAge() - assumptions.ageAtPlanStart(),
        assumptions.endAge(), Math.max(assumptions.endAge() - assumptions.ageAtPlanStart() + 1, 0),
        assumptions.inflationRate(), assumptions.rentalIncomeGrowthSpread(),
        assumptions.effectiveRentalIncomeGrowthRate(), assumptions.spendingGrowthSpread(),
        assumptions.effectiveSpendingGrowthRate(),
        displayCanonical(assumptions.annualLivingExpenses().divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP), displayCurrency),
        displayCanonical(assumptions.annualLivingExpenses(), displayCurrency),
        displayCanonical(assumptions.annualDiscretionaryExpenses(), displayCurrency),
        displayCanonical(assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()), displayCurrency),
        first == null ? null : displayCanonical(first.totalExpenses(), displayCurrency),
        displayCanonical(facts.rentalIncome(), displayCurrency), displayCanonical(facts.bondIncome(), displayCurrency),
        plannedIncomeYear == null ? null : plannedIncomeYear.year(),
        plannedIncomeYear == null ? null : displayCanonical(plannedIncomeYear.rentalIncome(), displayCurrency),
        plannedIncomeYear == null ? null : displayCanonical(plannedIncomeYear.bondIncome(), displayCurrency),
        plannedIncomeYear == null ? null : displayCanonical(plannedIncomeYear.funding().investmentReturn(), displayCurrency),
        plannedIncomeYear == null ? null : displayCanonical(plannedIncomeYear.pensionIncome(), displayCurrency),
        plannedIncomeYear == null ? null : displayCanonical(plannedIncomeYear.rentalIncome().add(plannedIncomeYear.bondIncome())
            .add(plannedIncomeYear.funding().investmentReturn()).add(plannedIncomeYear.pensionIncome()), displayCurrency),
        assumptions, result.failureAge(), List.copyOf(previewYears),
        first == null ? null : year(first, assumptions, displayCurrency));
  }

  /** Current Long-term Assets facts used by the editor's read-only fields. */
  public LongTermAssetAnnualSnapshotModel currentFacts(InvestmentProfile profile) {
    return longTermAssets.currentAnnualSnapshot(profile.portfolioId(), LocalDate.now(clock));
  }

  private PreviewYear year(SimulationYear row, SimulationAssumptions assumptions, CurrencyType displayCurrency) {
    SimulationFunding funding = row.funding();
    return new PreviewYear(row.year(), row.age(), "PROJECTED", row.lifecyclePhase().name(),
        displayCanonical(row.coreExpenses().add(row.discretionaryExpenses()), displayCurrency),
        displayCanonical(row.eventExpenses(), displayCurrency), displayCanonical(row.totalExpenses(), displayCurrency),
        assumptions.effectiveSpendingGrowthRate(), assumptions.expenseProfile().factorForYear(row.year() - assumptions.planStartYear()),
        displayCanonical(row.employmentIncome(), displayCurrency), displayCanonical(row.rentalIncome(), displayCurrency),
        displayCanonical(row.bondIncome(), displayCurrency), displayCanonical(funding.investmentReturn(), displayCurrency),
        displayCanonical(row.pensionIncome(), displayCurrency), displayCanonical(row.eventIncome(), displayCurrency),
        displayCanonical(row.totalIncome(), displayCurrency), displayCanonical(funding.fundingGap(), displayCurrency),
        displayCanonical(row.totalIncome().subtract(row.totalExpenses()).max(BigDecimal.ZERO), displayCurrency),
        displayCanonical(funding.reserveStart(), displayCurrency), displayCanonical(funding.reserveTransfer(), displayCurrency),
        displayCanonical(funding.reserveWithdrawal(), displayCurrency), displayCanonical(funding.reserveEnd(), displayCurrency),
        displayCanonical(funding.longTermFunding(), displayCurrency), displayCanonical(funding.longTermCapitalEnd(), displayCurrency),
        displayCanonical(funding.investmentStart(), displayCurrency), displayCanonical(funding.investmentReturn(), displayCurrency),
        displayCanonical(funding.investmentWithdrawal(), displayCurrency), displayCanonical(funding.investmentEnd(), displayCurrency),
        displayCanonical(funding.unfunded(), displayCurrency), funding.unfunded().signum() > 0 ? "UNFUNDED" : "FUNDED");
  }

  private PreviewYear currentYear(int year, int age, LongTermAssetAnnualSnapshotModel facts,
      SimulationAssumptions assumptions, CurrentYearBridgeResult bridge, CurrencyType displayCurrency) {
    boolean retired = age >= assumptions.retirementAge();
    BigDecimal employment = retired ? BigDecimal.ZERO : assumptions.annualEmploymentIncome();
    BigDecimal pension = age >= assumptions.pensionStartAge() ? assumptions.annualPension() : BigDecimal.ZERO;
    return new PreviewYear(year, age, "CURRENT", retired ? SimulationLifecyclePhase.RETIRED.name() : SimulationLifecyclePhase.WORKING.name(),
        null, null, null, null, null, displayCanonical(employment, displayCurrency), displayCanonical(facts.rentalIncome(), displayCurrency),
        displayCanonical(facts.bondIncome(), displayCurrency),
        bridge == null ? null : displayCanonical(bridge.investmentAnnualReturn(), displayCurrency),
        displayCanonical(pension, displayCurrency), null, null, null, null, null, null, null, null, null, null, null, null, null, null, "CURRENT FACTS");
  }

  private PreviewYear historicalYear(int year, int age, LongTermAssetAnnualSnapshotModel facts,
      SimulationAssumptions assumptions, CurrencyType displayCurrency) {
    BigDecimal rental = facts.rentalIncome();
    BigDecimal bond = facts.bondIncome();
    BigDecimal totalIncome = rental == null && bond == null ? null : zeroIfNull(rental).add(zeroIfNull(bond));
    boolean retired = age >= assumptions.retirementAge();
    return new PreviewYear(year, age, "HISTORICAL", retired ? SimulationLifecyclePhase.RETIRED.name() : SimulationLifecyclePhase.WORKING.name(),
        null, null, null, null, null, null, displayCanonical(rental, displayCurrency), displayCanonical(bond, displayCurrency),
        null, null, null, displayCanonical(totalIncome, displayCurrency), null, null, null, null, null, null, null, null, null, null, null, null, null, "HISTORICAL");
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
      int planStartYear, int ageAtPlanStart, int currentPlanningYear, int currentPlanningAge,
      int projectionStartYear, int retirementYear, int yearsUntilRetirement, int endYear, int endAge,
      int planHorizonYears, BigDecimal inflationRate, BigDecimal rentalIncomeGrowthSpread,
      BigDecimal effectiveRentalIncomeGrowthRate, BigDecimal spendingGrowthSpread,
      BigDecimal effectiveSpendingGrowthRate, BigDecimal monthlyLivingCosts, BigDecimal annualLivingCosts,
      BigDecimal annualExtras, BigDecimal totalAnnualCosts, BigDecimal nextYearCosts, BigDecimal rentalIncome,
      BigDecimal bondIncome, Integer plannedIncomeReferenceYear, BigDecimal plannedRentalIncome,
      BigDecimal plannedBondIncome, BigDecimal plannedInvestmentProfit, BigDecimal plannedPension,
      BigDecimal plannedAnnualIncome, SimulationAssumptions assumptions, Integer firstFailureAge,
      List<PreviewYear> years, PreviewYear firstProjectedYear) {}

  /** Presentation DTO of canonical annual facts; null means the source has no value for that year. */
  public record PreviewYear(
      int year, int age, String state, String lifecycle, BigDecimal recurringCosts, BigDecimal eventExpenses,
      BigDecimal totalCosts, BigDecimal spendingGrowthRate, BigDecimal expenseProfile, BigDecimal employmentIncome,
      BigDecimal rentalIncome, BigDecimal bondIncome, BigDecimal investmentReturn, BigDecimal pension,
      BigDecimal eventIncome, BigDecimal totalIncome, BigDecimal fundingGap, BigDecimal surplus,
      BigDecimal reserveStart, BigDecimal reserveTransfer, BigDecimal reserveWithdrawal, BigDecimal reserveEnd,
      BigDecimal longTermFunding, BigDecimal longTermEnd, BigDecimal investmentStart,
      BigDecimal investmentReturnForBalance, BigDecimal investmentWithdrawal, BigDecimal investmentEnd,
      BigDecimal unfunded, String status) {}
}
