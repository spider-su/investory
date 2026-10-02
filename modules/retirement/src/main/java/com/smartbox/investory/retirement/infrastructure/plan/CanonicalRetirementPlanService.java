package com.smartbox.investory.retirement.infrastructure.plan;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.infrastructure.assumptions.SimulationAssumptionsPersistenceMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical mutable plan store. It replaces revision snapshots with one plan and owned events. */
@Service
@Primary
@Transactional
public class CanonicalRetirementPlanService implements RetirementPlanApi {
  private final RetirementPlanRepository plans;
  private final RetirementPlanEventRepository events;
  private final RetirementPlanBaselineCodec baselineJson;
  private final Clock clock;

  public CanonicalRetirementPlanService(
      RetirementPlanRepository plans,
      RetirementPlanEventRepository events,
      RetirementPlanBaselineCodec baselineJson,
      Clock clock) {
    this.plans = plans;
    this.events = events;
    this.baselineJson = baselineJson;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> resolvePlanId(Long portfolioId, Long requestedPlanId) {
    if (requestedPlanId != null) {
      RetirementPlanEntity plan = get(portfolioId, requestedPlanId);
      return Optional.of(plan.getId());
    }
    return plans
        .findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(portfolioId)
        .map(RetirementPlanEntity::getId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PlanSummary> listPlans(Long portfolioId) {
    return plans.findAllByPortfolioIdAndArchivedFalseOrderByName(portfolioId).stream()
        .map(p -> new PlanSummary(p.getId(), p.getName()))
        .toList();
  }

  @Override
  public Long createPlan(CreatePlanCommand command) {
    Objects.requireNonNull(command, "command");
    validateName(command.portfolioId(), command.name(), null);
    RetirementPlanEntity plan = new RetirementPlanEntity();
    plan.setPortfolioId(command.portfolioId());
    plan.setName(command.name().trim());
    write(plan, command.assumptions(), command.baseline());
    Instant now = Instant.now(clock);
    plan.setCreatedAt(now);
    plan.setUpdatedAt(now);
    return savePlan(plan);
  }

  @Override
  public Long updatePlan(UpdatePlanCommand command) {
    Objects.requireNonNull(command, "command");
    RetirementPlanEntity plan = get(command.portfolioId(), command.planId());
    validateName(command.portfolioId(), command.name(), command.planId());
    plan.setName(command.name().trim());
    write(plan, command.assumptions(), baseline(plan));
    return touchAndSave(plan);
  }

  @Override
  @Transactional(readOnly = true)
  public PlanDetails details(Long portfolioId, Long planId) {
    RetirementPlanEntity plan = get(portfolioId, planId);
    return new PlanDetails(plan.getId(), plan.getName(), assumptions(plan), baseline(plan));
  }

  @Override
  public Long savePlanEvent(SavePlanEventCommand command) {
    RetirementPlanEntity plan = get(command.portfolioId(), command.planId());
    RetirementPlanEventEntity event =
        command.eventId() == null
            ? new RetirementPlanEventEntity()
            : events
                .findByIdAndPlanId(command.eventId(), plan.getId())
                .orElseThrow(RetirementPlanApi.EventNotFoundException::new);
    event.setPlanId(plan.getId());
    event.setYear(command.year());
    event.setName(command.name().trim());
    event.setAmount(command.amount());
    event.setType(command.type());
    event.setNotes(command.notes());
    if (event.getCreatedAt() == null) event.setCreatedAt(Instant.now(clock));
    Long id = events.save(event).getId();
    touchAndSave(plan);
    return id;
  }

  @Override
  public void deleteEvent(Long portfolioId, Long planId, Long eventId) {
    RetirementPlanEntity plan = get(portfolioId, planId);
    events
        .findByIdAndPlanId(eventId, planId)
        .orElseThrow(RetirementPlanApi.EventNotFoundException::new);
    events.deleteById(eventId);
    touchAndSave(plan);
  }

  @Override
  public void deletePlan(Long portfolioId, Long planId) {
    RetirementPlanEntity plan = get(portfolioId, planId);
    plan.setArchived(true);
    touchAndSave(plan);
  }

  @Override
  public void rebaselinePlan(Long portfolioId, Long planId, PlanningBaseline baseline) {
    RetirementPlanEntity plan = get(portfolioId, planId);
    writeBaseline(plan, baseline);
    touchAndSave(plan);
  }

  private RetirementPlanEntity get(Long portfolioId, Long id) {
    return plans
        .findByIdAndPortfolioId(id, portfolioId)
        .filter(p -> !p.isArchived())
        .orElseThrow(RetirementPlanApi.PlanNotFoundException::new);
  }

  private SimulationAssumptions assumptions(RetirementPlanEntity plan) {
    List<SimulationEvent> planEvents =
        events.findAllByPlanIdOrderByYearAscIdAsc(plan.getId()).stream()
            .map(
                e ->
                    new SimulationEvent(
                        e.getId(),
                        e.getYear(),
                        e.getName(),
                        e.getAmount(),
                        e.getType(),
                        e.getNotes()))
            .toList();
    return SimulationAssumptionsPersistenceMapper.read(plan, planEvents);
  }

  private void write(
      RetirementPlanEntity plan, SimulationAssumptions assumptions, PlanningBaseline baseline) {
    SimulationAssumptionsPersistenceMapper.write(plan, assumptions);
    writeBaseline(plan, baseline);
  }

  private void writeBaseline(RetirementPlanEntity plan, PlanningBaseline baseline) {
    if (baseline == null) {
      plan.setBaselineAsOfYear(null);
      return;
    }
    plan.setBaselineAsOfYear(baseline.asOfYear());
    plan.setBaselineReserve(baseline.reserve());
    plan.setBaselineInvestmentCapital(baseline.investmentCapital());
    plan.setBaselineLongTermCapital(baseline.longTermCapital());
    plan.setBaselineRentalIncome(baseline.rentalAnnualIncome());
    plan.setBaselineLongTermIncome(baseline.longTermAnnualIncome());
    plan.setBaselineLongTermState(baselineJson.write(baseline.longTermPlanningState()));
    plan.setBaselineLongTermStateVersion(RetirementPlanBaselineCodec.CURRENT_FORMAT_VERSION);
  }

  private PlanningBaseline baseline(RetirementPlanEntity plan) {
    return plan.getBaselineAsOfYear() == null
        ? null
        : new PlanningBaseline(
            plan.getBaselineAsOfYear(),
            plan.getBaselineReserve(),
            plan.getBaselineInvestmentCapital(),
            plan.getBaselineLongTermCapital(),
            plan.getBaselineRentalIncome(),
            plan.getBaselineLongTermIncome(),
            readBaseline(plan));
  }

  private com.smartbox.investory.profile.api.model.ProfileAssetProjection readBaseline(
      RetirementPlanEntity plan) {
    Integer version = plan.getBaselineLongTermStateVersion();
    if (version != null && version != RetirementPlanBaselineCodec.CURRENT_FORMAT_VERSION)
      throw new IllegalStateException(
          "Unsupported Long-Term planning baseline version: " + version);
    // Null external versions identify legacy raw JSON rows; the codec also accepts those.
    return baselineJson.read(plan.getBaselineLongTermState());
  }

  private void validateName(Long portfolioId, String name, Long id) {
    if (name == null || isBlank(name)) throw new IllegalArgumentException("Plan name is required");
    plans.findAllByPortfolioIdAndArchivedFalseOrderByName(portfolioId).stream()
        .filter(p -> !Objects.equals(p.getId(), id))
        .filter(p -> p.getName() != null && p.getName().trim().equalsIgnoreCase(name.trim()))
        .findAny()
        .ifPresent(
            p -> {
              throw new IllegalArgumentException("Plan name already exists");
            });
  }

  private Long savePlan(RetirementPlanEntity plan) {
    try {
      return plans.saveAndFlush(plan).getId();
    } catch (DataIntegrityViolationException e) {
      if (hasConstraint(e, "uq_retirement_plans_active_name"))
        throw new IllegalArgumentException("Plan name already exists", e);
      throw e;
    }
  }

  private Long touchAndSave(RetirementPlanEntity plan) {
    plan.setUpdatedAt(Instant.now(clock));
    return savePlan(plan);
  }

  private static boolean hasConstraint(Throwable failure, String expected) {
    for (Throwable current = failure; current != null; current = current.getCause())
      if (current instanceof org.hibernate.exception.ConstraintViolationException violation
          && expected.equals(violation.getConstraintName())) return true;
    return false;
  }
}
