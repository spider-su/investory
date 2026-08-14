package com.smartbox.investory.application.simulation;

import com.smartbox.investory.infrastructure.simulation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns logical plans and creates immutable assumption/event revisions. */
@Service
@Transactional
public class SimulationPlanService {
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
  public List<SimulationPlan> list(Long portfolioId) {
    return plans.findAllByPortfolioIdOrderByName(portfolioId).stream()
        .filter(plan -> !plan.isArchived())
        .toList();
  }

  @Transactional(readOnly = true)
  public SimulationPlan get(Long portfolioId, Long id) {
    SimulationPlan plan =
        plans
            .findByIdAndPortfolioId(id, portfolioId)
            .orElseThrow(() -> new NoSuchElementException("Simulation plan not found"));
    if (plan.isArchived()) throw new NoSuchElementException("Simulation plan not found");
    return plan;
  }

  public SimulationPlan create(Long portfolioId, String name, SimulationAssumptions assumptions) {
    validateName(portfolioId, name, null);
    SimulationPlan saved = plans.save(copy(new SimulationPlan(), portfolioId, name, assumptions));
    if (revisioned()) {
      SimulationPlanRevision revision = createRevision(saved, assumptions, 1);
      saved.setCurrentRevisionId(revision.getId());
      plans.save(saved);
      saveRevisionEvents(revision, assumptions.futureEvents());
    } else {
      saveLegacyEvents(saved, assumptions.futureEvents());
    }
    return saved;
  }

  public SimulationPlan update(
      Long portfolioId, Long id, String name, SimulationAssumptions assumptions) {
    SimulationPlan plan = get(portfolioId, id);
    validateName(portfolioId, name, id);
    if (revisioned()) {
      if (assumptions(plan).equals(assumptions)) {
        plan.setName(name.trim());
        return plans.save(plan);
      }
      int nextNumber =
          revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(id).stream()
                  .mapToInt(SimulationPlanRevision::getRevisionNumber)
                  .max()
                  .orElse(0)
              + 1;
      SimulationPlanRevision revision = createRevision(plan, assumptions, nextNumber);
      plan.setCurrentRevisionId(revision.getId());
      // Keep legacy columns synchronized for old readers; revisions are the authoritative source.
      copy(plan, portfolioId, name, assumptions);
      SimulationPlan saved = plans.save(plan);
      saveRevisionEvents(revision, assumptions.futureEvents());
      return saved;
    }
    return plans.save(copy(plan, portfolioId, name, assumptions));
  }

  public void delete(Long portfolioId, Long id) {
    SimulationPlan plan = get(portfolioId, id);
    if (revisioned()) {
      plan.setArchived(true);
      plans.save(plan);
    } else {
      plans.delete(plan);
    }
  }

  @Transactional(readOnly = true)
  public SimulationPlanRevision currentRevision(Long portfolioId, Long planId) {
    SimulationPlan plan = get(portfolioId, planId);
    if (!revisioned() || plan.getCurrentRevisionId() == null) return null;
    return revisions
        .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), planId)
        .orElseThrow(() -> new IllegalStateException("Current plan revision not found"));
  }

  @Transactional(readOnly = true)
  public Long currentRevisionId(Long portfolioId, Long planId) {
    SimulationPlan plan = get(portfolioId, planId);
    return plan.getCurrentRevisionId();
  }

  @Transactional(readOnly = true)
  public List<SimulationPlanRevision> revisionHistory(Long portfolioId, Long planId) {
    get(portfolioId, planId);
    return revisioned()
        ? revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(planId)
        : List.of();
  }

  @Transactional(readOnly = true)
  public SimulationPlanRevision revision(Long portfolioId, Long planId, Long revisionId) {
    plans
        .findByIdAndPortfolioId(planId, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Simulation plan not found"));
    if (!revisioned()) throw new NoSuchElementException("Plan revision not found");
    return revisions
        .findByIdAndSimulationPlanId(revisionId, planId)
        .orElseThrow(() -> new NoSuchElementException("Plan revision not found"));
  }

  @Transactional(readOnly = true)
  public SimulationAssumptions assumptions(SimulationPlan plan) {
    if (revisioned() && plan.getCurrentRevisionId() != null) {
      SimulationPlanRevision revision =
          revisions
              .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), plan.getId())
              .orElseThrow(() -> new IllegalStateException("Current plan revision not found"));
      return assumptions(revision, revisionEventRecords(revision.getId()));
    }
    return assumptionsFromLegacy(plan);
  }

  @Transactional(readOnly = true)
  public List<SimulationEvent> events(Long portfolioId, Long planId) {
    SimulationPlan plan = get(portfolioId, planId);
    if (revisioned() && plan.getCurrentRevisionId() != null)
      return revisionEventRecords(plan.getCurrentRevisionId());
    return legacyEventRecords(planId);
  }

  public SimulationPlanEvent saveEvent(
      Long portfolioId,
      Long planId,
      Long eventId,
      int year,
      String name,
      BigDecimal amount,
      SimulationEventType type,
      String notes) {
    SimulationPlan plan = get(portfolioId, planId);
    if (!revisioned() || plan.getCurrentRevisionId() == null)
      return saveLegacyEvent(plan, eventId, year, name, amount, type, notes);
    List<SimulationEvent> current = events(portfolioId, planId);
    List<SimulationEvent> next =
        current.stream()
            .filter(event -> !Objects.equals(event.id(), eventId))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    next.add(new SimulationEvent(eventId, year, name.trim(), amount, type, notes));
    SimulationPlanRevision revision = newRevisionFromCurrent(plan);
    plan.setCurrentRevisionId(revision.getId());
    plans.save(plan);
    saveRevisionEvents(revision, next);
    SimulationPlanEvent result = new SimulationPlanEvent();
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
    SimulationPlan plan = get(portfolioId, planId);
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
    SimulationPlanRevision revision = newRevisionFromCurrent(plan);
    plan.setCurrentRevisionId(revision.getId());
    plans.save(plan);
    saveRevisionEvents(revision, next);
  }

  private SimulationPlanRevision newRevisionFromCurrent(SimulationPlan plan) {
    SimulationAssumptions current = assumptions(plan);
    int nextNumber =
        revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(plan.getId()).stream()
                .mapToInt(SimulationPlanRevision::getRevisionNumber)
                .max()
                .orElse(0)
            + 1;
    SimulationPlanRevision revision = createRevision(plan, current, nextNumber);
    return revision;
  }

  private SimulationPlanRevision createRevision(
      SimulationPlan plan, SimulationAssumptions assumptions, int number) {
    SimulationPlanRevision revision = new SimulationPlanRevision();
    revision.setSimulationPlanId(plan.getId());
    revision.setRevisionNumber(number);
    copy(revision, assumptions);
    return revisions.save(revision);
  }

  private void saveRevisionEvents(SimulationPlanRevision revision, List<SimulationEvent> source) {
    for (SimulationEvent event : source) {
      SimulationPlanRevisionEvent stored = new SimulationPlanRevisionEvent();
      stored.setRevisionId(revision.getId());
      stored.setYear(event.year());
      stored.setName(event.name());
      stored.setAmount(event.amount());
      stored.setType(event.type());
      stored.setNotes(event.notes());
      revisionEvents.save(stored);
    }
  }

  private void saveLegacyEvents(SimulationPlan plan, List<SimulationEvent> source) {
    for (SimulationEvent event : source) {
      SimulationPlanEvent stored = new SimulationPlanEvent();
      stored.setSimulationPlanId(plan.getId());
      stored.setYear(event.year());
      stored.setName(event.name());
      stored.setAmount(event.amount());
      stored.setType(event.type());
      stored.setNotes(event.notes());
      legacyEvents.save(stored);
    }
  }

  private SimulationPlanEvent saveLegacyEvent(
      SimulationPlan plan,
      Long eventId,
      int year,
      String name,
      BigDecimal amount,
      SimulationEventType type,
      String notes) {
    SimulationPlanEvent event =
        eventId == null
            ? new SimulationPlanEvent()
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

  private SimulationAssumptions assumptionsFromLegacy(SimulationPlan plan) {
    return assumptions(plan, legacyEventRecords(plan.getId()));
  }

  private SimulationAssumptions assumptions(SimulationPlan plan, List<SimulationEvent> eventList) {
    return new SimulationAssumptions(
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
        plan.getRentalIncomeGrowthRate() == null
            ? SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_RATE
            : plan.getRentalIncomeGrowthRate(),
        plan.getSpendingGrowthRate() == null
            ? plan.getInflationRate()
            : plan.getSpendingGrowthRate(),
        plan.getFundingStrategy() == null
            ? SimulationFundingStrategy.SIMPLE_WATERFALL
            : plan.getFundingStrategy(),
        plan.getSafeReserveYears() == null ? BigDecimal.ZERO : plan.getSafeReserveYears(),
        plan.getEquityHarvestMinimumReturnRate() == null
            ? BigDecimal.ZERO
            : plan.getEquityHarvestMinimumReturnRate(),
        plan.getEquityGainHarvestRate() == null ? BigDecimal.ZERO : plan.getEquityGainHarvestRate(),
        plan.getAllowEmergencyEquityWithdrawal() == null
            || plan.getAllowEmergencyEquityWithdrawal(),
        plan.getRetirementAge() == null ? plan.getCurrentAge() : plan.getRetirementAge(),
        plan.getAnnualEmploymentIncome() == null
            ? BigDecimal.ZERO
            : plan.getAnnualEmploymentIncome(),
        plan.getAnnualPreRetirementContribution() == null
            ? BigDecimal.ZERO
            : plan.getAnnualPreRetirementContribution());
  }

  private SimulationAssumptions assumptions(
      SimulationPlanRevision revision, List<SimulationEvent> eventList) {
    return new SimulationAssumptions(
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
        revision.getRentalIncomeGrowthRate(),
        revision.getSpendingGrowthRate(),
        revision.getFundingStrategy(),
        revision.getSafeReserveYears(),
        revision.getEquityHarvestMinimumReturnRate(),
        revision.getEquityGainHarvestRate(),
        revision.getAllowEmergencyEquityWithdrawal(),
        revision.getRetirementAge(),
        revision.getAnnualEmploymentIncome(),
        revision.getAnnualPreRetirementContribution());
  }

  private static void copy(SimulationPlanRevision target, SimulationAssumptions a) {
    target.setCurrentAge(a.currentAge());
    target.setStartYear(a.startYear());
    target.setEndAge(a.endAge());
    target.setRetirementAge(a.retirementAge());
    target.setAnnualEmploymentIncome(a.annualEmploymentIncome());
    target.setAnnualPreRetirementContribution(a.annualPreRetirementContribution());
    target.setAnnualLivingExpenses(a.annualLivingExpenses());
    target.setAnnualDiscretionaryExpenses(a.annualDiscretionaryExpenses());
    target.setInflationRate(a.inflationRate());
    target.setRentalIncomeGrowthRate(a.rentalIncomeGrowthRate());
    target.setSpendingGrowthRate(a.spendingGrowthRate());
    target.setFundingStrategy(a.fundingStrategy());
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
  }

  private static SimulationPlan copy(
      SimulationPlan p, Long portfolioId, String name, SimulationAssumptions a) {
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
    p.setRentalIncomeGrowthRate(a.rentalIncomeGrowthRate());
    p.setSpendingGrowthRate(a.spendingGrowthRate());
    p.setFundingStrategy(a.fundingStrategy());
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

  private boolean revisioned() {
    return revisions != null && revisionEvents != null;
  }

  private void validateName(Long portfolioId, String name, Long id) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Plan name is required");
    if (list(portfolioId).stream()
        .anyMatch(p -> !Objects.equals(p.getId(), id) && p.getName().equalsIgnoreCase(name.trim())))
      throw new IllegalArgumentException("Plan name already exists");
  }
}
