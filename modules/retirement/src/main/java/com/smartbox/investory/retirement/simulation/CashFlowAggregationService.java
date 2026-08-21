package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    Map<String, PlannedCashFlow> byIdentity = new LinkedHashMap<>();
    flows.stream().sorted(Comparator.comparing(f -> f.source() == ProjectionSource.ACTUAL ? 1 : 0))
        .forEach(flow -> byIdentity.put(identity(flow), flow));
    return new ArrayList<>(byIdentity.values());
  }

  private static String identity(PlannedCashFlow flow) {
    return flow.id() + "|" + flow.effectiveDate();
  }

  private static boolean applicable(PlannedCashFlow flow, Period period) {
    return switch (flow.cadence()) {
      case ONE_OFF, ANNUAL -> !flow.effectiveDate().isAfter(period.end())
          && !flow.effectiveDate().isBefore(period.start());
      case MONTHLY -> !YearMonth.from(flow.effectiveDate()).isAfter(YearMonth.from(period.end()));
    };
  }

  private static BigDecimal amountFor(PlannedCashFlow flow, Period period) {
    return switch (flow.cadence()) {
      case ONE_OFF, ANNUAL -> flow.amount();
      case MONTHLY -> {
        YearMonth first = YearMonth.from(period.start());
        YearMonth last = YearMonth.from(period.end());
        YearMonth effective = YearMonth.from(flow.effectiveDate());
        long months = Math.max(0, java.time.temporal.ChronoUnit.MONTHS.between(
            effective.isAfter(first) ? effective : first, last) + 1);
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
