package com.smartbox.investory.retirement.infrastructure.plan;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationAssumptionsPersistenceMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
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

  public CanonicalRetirementPlanService(
      RetirementPlanRepository plans,
      RetirementPlanEventRepository events,
      RetirementPlanBaselineCodec baselineJson) {
    this.plans = plans;
    this.events = events;
    this.baselineJson = baselineJson;
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
    return plans.save(plan).getId();
  }

  @Override
  public Long updatePlan(UpdatePlanCommand command) {
    Objects.requireNonNull(command, "command");
    RetirementPlanEntity plan = get(command.portfolioId(), command.planId());
    validateName(command.portfolioId(), command.name(), command.planId());
    plan.setName(command.name().trim());
    write(plan, command.assumptions(), baseline(plan));
    return plans.save(plan).getId();
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
    if (event.getCreatedAt() == null) event.setCreatedAt(Instant.now());
    return events.save(event).getId();
  }

  @Override
  public void deleteEvent(Long portfolioId, Long planId, Long eventId) {
    get(portfolioId, planId);
    events
        .findByIdAndPlanId(eventId, planId)
        .orElseThrow(RetirementPlanApi.EventNotFoundException::new);
    events.deleteById(eventId);
  }

  @Override
  public void deletePlan(Long portfolioId, Long planId) {
    RetirementPlanEntity plan = get(portfolioId, planId);
    plan.setArchived(true);
    plans.save(plan);
  }

  @Override
  public void rebaselinePlan(Long portfolioId, Long planId, PlanningBaseline baseline) {
    RetirementPlanEntity plan = get(portfolioId, planId);
    writeBaseline(plan, baseline);
    plans.save(plan);
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
    plan.setBaselineLongTermStateVersion(1);
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
            baselineJson.read(plan.getBaselineLongTermState()));
  }

  private void validateName(Long portfolioId, String name, Long id) {
    if (name == null || isBlank(name)) throw new IllegalArgumentException("Plan name is required");
    plans.findAllByPortfolioIdAndArchivedFalseOrderByName(portfolioId).stream()
        .filter(p -> !Objects.equals(p.getId(), id))
        .filter(p -> p.getName().equalsIgnoreCase(name.trim()))
        .findAny()
        .ifPresent(
            p -> {
              throw new IllegalArgumentException("Plan name already exists");
            });
  }
}
