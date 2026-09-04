package com.smartbox.investory.retirement.infrastructure.simulation;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementSandboxPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.CreatePlanCommand;
import com.smartbox.investory.retirement.api.model.PlanDetails;
import com.smartbox.investory.retirement.api.model.PlanSummary;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.RevisionSummary;
import com.smartbox.investory.retirement.api.model.SavePlanEventCommand;
import com.smartbox.investory.retirement.api.model.UpdatePlanCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns logical plans and creates immutable assumption/event revisions. */
@Service
@Primary
@Transactional
public class SimulationPlanService implements RetirementPlanApi, RetirementSandboxPlanApi {
  private final SimulationPlanRepository plans;
  private final SimulationPlanRevisionRepository revisions;
  private final SimulationPlanRevisionEventRepository revisionEvents;
  private final PlanningBaselineJsonCodec baselineJson;

  @Autowired
  public SimulationPlanService(
      SimulationPlanRepository plans,
      SimulationPlanRevisionRepository revisions,
      SimulationPlanRevisionEventRepository revisionEvents,
      PlanningBaselineJsonCodec baselineJson) {
    this.plans = plans;
    this.revisions = revisions;
    this.revisionEvents = revisionEvents;
    this.baselineJson = baselineJson;
  }

  @Transactional(readOnly = true)
  public List<SimulationPlanEntity> list(Long portfolioId) {
    return plans.findAllByPortfolioIdAndArchivedFalseOrderByName(portfolioId);
  }

  @Override
  public List<PlanSummary> listPlans(Long portfolioId) {
    return list(portfolioId).stream()
        .filter(plan -> !plan.isSandbox())
        .map(plan -> new PlanSummary(plan.getId(), plan.getName()))
        .toList();
  }

  /**
   * Resolves an explicit owned plan first; otherwise returns the most recently updated saved plan.
   */
  @Transactional(readOnly = true)
  public Optional<Long> resolvePlanId(Long portfolioId, Long requestedPlanId) {
    if (requestedPlanId != null) {
      SimulationPlanEntity plan = get(portfolioId, requestedPlanId);
      return plan.isSandbox() ? Optional.empty() : Optional.of(plan.getId());
    }
    return plans
        .findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(portfolioId)
        .filter(plan -> !plan.isSandbox())
        .map(SimulationPlanEntity::getId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> resolve(Long portfolioId) {
    return plans
        .findFirstByPortfolioIdAndSandboxTrueAndArchivedFalse(portfolioId)
        .map(SimulationPlanEntity::getId);
  }

  @Override
  @Transactional(readOnly = true)
  public SandboxSimulationInput load(Long portfolioId, Long planId) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    if (!plan.isSandbox()) throw new PlanNotFoundException();
    SimulationPlanRevisionEntity revision =
        revisions
            .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), planId)
            .orElseThrow(RevisionNotFoundException::new);
    return baselineJson.readSandbox(revision.getBaselineLongTermState());
  }

  @Override
  public Long save(Long portfolioId, Long planId, SandboxSimulationInput input) {
    Objects.requireNonNull(input, "input");
    SimulationPlanEntity plan;
    if (planId == null) {
      plan =
          plans
              .findFirstByPortfolioIdAndSandboxTrueAndArchivedFalse(portfolioId)
              .orElseGet(
                  () -> {
                    SimulationPlanEntity created = newPlan(portfolioId, "Sandbox");
                    created.setSandbox(true);
                    return plans.save(created);
                  });
    } else {
      plan = getForUpdate(portfolioId, planId);
      if (!plan.isSandbox()) throw new PlanNotFoundException();
    }
    int nextNumber = nextRevisionNumber(plan.getId());
    SimulationPlanRevisionEntity revision =
        createRevision(
            plan, sandboxAssumptions(input), nextNumber, null, baselineJson.writeSandbox(input), 1);
    plan.setCurrentRevisionId(revision.getId());
    return plans.save(plan).getId();
  }

  private static SimulationAssumptions sandboxAssumptions(SandboxSimulationInput input) {
    return SimulationAssumptions.defaults(input.currentAge(), input.endAge(), input.startYear())
        .toBuilder()
        .currentAge(input.currentAge())
        .endAge(input.endAge())
        .retirementAge(input.retirementAge())
        .annualLivingExpenses(input.annualSpending())
        .inflationRate(input.inflationRate())
        .fixedIncomeReturnRate(input.bondReturnRate())
        .equityReturnRate(input.equityReturnRate())
        .pensionStartAge(input.pensionAge())
        .annualPension(input.monthlyPensionIncome().multiply(BigDecimal.valueOf(12)))
        .build();
  }

  @Transactional(readOnly = true)
  public SimulationPlanEntity get(Long portfolioId, Long id) {
    SimulationPlanEntity plan =
        plans.findByIdAndPortfolioId(id, portfolioId).orElseThrow(PlanNotFoundException::new);
    if (plan.isArchived()) throw new PlanNotFoundException();
    return plan;
  }

  public SimulationPlanEntity create(
      Long portfolioId, String name, SimulationAssumptions assumptions) {
    return create(portfolioId, name, assumptions, null);
  }

  public SimulationPlanEntity create(
      Long portfolioId, String name, SimulationAssumptions assumptions, PlanningBaseline baseline) {
    validateName(portfolioId, name, null);
    SimulationPlanEntity saved = plans.save(newPlan(portfolioId, name));
    SimulationPlanRevisionEntity revision = createRevision(saved, assumptions, 1, baseline);
    saved.setCurrentRevisionId(revision.getId());
    plans.save(saved);
    saveRevisionEvents(revision, assumptions.futureEvents());
    return saved;
  }

  public SimulationPlanEntity update(
      Long portfolioId, Long id, String name, SimulationAssumptions assumptions) {
    SimulationPlanEntity plan = getForUpdate(portfolioId, id);
    validateName(portfolioId, name, id);
    SimulationAssumptions current = assumptions(plan);
    if (current.equals(assumptions)) {
      plan.setName(name.trim());
      return plans.save(plan);
    }
    int nextNumber = nextRevisionNumber(id);
    SimulationPlanRevisionEntity revision =
        createRevision(plan, assumptions, nextNumber, currentBaseline(plan));
    plan.setCurrentRevisionId(revision.getId());
    plan.setName(name.trim());
    SimulationPlanEntity saved = plans.save(plan);
    saveRevisionEvents(revision, assumptions.futureEvents());
    return saved;
  }

  public void delete(Long portfolioId, Long id) {
    SimulationPlanEntity plan = getForUpdate(portfolioId, id);
    plan.setArchived(true);
    plans.save(plan);
  }

  @Transactional(readOnly = true)
  public SimulationPlanRevisionEntity currentRevision(Long portfolioId, Long planId) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    return revisions
        .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), planId)
        .orElseThrow(RetirementPlanApi.RevisionNotFoundException::new);
  }

  @Override
  public Long createPlan(CreatePlanCommand command) {
    Objects.requireNonNull(command, "command");
    return create(command.portfolioId(), command.name(), command.assumptions(), command.baseline())
        .getId();
  }

  @Override
  public Long updatePlan(UpdatePlanCommand command) {
    Objects.requireNonNull(command, "command");
    return update(command.portfolioId(), command.planId(), command.name(), command.assumptions())
        .getId();
  }

  @Override
  public Long savePlanEvent(SavePlanEventCommand command) {
    Objects.requireNonNull(command, "command");
    SimulationPlanRevisionEventEntity saved =
        saveEvent(
            command.portfolioId(),
            command.planId(),
            command.eventId(),
            command.year(),
            command.name(),
            command.amount(),
            command.type(),
            command.notes());
    return saved.getLogicalEventId() == null ? saved.getId() : saved.getLogicalEventId();
  }

  @Override
  public void deletePlan(Long portfolioId, Long planId) {
    delete(portfolioId, planId);
  }

  @Override
  public RevisionSummary rebaselinePlan(Long portfolioId, Long planId, PlanningBaseline baseline) {
    SimulationPlanRevisionEntity revision = rebaseline(portfolioId, planId, baseline);
    return new RevisionSummary(
        revision.getId(), revision.getRevisionNumber(), revision.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public List<SimulationPlanRevisionEntity> revisionHistory(Long portfolioId, Long planId) {
    get(portfolioId, planId);
    return revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(planId);
  }

  @Transactional(readOnly = true)
  public SimulationPlanRevisionEntity revision(Long portfolioId, Long planId, Long revisionId) {
    plans.findByIdAndPortfolioId(planId, portfolioId).orElseThrow(PlanNotFoundException::new);
    return revisions
        .findByIdAndSimulationPlanId(revisionId, planId)
        .orElseThrow(RetirementPlanApi.RevisionNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public SimulationAssumptions assumptions(SimulationPlanEntity plan) {
    SimulationPlanRevisionEntity revision =
        revisions
            .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), plan.getId())
            .orElseThrow(RetirementPlanApi.RevisionNotFoundException::new);
    return assumptions(revision, revisionEventRecords(revision.getId()));
  }

  @Transactional(readOnly = true)
  public List<SimulationEvent> events(Long portfolioId, Long planId) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    return revisionEventRecords(plan.getCurrentRevisionId());
  }

  public SimulationPlanRevisionEventEntity saveEvent(
      Long portfolioId,
      Long planId,
      Long eventId,
      int year,
      String name,
      BigDecimal amount,
      SimulationEventType type,
      String notes) {
    SimulationPlanEntity plan = getForUpdate(portfolioId, planId);
    List<SimulationEvent> current = revisionEventRecords(plan.getCurrentRevisionId());
    List<SimulationEvent> next =
        current.stream()
            .filter(event -> !Objects.equals(event.id(), eventId))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    next.add(new SimulationEvent(eventId, year, name.trim(), amount, type, notes));
    SimulationPlanRevisionEntity revision = newRevisionFromCurrent(plan);
    plan.setCurrentRevisionId(revision.getId());
    plans.save(plan);
    return saveRevisionEvents(revision, next).getLast();
  }

  public void deleteEvent(Long portfolioId, Long planId, Long eventId) {
    SimulationPlanEntity plan = getForUpdate(portfolioId, planId);
    List<SimulationEvent> current = revisionEventRecords(plan.getCurrentRevisionId());
    List<SimulationEvent> next =
        current.stream().filter(event -> !Objects.equals(event.id(), eventId)).toList();
    if (next.size() == current.size()) throw new RetirementPlanApi.EventNotFoundException();
    SimulationPlanRevisionEntity revision = newRevisionFromCurrent(plan);
    plan.setCurrentRevisionId(revision.getId());
    plans.save(plan);
    saveRevisionEvents(revision, next);
  }

  private SimulationPlanRevisionEntity newRevisionFromCurrent(SimulationPlanEntity plan) {
    SimulationAssumptions current = assumptions(plan);
    int nextNumber = nextRevisionNumber(plan.getId());
    PlanningBaseline baseline =
        plan.getCurrentRevisionId() == null
            ? null
            : revisions
                .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), plan.getId())
                .map(this::baseline)
                .orElse(null);
    SimulationPlanRevisionEntity revision = createRevision(plan, current, nextNumber, baseline);
    return revision;
  }

  private SimulationPlanRevisionEntity createRevision(
      SimulationPlanEntity plan,
      SimulationAssumptions assumptions,
      int number,
      PlanningBaseline baseline) {
    return createRevision(plan, assumptions, number, baseline, null, null);
  }

  private SimulationPlanRevisionEntity createRevision(
      SimulationPlanEntity plan,
      SimulationAssumptions assumptions,
      int number,
      PlanningBaseline baseline,
      String baselineLongTermState,
      Integer baselineLongTermStateVersion) {
    SimulationPlanRevisionEntity revision = new SimulationPlanRevisionEntity();
    revision.setSimulationPlanId(plan.getId());
    revision.setRevisionNumber(number);
    copy(revision, assumptions, baseline);
    if (baselineLongTermState != null) {
      revision.setBaselineLongTermState(baselineLongTermState);
      revision.setBaselineLongTermStateVersion(baselineLongTermStateVersion);
    }
    return revisions.save(revision);
  }

  private List<SimulationPlanRevisionEventEntity> saveRevisionEvents(
      SimulationPlanRevisionEntity revision, List<SimulationEvent> source) {
    List<SimulationPlanRevisionEventEntity> saved = new java.util.ArrayList<>();
    for (SimulationEvent event : source) {
      SimulationPlanRevisionEventEntity stored = new SimulationPlanRevisionEventEntity();
      stored.setRevisionId(revision.getId());
      stored.setLogicalEventId(event.id());
      stored.setYear(event.year());
      stored.setName(event.name());
      stored.setAmount(event.amount());
      stored.setType(event.type());
      stored.setNotes(event.notes());
      saved.add(revisionEvents.save(stored));
    }
    return List.copyOf(saved);
  }

  private List<SimulationEvent> revisionEventRecords(Long revisionId) {
    return revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(revisionId).stream()
        .map(
            e ->
                new SimulationEvent(
                    e.getLogicalEventId() == null ? e.getId() : e.getLogicalEventId(),
                    e.getYear(),
                    e.getName(),
                    e.getAmount(),
                    e.getType(),
                    e.getNotes()))
        .toList();
  }

  private SimulationAssumptions assumptions(
      SimulationPlanRevisionEntity revision, List<SimulationEvent> eventList) {
    return SimulationAssumptionsPersistenceMapper.read(revision, eventList);
  }

  private void copy(
      SimulationPlanRevisionEntity target, SimulationAssumptions a, PlanningBaseline baseline) {
    SimulationAssumptionsPersistenceMapper.write(target, a);
    if (baseline != null) {
      target.setBaselineAsOfYear(baseline.asOfYear());
      target.setBaselineReserve(baseline.reserve());
      target.setBaselineInvestmentCapital(baseline.investmentCapital());
      target.setBaselineLongTermCapital(baseline.longTermCapital());
      target.setBaselineRentalIncome(baseline.rentalAnnualIncome());
      target.setBaselineLongTermIncome(baseline.longTermAnnualIncome());
      target.setBaselineLongTermState(baselineJson.write(baseline.longTermPlanningState()));
      target.setBaselineLongTermStateVersion(1);
    }
  }

  private PlanningBaseline currentBaseline(SimulationPlanEntity plan) {
    return revisions
        .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), plan.getId())
        .map(this::baseline)
        .orElse(null);
  }

  /**
   * Explicit review action: accepts current normalized state as a new immutable revision baseline.
   */
  public SimulationPlanRevisionEntity rebaseline(
      Long portfolioId, Long planId, PlanningBaseline baseline) {
    SimulationPlanEntity plan = getForUpdate(portfolioId, planId);
    SimulationAssumptions current = assumptions(plan);
    int nextNumber = nextRevisionNumber(planId);
    SimulationPlanRevisionEntity revision = createRevision(plan, current, nextNumber, baseline);
    plan.setCurrentRevisionId(revision.getId());
    plans.save(plan);
    saveRevisionEvents(revision, current.futureEvents());
    return revision;
  }

  private SimulationPlanEntity getForUpdate(Long portfolioId, Long id) {
    SimulationPlanEntity plan =
        plans
            .findByIdAndPortfolioIdForUpdate(id, portfolioId)
            .orElseThrow(PlanNotFoundException::new);
    if (plan.isArchived()) throw new PlanNotFoundException();
    return plan;
  }

  private PlanningBaseline baseline(SimulationPlanRevisionEntity revision) {
    return revision.getBaselineAsOfYear() == null
        ? null
        : new PlanningBaseline(
            revision.getBaselineAsOfYear(),
            revision.getBaselineReserve(),
            revision.getBaselineInvestmentCapital(),
            revision.getBaselineLongTermCapital(),
            revision.getBaselineRentalIncome(),
            revision.getBaselineLongTermIncome(),
            baselineJson.read(revision.getBaselineLongTermState()));
  }

  private static SimulationPlanEntity newPlan(Long portfolioId, String name) {
    SimulationPlanEntity p = new SimulationPlanEntity();
    p.setPortfolioId(portfolioId);
    p.setName(name.trim());
    return p;
  }

  @Override
  @Transactional(readOnly = true)
  public PlanDetails details(Long portfolioId, Long planId) {
    SimulationPlanEntity plan = get(portfolioId, planId);
    SimulationPlanRevisionEntity revision =
        revisions
            .findByIdAndSimulationPlanId(plan.getCurrentRevisionId(), planId)
            .orElseThrow(RetirementPlanApi.RevisionNotFoundException::new);
    return new PlanDetails(
        plan.getId(),
        plan.getName(),
        assumptions(revision, revisionEventRecords(revision.getId())),
        revision.getId(),
        new RevisionSummary(
            revision.getId(), revision.getRevisionNumber(), revision.getCreatedAt()),
        baseline(revision));
  }

  private int nextRevisionNumber(Long planId) {
    return revisions
            .findFirstBySimulationPlanIdOrderByRevisionNumberDesc(planId)
            .map(SimulationPlanRevisionEntity::getRevisionNumber)
            .orElse(0)
        + 1;
  }

  private void validateName(Long portfolioId, String name, Long id) {
    if (name == null || isBlank(name)) throw new IllegalArgumentException("Plan name is required");
    if (list(portfolioId).stream()
        .anyMatch(p -> !Objects.equals(p.getId(), id) && p.getName().equalsIgnoreCase(name.trim())))
      throw new IllegalArgumentException("Plan name already exists");
  }
}
