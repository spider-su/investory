package com.smartbox.investory.retirement.infrastructure.annualcost;

import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.PlanningMetric;
import com.smartbox.investory.retirement.api.model.PlanningValueKind;
import com.smartbox.investory.retirement.api.model.PlanningYearStatus;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanEntity;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanRepository;
import com.smartbox.investory.retirement.infrastructure.planningyear.RetirementPlanningYearEntity;
import com.smartbox.investory.retirement.infrastructure.planningyear.RetirementPlanningYearRepository;
import com.smartbox.investory.retirement.infrastructure.planningyear.RetirementPlanningYearStateCodec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RetirementAnnualCostsService implements RetirementAnnualCostsApi {
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  private final RetirementPlanRepository plans;
  private final RetirementAnnualCostGroupRepository groups;
  private final RetirementAnnualCostYearRepository costYears;
  private final RetirementPlanningYearRepository planningYears;
  private final RetirementPlanningYearStateCodec yearState;
  private final Clock clock;

  public RetirementAnnualCostsService(
      RetirementPlanRepository plans,
      RetirementAnnualCostGroupRepository groups,
      RetirementAnnualCostYearRepository costYears,
      RetirementPlanningYearRepository planningYears,
      RetirementPlanningYearStateCodec yearState,
      Clock clock) {
    this.plans = plans;
    this.groups = groups;
    this.costYears = costYears;
    this.planningYears = planningYears;
    this.yearState = yearState;
    this.clock = clock;
  }

  @Override
  public AnnualCosts load(Long portfolioId, Long planId, int year) {
    RetirementPlanEntity plan = getPlan(portfolioId, planId);
    return annualCosts(plan, ensureCostYear(planId, year));
  }

  @Override
  public AnnualCosts saveGroup(
      Long portfolioId,
      Long planId,
      int year,
      Long groupId,
      String name,
      BigDecimal monthlyAmount) {
    RetirementPlanEntity plan = getPlan(portfolioId, planId);
    RetirementAnnualCostYearEntity costYear = ensureCostYear(planId, year);
    String normalizedName = normalizeName(name);
    requireNonNegative(monthlyAmount, "Monthly amount");
    boolean duplicate =
        groupId == null
            ? groups.existsByYearIdAndNameIgnoreCase(costYear.getId(), normalizedName)
            : groups.existsByYearIdAndNameIgnoreCaseAndIdNot(
                costYear.getId(), normalizedName, groupId);
    if (duplicate) throw new IllegalArgumentException("A cost group with this name already exists");

    RetirementAnnualCostGroupEntity group =
        groupId == null
            ? new RetirementAnnualCostGroupEntity()
            : groups
                .findByIdAndYearId(groupId, costYear.getId())
                .orElseThrow(RetirementPlanApi.PlanNotFoundException::new);
    group.setYearId(costYear.getId());
    group.setName(normalizedName);
    group.setMonthlyAmount(monthlyAmount);
    Instant now = Instant.now(clock);
    if (group.getCreatedAt() == null) group.setCreatedAt(now);
    group.setUpdatedAt(now);
    if (groupId == null) group.setSortOrder(groups.countByYearId(costYear.getId()));
    groups.save(group);
    touchPlan(plan);
    return annualCosts(plan, costYear);
  }

  @Override
  public AnnualCosts deleteGroup(Long portfolioId, Long planId, int year, Long groupId) {
    RetirementPlanEntity plan = getPlan(portfolioId, planId);
    RetirementAnnualCostYearEntity costYear = ensureCostYear(planId, year);
    RetirementAnnualCostGroupEntity group =
        groups
            .findByIdAndYearId(groupId, costYear.getId())
            .orElseThrow(RetirementPlanApi.PlanNotFoundException::new);
    groups.delete(group);
    touchPlan(plan);
    return annualCosts(plan, costYear);
  }

  @Override
  public AnnualCosts saveAnnualExtras(
      Long portfolioId, Long planId, int year, BigDecimal annualAmount) {
    RetirementPlanEntity plan = getPlan(portfolioId, planId);
    RetirementAnnualCostYearEntity costYear = ensureCostYear(planId, year);
    requireNonNegative(annualAmount, "Annual cost");
    plan.setAnnualDiscretionaryExpenses(annualAmount);
    touchPlan(plan);
    return annualCosts(plan, costYear);
  }

  private AnnualCosts annualCosts(
      RetirementPlanEntity plan, RetirementAnnualCostYearEntity costYear) {
    List<RetirementAnnualCostGroupEntity> rows =
        groups.findAllByYearIdOrderBySortOrderAscIdAsc(costYear.getId());
    BigDecimal assignedMonthly =
        rows.stream()
            .map(RetirementAnnualCostGroupEntity::getMonthlyAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    ApprovedLivingCost approved =
        latestApprovedLivingCost(plan.getPortfolioId(), plan.getId(), costYear.getYear());
    BigDecimal plannedMonthly = monthlyAmount(plan.getAnnualLivingExpenses());
    BigDecimal totalMonthly = plannedMonthly;
    if (approved != null) totalMonthly = totalMonthly.max(approved.monthlyAmount());
    BigDecimal otherMonthly = totalMonthly.subtract(assignedMonthly).max(BigDecimal.ZERO);
    BigDecimal annualLiving = totalMonthly.multiply(TWELVE);
    return new AnnualCosts(
        plan.getId(),
        costYear.getYear(),
        plan.getName(),
        rows.stream()
            .map(
                row ->
                    new Group(
                        row.getId(), row.getName(), row.getMonthlyAmount(), row.getSortOrder()))
            .toList(),
        totalMonthly,
        annualLiving,
        plan.getAnnualDiscretionaryExpenses(),
        approved == null ? null : approved.year(),
        approved == null ? null : approved.monthlyAmount(),
        otherMonthly);
  }

  private ApprovedLivingCost latestApprovedLivingCost(
      Long portfolioId, Long planId, int throughYear) {
    return planningYears.findAllByPortfolioIdOrderByYearAsc(portfolioId).stream()
        .filter(year -> year.getYear() <= throughYear)
        .filter(year -> year.getStatus() == PlanningYearStatus.CLOSED)
        .filter(
            year -> {
              yearState.readInto(year);
              return Objects.equals(year.getBaselinePlanId(), planId);
            })
        .map(this::approvedLivingCost)
        .filter(Objects::nonNull)
        .max(Comparator.comparingInt(ApprovedLivingCost::year))
        .orElse(null);
  }

  private RetirementAnnualCostYearEntity ensureCostYear(Long planId, int year) {
    if (year < 1900 || year > 2200) throw new IllegalArgumentException("Year is out of range");
    return costYears
        .findByPlanIdAndYear(planId, year)
        .orElseGet(
            () -> {
              RetirementAnnualCostYearEntity created = new RetirementAnnualCostYearEntity();
              created.setPlanId(planId);
              created.setYear(year);
              created.setCreatedAt(Instant.now(clock));
              created = costYears.save(created);
              RetirementAnnualCostYearEntity target = created;
              costYears.findAllByPlanIdAndYearLessThanOrderByYearDesc(planId, year).stream()
                  .findFirst()
                  .ifPresent(
                      source -> {
                        groups
                            .findAllByYearIdOrderBySortOrderAscIdAsc(source.getId())
                            .forEach(
                                previous -> {
                                  RetirementAnnualCostGroupEntity copy =
                                      new RetirementAnnualCostGroupEntity();
                                  copy.setYearId(target.getId());
                                  copy.setName(previous.getName());
                                  copy.setMonthlyAmount(previous.getMonthlyAmount());
                                  copy.setSortOrder(previous.getSortOrder());
                                  Instant now = Instant.now(clock);
                                  copy.setCreatedAt(now);
                                  copy.setUpdatedAt(now);
                                  groups.save(copy);
                                });
                      });
              return target;
            });
  }

  private void touchPlan(RetirementPlanEntity plan) {
    plan.setUpdatedAt(Instant.now(clock));
    plans.save(plan);
  }

  private ApprovedLivingCost approvedLivingCost(RetirementPlanningYearEntity year) {
    var actual = year.getValues().get(PlanningValueKind.ACTUAL);
    var value = actual == null ? null : actual.get(PlanningMetric.CORE_SPENDING);
    return value == null || value.approvedValue() == null
        ? null
        : new ApprovedLivingCost(year.getYear(), monthlyAmount(value.approvedValue()));
  }

  private static BigDecimal monthlyAmount(BigDecimal annualAmount) {
    return annualAmount.divide(TWELVE, 2, RoundingMode.HALF_UP);
  }

  private RetirementPlanEntity getPlan(Long portfolioId, Long planId) {
    return plans
        .findByIdAndPortfolioId(planId, portfolioId)
        .filter(plan -> !plan.isArchived())
        .orElseThrow(RetirementPlanApi.PlanNotFoundException::new);
  }

  private static String normalizeName(String name) {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Group name is required");
    String normalized = name.trim();
    if (normalized.length() > 120)
      throw new IllegalArgumentException("Group name must be 120 characters or fewer");
    return normalized;
  }

  private static void requireNonNegative(BigDecimal amount, String label) {
    if (amount == null || amount.signum() < 0)
      throw new IllegalArgumentException(label + " must be zero or greater");
  }

  private record ApprovedLivingCost(int year, BigDecimal monthlyAmount) {}
}
