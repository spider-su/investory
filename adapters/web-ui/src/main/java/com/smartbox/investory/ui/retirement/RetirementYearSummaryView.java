package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.PlanningTimelineState;
import com.smartbox.investory.retirement.planning.PlanningTimelineYear;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compact, user-facing summary of one authoritative yearly projection row. */
public record RetirementYearSummaryView(
    int year,
    String state,
    BigDecimal spending,
    BigDecimal income,
    BigDecimal netCash,
    BucketSummary cash,
    BucketSummary bonds,
    BucketSummary equities,
    BucketSummary realEstate,
    String status) {

  public record BucketSummary(BigDecimal startValue, BigDecimal annualValue, BigDecimal endValue) {}

  public static Map<Integer, RetirementYearSummaryView> from(
      PlanningTimeline timeline, Map<Integer, PlanningTimelineMoney> moneyByYear) {
    Map<Integer, RetirementYearSummaryView> result = new LinkedHashMap<>();
    for (PlanningTimelineYear row : timeline.years()) {
      PlanningTimelineMoney money = moneyByYear.get(row.year());
      BigDecimal spending = money == null ? null : money.annualCosts();
      BigDecimal income = money == null ? null : money.totalIncome();
      BigDecimal netCash = spending == null || income == null ? null : income.subtract(spending);
      String state = stateLabel(row.state());
      String status = statusLabel(row);
      result.put(
          row.year(),
          new RetirementYearSummaryView(
              row.year(),
              state,
              spending,
              income,
              netCash,
              new BucketSummary(money == null ? null : money.cashStart(), null, money == null ? null : money.cashEnd()),
              new BucketSummary(money == null ? null : money.bondsStart(), money == null ? null : money.bondReturn(), money == null ? null : money.bondsEnd()),
              new BucketSummary(money == null ? null : money.equitiesStart(), money == null ? null : money.equityReturn(), money == null ? null : money.equitiesEnd()),
              new BucketSummary(money == null ? null : money.realEstateStart(), money == null ? null : money.rentalIncome(), money == null ? null : money.realEstateEnd()),
              status));
    }
    return Collections.unmodifiableMap(result);
  }

  private static String stateLabel(PlanningTimelineState state) {
    return switch (state) {
      case ACTUAL, NEEDS_REVIEW -> "Actual";
      case LIVE -> "Live";
      case PROJECTED -> "Projected";
    };
  }

  private static String statusLabel(PlanningTimelineYear row) {
    return switch (row.state()) {
      case ACTUAL, NEEDS_REVIEW -> "Actual";
      case LIVE -> "Live";
      case PROJECTED -> row.projection() != null && row.projection().failed() ? "Unfunded" : "Funded";
    };
  }
}
