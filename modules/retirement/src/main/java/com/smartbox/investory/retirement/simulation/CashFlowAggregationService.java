package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Aggregates generic planned flows. It has no asset-category rules. */
public final class CashFlowAggregationService {
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  public Result aggregate(Period period, List<PlannedCashFlow> flows) {
    return aggregate(period, flows, true, true);
  }

  public Result aggregateProjected(Period period, List<PlannedCashFlow> flows) {
    return aggregate(period, flows, false, true);
  }

  /** Projects a remaining period without letting an earlier actual occurrence erase it. */
  public Result aggregateProjectedRaw(Period period, List<PlannedCashFlow> flows) {
    return aggregateInternal(period, flows == null ? List.of() : flows, false, true);
  }

  public Result aggregateReview(Period period, List<PlannedCashFlow> flows) {
    return aggregate(period, flows, true, true);
  }

  public Result aggregate(
      Period period, List<PlannedCashFlow> flows, boolean includeActual, boolean includeProjected) {
    return aggregateInternal(period, replaceProjectedWithActual(flows == null ? List.of() : flows), includeActual, includeProjected);
  }

  private Result aggregateInternal(
      Period period, List<PlannedCashFlow> selected, boolean includeActual, boolean includeProjected) {
    BigDecimal actualIncome = BigDecimal.ZERO;
    BigDecimal actualExpenses = BigDecimal.ZERO;
    BigDecimal projectedIncome = BigDecimal.ZERO;
    BigDecimal projectedExpenses = BigDecimal.ZERO;
    for (PlannedCashFlow flow : selected) {
      if (!applicable(flow, period) || !included(flow.source(), includeActual, includeProjected)) continue;
      BigDecimal value = amountFor(flow, period);
      if (flow.source() == ProjectionSource.ACTUAL) {
        if (flow.direction() == CashFlowDirection.INCOME) actualIncome = actualIncome.add(value);
        else actualExpenses = actualExpenses.add(value);
      } else if (flow.direction() == CashFlowDirection.INCOME) {
        projectedIncome = projectedIncome.add(value);
      } else {
        projectedExpenses = projectedExpenses.add(value);
      }
    }
    return new Result(actualIncome, actualExpenses, projectedIncome, projectedExpenses);
  }

  private static boolean included(ProjectionSource source, boolean actual, boolean projected) {
    return source == ProjectionSource.ACTUAL ? actual : projected;
  }

  private static List<PlannedCashFlow> replaceProjectedWithActual(List<PlannedCashFlow> flows) {
    List<PlannedCashFlow> actual = flows.stream()
        .filter(flow -> flow.source() == ProjectionSource.ACTUAL)
        .toList();
    List<PlannedCashFlow> result = new ArrayList<>();
    for (PlannedCashFlow flow : flows) {
      if (flow.source() == ProjectionSource.ACTUAL) {
        result.add(flow);
        continue;
      }
      List<PlannedCashFlow> replacements = actual.stream()
          .filter(candidate -> sameOccurrence(flow, candidate))
          .toList();
      if (replacements.isEmpty()) {
        result.add(flow);
      } else if (flow.cadence() == CashFlowCadence.MONTHLY) {
        LocalDate remainingFrom = flow.effectiveFrom();
        for (PlannedCashFlow replacement : replacements) {
          LocalDate coveredThrough = replacement.effectiveTo() == null
              ? YearMonth.from(replacement.effectiveFrom()).atEndOfMonth()
              : replacement.effectiveTo();
          if (!coveredThrough.isBefore(remainingFrom)) {
            remainingFrom = coveredThrough.plusDays(1);
          }
        }
        if (flow.effectiveTo() == null || !remainingFrom.isAfter(flow.effectiveTo())) {
          result.add(new PlannedCashFlow(
              flow.id(), flow.category(), flow.direction(), flow.cadence(), flow.amount(),
              remainingFrom, flow.effectiveTo(), flow.eventDate(), flow.source(), flow.currency()));
        }
      }
    }
    return result;
  }

  private static boolean sameOccurrence(PlannedCashFlow projected, PlannedCashFlow actual) {
    if (!projected.id().equals(actual.id()) || projected.cadence() != actual.cadence()) return false;
    if (projected.cadence() == CashFlowCadence.ONE_OFF) {
      return projected.eventDate().equals(actual.eventDate());
    }
    if (projected.cadence() == CashFlowCadence.ANNUAL) {
      return projected.effectiveFrom().getYear() == actual.effectiveFrom().getYear();
    }
    LocalDate projectedEnd = projected.effectiveTo();
    LocalDate actualEnd = actual.effectiveTo() == null ? actual.effectiveFrom() : actual.effectiveTo();
    return !actualEnd.isBefore(projected.effectiveFrom())
        && (projectedEnd == null || !actual.effectiveFrom().isAfter(projectedEnd));
  }

  private static boolean applicable(PlannedCashFlow flow, Period period) {
    LocalDate start = flow.effectiveFrom();
    LocalDate end = flow.effectiveTo() == null ? period.end() : flow.effectiveTo();
    return switch (flow.cadence()) {
      case ONE_OFF -> {
        LocalDate event = flow.eventDate() == null ? start : flow.eventDate();
        yield !event.isBefore(period.start()) && !event.isAfter(period.end())
            && !start.isAfter(event) && !end.isBefore(event);
      }
      case ANNUAL -> !start.isAfter(period.end()) && !end.isBefore(period.start());
      case MONTHLY -> !YearMonth.from(start).isAfter(YearMonth.from(period.end()))
          && !YearMonth.from(end).isBefore(YearMonth.from(period.start()));
    };
  }

  private static BigDecimal amountFor(PlannedCashFlow flow, Period period) {
    return switch (flow.cadence()) {
      case ONE_OFF, ANNUAL -> flow.amount();
      case MONTHLY -> {
        YearMonth first = YearMonth.from(period.start());
        YearMonth last = YearMonth.from(period.end());
        YearMonth effective = YearMonth.from(flow.effectiveFrom());
        YearMonth until = YearMonth.from(flow.effectiveTo() == null ? period.end() : flow.effectiveTo());
        YearMonth firstApplicable = effective.isAfter(first) ? effective : first;
        YearMonth lastApplicable = until.isBefore(last) ? until : last;
        long months = lastApplicable.isBefore(firstApplicable) ? 0
            : java.time.temporal.ChronoUnit.MONTHS.between(firstApplicable, lastApplicable) + 1;
        yield flow.amount().multiply(BigDecimal.valueOf(months));
      }
    };
  }

  public record Result(
      BigDecimal actualIncome,
      BigDecimal actualExpenses,
      BigDecimal projectedIncome,
      BigDecimal projectedExpenses) {
    public Result {
      actualIncome = nz(actualIncome);
      actualExpenses = nz(actualExpenses);
      projectedIncome = nz(projectedIncome);
      projectedExpenses = nz(projectedExpenses);
    }

    public BigDecimal periodIncome() { return actualIncome.add(projectedIncome); }
    public BigDecimal periodExpenses() { return actualExpenses.add(projectedExpenses); }
    public BigDecimal netCashFlow() { return periodIncome().subtract(periodExpenses()); }
    public BigDecimal fundingGap() { return netCashFlow().negate().max(BigDecimal.ZERO); }
    public BigDecimal surplus() { return netCashFlow().max(BigDecimal.ZERO); }
    public BigDecimal rounded(int scale) {
      return netCashFlow().setScale(scale, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
  }
}
