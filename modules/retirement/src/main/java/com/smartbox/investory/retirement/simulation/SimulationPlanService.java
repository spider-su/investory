package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.infrastructure.simulation.*;
import com.smartbox.investory.retirement.planning.PlanningBaseline;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns logical plans and creates immutable assumption/event revisions. */
@Service
@Transactional
public class SimulationPlanService {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private final SimulationPlanRepository plans;
  private final SimulationPlanEventRepository legacyEvents;
  private final SimulationPlanRevisionRepository revisions;
  private final SimulationPlanRevisionEventRepository revisionEvents;

  /** Compatibility constructor for focused legacy unit tests. */
  public SimulationPlanService(
      SimulationPlanRepository plans, SimulationPlanEventRepository legacyEvents) {
    this(plans, legacyEvents, null, null);
  }

  @Autowired
  public SimulationPlanService(
      SimulationPlanRepository plans,
      SimulationPlanEventRepository legacyEvents,
      SimulationPlanRevisionRepository revisions,
      SimulationPlanRevisionEventRepository revisionEvents) {
    this.plans = plans;
    this.legacyEvents = legacyEvents;
    this.revisions = revisions;
    this.revisionEvents = revisionEvents;
  }

  @Transactional(readOnly = true)
  public List<SimulationPlanEntity> list(Long portfolioId) {
    return plans.findAllByPortfolioIdOrderByName(portfolioId).stream()
        .filter(plan -> !plan.isArchived())
        .toList();
  }

  /** Resolves an explicit owned plan first; otherwise returns the most recently updated saved plan. */
  @Transactional(readOnly = true)
  public Optional<Long> resolvePlanId(Long portfolioId, Long requestedPlanId) {
    if (requestedPlanId != null) return Optional.of(get(portfolioId, requestedPlanId).getId());
    return plans
        .findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(portfolioId)
        .map(SimulationPlanEntity::getId);
  }

  @Transactional(readOnly = true)
  public SimulationPlanEntity get(Long portfolioId, Long id) {
    SimulationPlanEntity plan =
        plans
            .findByIdAndPortfolioId(id, portfolioId)
            .orElseThrow(() -> new NoSuchElementException("Simulation plan not found"));
    if (plan.isArchived()) throw new NoSuchElementException("Simulation plan not found");
    return plan;
  }

  /** Public read boundary for adapters; persistence entities stay inside this service. */
  @Transactional(readOnly = true)
  public SimulationAssumptions assumptions(Long portfolioId, Long id) {
    return assumptions(get(portfolioId, id));
  }

  @Transactional(readOnly = true)
  public String name(Long portfolioId, Long id) {
    return get(portfolioId, id).getName();
  }

  public Long createId(Long portfolioId, String name, SimulationAssumptions assumptions) {
    return create(portfolioId, name, assumptions).getId();
  }

  public Long createId(Long portfolioId, String name, SimulationAssumptions assumptions,
      PlanningBaseline baseline) {
    return create(portfolioId, name, assumptions, baseline).getId();
  }

  public Long updateId(Long portfolioId, Long id, String name, SimulationAssumptions assumptions) {
    return update(portfolioId, id, name, assumptions).getId();
  }

  public Long updateId(Long portfolioId, Long id, String name, SimulationAssumptions assumptions,
      PlanningBaseline baseline) {
    return update(portfolioId, id, name, assumptions, baseline).getId();
  }

  public SimulationPlanEntity create(
      Long portfolioId, String name, SimulationAssumptions assumptions) {
    return create(portfolioId, name, assumptions, null);
  }

  public SimulationPlanEntity create(Long portfolioId, String name,
      SimulationAssumptions assumptions, PlanningBaseline baseline) {
    validateName(portfolioId, name, null);
    SimulationPlanEntity saved =
        plans.save(copy(new SimulationPlanEntity(), portfolioId, name, assumptions));
    if (revisioned()) {
      SimulationPlanRevisionEntity revision = createRevision(saved, assumptions, 1, baseline);
      saved.setCurrentRevisionId(revision.getId());
      plans.save(saved);
      saveRevisionEvents(revision, assumptions.futureEvents());
    } else {
      saveLegacyEvents(saved, assumptions.futureEvents());
    }
    return saved;
  }

  public SimulationPlanEntity update(
      Long portfolioId, Long id, String name, SimulationAssumptions assumptions) {
    return update(portfolioId, id, name, assumptions, null);
  }

  public SimulationPlanEntity update(Long portfolioId, Long id, String name,
      SimulationAssumptions assumptions, PlanningBaseline baseline) {
    SimulationPlanEntity plan = get(portfolioId, id);
    validateName(portfolioId, name, id);
    if (revisioned()) {
      if (assumptions(plan).equals(assumptions)) {
        plan.setName(name.trim());
        return plans.save(plan);
      }
      int nextNumber =
          revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(id).stream()
                  .mapToInt(SimulationPlanRevisionEntity::getRevisionNumber)
                  .max()
                  .orElse(0)
              + 1;
      SimulationPlanRevisionEntity revision = createRevision(plan, assumptions, nextNumber, baseline);
      plan.setCurrentRevisionId(revision.getId());
      // Keep legacy columns synchronized for old readers; revisions are the authoritative source.
      copy(plan, portfolioId, name, assumptions);
      SimulationPlanEntity saved = plans.save(plan);
      saveRevisionEvents(revision, assumptions.futureEvents());
      return saved;
    }
    return plans.save(copy(plan, portfolioId, name, assumptions));
  }

  public void delete(Long portfolioId, Long id) {
    SimulationPlanEntity plan = get(portfolioId, id);
    if (revisioned()) {
      plan.setArchived(true);
      plans.save(plan);
    } else {
      plans.delete(plan);
    }
  }

  @Transactional(readOnly = true)
  public SimulationPlanRevisionEntity currentRevision(Long portfolioId, Long planId) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    if (!revisioned() || plan.getCurrentRevisionId() == null) return null;
    return revisions
        .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), planId)
        .orElseThrow(() -> new IllegalStateException("Current plan revision not found"));
  }

  @Transactional(readOnly = true)
  public Long currentRevisionId(Long portfolioId, Long planId) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    return plan.getCurrentRevisionId();
  }

  @Transactional(readOnly = true)
  public List<SimulationPlanRevisionEntity> revisionHistory(Long portfolioId, Long planId) {
    get(portfolioId, planId);
    return revisioned()
        ? revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(planId)
        : List.of();
  }

  @Transactional(readOnly = true)
  public SimulationPlanRevisionEntity revision(Long portfolioId, Long planId, Long revisionId) {
    plans
        .findByIdAndPortfolioId(planId, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Simulation plan not found"));
    if (!revisioned()) throw new NoSuchElementException("Plan revision not found");
    return revisions
        .findByIdAndSimulationPlanId(revisionId, planId)
        .orElseThrow(() -> new NoSuchElementException("Plan revision not found"));
  }

  @Transactional(readOnly = true)
  public SimulationAssumptions assumptions(SimulationPlanEntity plan) {
    if (revisioned() && plan.getCurrentRevisionId() != null) {
      SimulationPlanRevisionEntity revision =
          revisions
              .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), plan.getId())
              .orElseThrow(() -> new IllegalStateException("Current plan revision not found"));
      return assumptions(revision, revisionEventRecords(revision.getId()));
    }
    return assumptionsFromLegacy(plan);
  }

  @Transactional(readOnly = true)
  public List<SimulationEvent> events(Long portfolioId, Long planId) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    if (revisioned() && plan.getCurrentRevisionId() != null)
      return revisionEventRecords(plan.getCurrentRevisionId());
    return legacyEventRecords(planId);
  }

  public SimulationPlanEventEntity saveEvent(
      Long portfolioId,
      Long planId,
      Long eventId,
      int year,
      String name,
      BigDecimal amount,
      SimulationEventType type,
      String notes) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    if (!revisioned() || plan.getCurrentRevisionId() == null)
      return saveLegacyEvent(plan, eventId, year, name, amount, type, notes);
    List<SimulationEvent> current = events(portfolioId, planId);
    List<SimulationEvent> next =
        current.stream()
            .filter(event -> !Objects.equals(event.id(), eventId))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    next.add(new SimulationEvent(eventId, year, name.trim(), amount, type, notes));
    SimulationPlanRevisionEntity revision = newRevisionFromCurrent(plan);
    plan.setCurrentRevisionId(revision.getId());
    plans.save(plan);
    saveRevisionEvents(revision, next);
    SimulationPlanEventEntity result = new SimulationPlanEventEntity();
    result.setId(revision.getId());
    result.setSimulationPlanId(planId);
    result.setYear(year);
    result.setName(name.trim());
    result.setAmount(amount);
    result.setType(type);
    result.setNotes(notes);
    return result;
  }

  public void deleteEvent(Long portfolioId, Long planId, Long eventId) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    if (!revisioned() || plan.getCurrentRevisionId() == null) {
      legacyEvents.delete(
          legacyEvents
              .findByIdAndSimulationPlanId(eventId, planId)
              .orElseThrow(() -> new NoSuchElementException("Simulation event not found")));
      return;
    }
    List<SimulationEvent> next =
        events(portfolioId, planId).stream()
            .filter(event -> !Objects.equals(event.id(), eventId))
            .toList();
    if (next.size() == events(portfolioId, planId).size())
      throw new NoSuchElementException("Simulation event not found");
    SimulationPlanRevisionEntity revision = newRevisionFromCurrent(plan);
    plan.setCurrentRevisionId(revision.getId());
    plans.save(plan);
    saveRevisionEvents(revision, next);
  }

  private SimulationPlanRevisionEntity newRevisionFromCurrent(SimulationPlanEntity plan) {
    SimulationAssumptions current = assumptions(plan);
    int nextNumber =
        revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(plan.getId()).stream()
                .mapToInt(SimulationPlanRevisionEntity::getRevisionNumber)
                .max()
                .orElse(0)
            + 1;
    PlanningBaseline baseline = plan.getCurrentRevisionId() == null ? null
        : revisions.findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), plan.getId())
            .map(this::baseline).orElse(null);
    SimulationPlanRevisionEntity revision = createRevision(plan, current, nextNumber, baseline);
    return revision;
  }

  private SimulationPlanRevisionEntity createRevision(
      SimulationPlanEntity plan, SimulationAssumptions assumptions, int number,
      PlanningBaseline baseline) {
    SimulationPlanRevisionEntity revision = new SimulationPlanRevisionEntity();
    revision.setSimulationPlanId(plan.getId());
    revision.setRevisionNumber(number);
    copy(revision, assumptions, baseline);
    return revisions.save(revision);
  }

  private void saveRevisionEvents(
      SimulationPlanRevisionEntity revision, List<SimulationEvent> source) {
    for (SimulationEvent event : source) {
      SimulationPlanRevisionEventEntity stored = new SimulationPlanRevisionEventEntity();
      stored.setRevisionId(revision.getId());
      stored.setYear(event.year());
      stored.setName(event.name());
      stored.setAmount(event.amount());
      stored.setType(event.type());
      stored.setNotes(event.notes());
      revisionEvents.save(stored);
    }
  }

  private void saveLegacyEvents(SimulationPlanEntity plan, List<SimulationEvent> source) {
    for (SimulationEvent event : source) {
      SimulationPlanEventEntity stored = new SimulationPlanEventEntity();
      stored.setSimulationPlanId(plan.getId());
      stored.setYear(event.year());
      stored.setName(event.name());
      stored.setAmount(event.amount());
      stored.setType(event.type());
      stored.setNotes(event.notes());
      legacyEvents.save(stored);
    }
  }

  private SimulationPlanEventEntity saveLegacyEvent(
      SimulationPlanEntity plan,
      Long eventId,
      int year,
      String name,
      BigDecimal amount,
      SimulationEventType type,
      String notes) {
    SimulationPlanEventEntity event =
        eventId == null
            ? new SimulationPlanEventEntity()
            : legacyEvents
                .findByIdAndSimulationPlanId(eventId, plan.getId())
                .orElseThrow(() -> new NoSuchElementException("Simulation event not found"));
    event.setSimulationPlanId(plan.getId());
    event.setYear(year);
    event.setName(name.trim());
    event.setAmount(amount);
    event.setType(type);
    event.setNotes(notes);
    return legacyEvents.save(event);
  }

  private List<SimulationEvent> revisionEventRecords(Long revisionId) {
    return revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(revisionId).stream()
        .map(
            e ->
                new SimulationEvent(
                    e.getId(), e.getYear(), e.getName(), e.getAmount(), e.getType(), e.getNotes()))
        .toList();
  }

  private List<SimulationEvent> legacyEventRecords(Long planId) {
    return legacyEvents.findAllBySimulationPlanIdOrderByYearAscIdAsc(planId).stream()
        .map(
            e ->
                new SimulationEvent(
                    e.getId(), e.getYear(), e.getName(), e.getAmount(), e.getType(), e.getNotes()))
        .toList();
  }

  private SimulationAssumptions assumptionsFromLegacy(SimulationPlanEntity plan) {
    return assumptions(plan, legacyEventRecords(plan.getId()));
  }

  private SimulationAssumptions assumptions(
      SimulationPlanEntity plan, List<SimulationEvent> eventList) {
    SimulationAssumptions result =
        new SimulationAssumptions(
            plan.getCurrentAge(),
            plan.getEndAge(),
            plan.getAnnualLivingExpenses(),
            plan.getInflationRate(),
            plan.getCashReturnRate(),
            plan.getFixedIncomeReturnRate(),
            plan.getEquityReturnRate(),
            plan.getRealEstateReturnRate(),
            plan.getOtherReturnRate(),
            plan.getPensionStartAge(),
            plan.getAnnualPension(),
            plan.getCapitalGainTaxRate(),
            plan.getStartYear(),
            plan.getAnnualDiscretionaryExpenses(),
            eventList,
            plan.getRentalIncomeGrowthSpread() == null
                ? SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_SPREAD
                : plan.getRentalIncomeGrowthSpread(),
            plan.getSpendingGrowthSpread() == null
                ? SimulationAssumptions.DEFAULT_SPENDING_GROWTH_SPREAD
                : plan.getSpendingGrowthSpread(),
            plan.getFundingStrategy() == null
                ? SimulationFundingStrategy.SIMPLE_WATERFALL
                : plan.getFundingStrategy(),
            plan.getSafeReserveYears() == null ? BigDecimal.ZERO : plan.getSafeReserveYears(),
            plan.getEquityHarvestMinimumReturnRate() == null
                ? BigDecimal.ZERO
                : plan.getEquityHarvestMinimumReturnRate(),
            plan.getEquityGainHarvestRate() == null
                ? BigDecimal.ZERO
                : plan.getEquityGainHarvestRate(),
            plan.getAllowEmergencyEquityWithdrawal() == null
                || plan.getAllowEmergencyEquityWithdrawal(),
            plan.getRetirementAge() == null ? plan.getCurrentAge() : plan.getRetirementAge(),
            plan.getAnnualEmploymentIncome() == null
                ? BigDecimal.ZERO
                : plan.getAnnualEmploymentIncome(),
            plan.getAnnualPreRetirementContribution() == null
                ? BigDecimal.ZERO
                : plan.getAnnualPreRetirementContribution());
    return result
        .withFundingOrder(parseFundingOrder(plan.getFundingOrder()))
        .withExpenseProfile(parseExpenseProfile(plan.getExpenseProfile()));
  }

  private SimulationAssumptions assumptions(
      SimulationPlanRevisionEntity revision, List<SimulationEvent> eventList) {
    SimulationAssumptions result =
        new SimulationAssumptions(
            revision.getCurrentAge(),
            revision.getEndAge(),
            revision.getAnnualLivingExpenses(),
            revision.getInflationRate(),
            revision.getCashReturnRate(),
            revision.getFixedIncomeReturnRate(),
            revision.getEquityReturnRate(),
            revision.getRealEstateReturnRate(),
            revision.getOtherReturnRate(),
            revision.getPensionStartAge(),
            revision.getAnnualPension(),
            revision.getCapitalGainTaxRate(),
            revision.getStartYear(),
            revision.getAnnualDiscretionaryExpenses(),
            eventList,
            revision.getRentalIncomeGrowthSpread(),
            revision.getSpendingGrowthSpread(),
            revision.getFundingStrategy(),
            revision.getSafeReserveYears(),
            revision.getEquityHarvestMinimumReturnRate(),
            revision.getEquityGainHarvestRate(),
            revision.getAllowEmergencyEquityWithdrawal(),
            revision.getRetirementAge(),
            revision.getAnnualEmploymentIncome(),
            revision.getAnnualPreRetirementContribution());
    return result
        .withFundingOrder(parseFundingOrder(revision.getFundingOrder()))
        .withExpenseProfile(parseExpenseProfile(revision.getExpenseProfile()));
  }

  private static void copy(SimulationPlanRevisionEntity target, SimulationAssumptions a,
      PlanningBaseline baseline) {
    target.setCurrentAge(a.currentAge());
    target.setStartYear(a.startYear());
    target.setEndAge(a.endAge());
    target.setRetirementAge(a.retirementAge());
    target.setAnnualEmploymentIncome(a.annualEmploymentIncome());
    target.setAnnualPreRetirementContribution(a.annualPreRetirementContribution());
    target.setAnnualLivingExpenses(a.annualLivingExpenses());
    target.setAnnualDiscretionaryExpenses(a.annualDiscretionaryExpenses());
    target.setInflationRate(a.inflationRate());
    target.setRentalIncomeGrowthSpread(a.rentalIncomeGrowthSpread());
    target.setSpendingGrowthSpread(a.spendingGrowthSpread());
    target.setFundingStrategy(a.fundingStrategy());
    target.setFundingOrder(serializeFundingOrder(a.fundingOrder()));
    target.setExpenseProfile(serializeExpenseProfile(a.expenseProfile()));
    target.setSafeReserveYears(a.safeReserveYears());
    target.setEquityHarvestMinimumReturnRate(a.equityHarvestMinimumReturnRate());
    target.setEquityGainHarvestRate(a.equityGainHarvestRate());
    target.setAllowEmergencyEquityWithdrawal(a.allowEmergencyEquityWithdrawal());
    target.setCashReturnRate(a.cashReturnRate());
    target.setFixedIncomeReturnRate(a.fixedIncomeReturnRate());
    target.setEquityReturnRate(a.equityReturnRate());
    target.setRealEstateReturnRate(a.realEstateReturnRate());
    target.setOtherReturnRate(a.otherReturnRate());
    target.setPensionStartAge(a.pensionStartAge());
    target.setAnnualPension(a.annualPension());
    target.setCapitalGainTaxRate(a.capitalGainTaxRate());
    if (baseline != null) {
      target.setBaselineAsOfYear(baseline.asOfYear());
      target.setBaselineReserve(baseline.reserve());
      target.setBaselineInvestmentCapital(baseline.investmentCapital());
      target.setBaselineLongTermCapital(baseline.longTermCapital());
      target.setBaselineRentalIncome(baseline.rentalAnnualIncome());
      target.setBaselineLongTermIncome(baseline.longTermAnnualIncome());
      target.setBaselineLongTermState(serializePlanningState(baseline.longTermPlanningState()));
    }
  }

  /** Explicit review action: accepts current normalized state as a new immutable revision baseline. */
  public SimulationPlanRevisionEntity rebaseline(Long portfolioId, Long planId,
      PlanningBaseline baseline) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    if (!revisioned()) throw new IllegalStateException("Plan revisions are not configured");
    SimulationAssumptions current = assumptions(plan);
    int nextNumber = revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(planId).stream()
        .mapToInt(SimulationPlanRevisionEntity::getRevisionNumber).max().orElse(0) + 1;
    SimulationPlanRevisionEntity revision = createRevision(plan, current, nextNumber, baseline);
    plan.setCurrentRevisionId(revision.getId());
    plans.save(plan);
    saveRevisionEvents(revision, current.futureEvents());
    return revision;
  }

  private PlanningBaseline baseline(SimulationPlanRevisionEntity revision) {
    return revision.getBaselineAsOfYear() == null ? null : new PlanningBaseline(
        revision.getBaselineAsOfYear(), revision.getBaselineReserve(), revision.getBaselineInvestmentCapital(),
        revision.getBaselineLongTermCapital(), revision.getBaselineRentalIncome(), revision.getBaselineLongTermIncome(),
        deserializePlanningState(revision.getBaselineLongTermState()));
  }

  @Transactional(readOnly = true)
  public PlanningBaseline baseline(Long portfolioId, Long planId) {
    SimulationPlanRevisionEntity revision = currentRevision(portfolioId, planId);
    if (revision == null || revision.getBaselineAsOfYear() == null) return null;
    return new PlanningBaseline(revision.getBaselineAsOfYear(), revision.getBaselineReserve(),
        revision.getBaselineInvestmentCapital(), revision.getBaselineLongTermCapital(),
        revision.getBaselineRentalIncome(), revision.getBaselineLongTermIncome(),
        deserializePlanningState(revision.getBaselineLongTermState()));
  }

  private static String serializePlanningState(LongTermAnnualProjectionApi.PlanningState state) {
    try {
      return JSON.writeValueAsString(state);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to persist Long-Term planning baseline", e);
    }
  }

  private static LongTermAnnualProjectionApi.PlanningState deserializePlanningState(String value) {
    if (value == null || value.isBlank()) return LongTermAnnualProjectionApi.PlanningState.EMPTY;
    try {
      return JSON.readValue(value, LongTermAnnualProjectionApi.PlanningState.class);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read Long-Term planning baseline", e);
    }
  }

  private static SimulationPlanEntity copy(
      SimulationPlanEntity p, Long portfolioId, String name, SimulationAssumptions a) {
    p.setPortfolioId(portfolioId);
    p.setName(name.trim());
    p.setCurrentAge(a.currentAge());
    p.setStartYear(a.startYear());
    p.setEndAge(a.endAge());
    p.setRetirementAge(a.retirementAge());
    p.setAnnualEmploymentIncome(a.annualEmploymentIncome());
    p.setAnnualPreRetirementContribution(a.annualPreRetirementContribution());
    p.setAnnualLivingExpenses(a.annualLivingExpenses());
    p.setAnnualDiscretionaryExpenses(a.annualDiscretionaryExpenses());
    p.setInflationRate(a.inflationRate());
    p.setRentalIncomeGrowthSpread(a.rentalIncomeGrowthSpread());
    p.setSpendingGrowthSpread(a.spendingGrowthSpread());
    p.setFundingStrategy(a.fundingStrategy());
    p.setFundingOrder(serializeFundingOrder(a.fundingOrder()));
    p.setExpenseProfile(serializeExpenseProfile(a.expenseProfile()));
    p.setSafeReserveYears(a.safeReserveYears());
    p.setEquityHarvestMinimumReturnRate(a.equityHarvestMinimumReturnRate());
    p.setEquityGainHarvestRate(a.equityGainHarvestRate());
    p.setAllowEmergencyEquityWithdrawal(a.allowEmergencyEquityWithdrawal());
    p.setCashReturnRate(a.cashReturnRate());
    p.setFixedIncomeReturnRate(a.fixedIncomeReturnRate());
    p.setEquityReturnRate(a.equityReturnRate());
    p.setRealEstateReturnRate(a.realEstateReturnRate());
    p.setOtherReturnRate(a.otherReturnRate());
    p.setPensionStartAge(a.pensionStartAge());
    p.setAnnualPension(a.annualPension());
    p.setCapitalGainTaxRate(a.capitalGainTaxRate());
    return p;
  }

  private static String serializeFundingOrder(List<FundingSource> order) {
    return String.join(",", order.stream().map(Enum::name).toList());
  }

  private static List<FundingSource> parseFundingOrder(String value) {
    if (value == null || value.isBlank()) return SimulationAssumptions.DEFAULT_FUNDING_ORDER;
    try {
      return Arrays.stream(value.split(",")).map(String::trim).map(FundingSource::valueOf).toList();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown funding source in simulation plan", exception);
    }
  }

  private boolean revisioned() {
    return revisions != null && revisionEvents != null;
  }

  private void validateName(Long portfolioId, String name, Long id) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Plan name is required");
    if (list(portfolioId).stream()
        .anyMatch(p -> !Objects.equals(p.getId(), id) && p.getName().equalsIgnoreCase(name.trim())))
      throw new IllegalArgumentException("Plan name already exists");
  }

  private static String serializeExpenseProfile(ExpenseProfile profile) {
    return profile.steps().stream()
        .map(step -> step.fromYear() + ":" + step.factor().toPlainString())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private static ExpenseProfile parseExpenseProfile(String value) {
    if (value == null || value.isBlank()) return ExpenseProfile.EMPTY;
    try {
      return new ExpenseProfile(
          Arrays.stream(value.split(";"))
              .map(String::trim)
              .map(
                  entry -> {
                    String[] parts = entry.split(":", -1);
                    if (parts.length != 2) throw new IllegalArgumentException();
                    return new ExpenseProfileStep(
                        Integer.parseInt(parts[0].trim()), new BigDecimal(parts[1].trim()));
                  })
              .toList());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid expense profile in simulation plan", exception);
    }
  }
}
