package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.api.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.LongTermAssetCashFlow;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Shared effective-period rules used by interactive edits and bootstrap imports. */
public final class LongTermAssetPeriodRules {
  private LongTermAssetPeriodRules() {}

  public static boolean overlaps(
      LocalDate firstFrom, LocalDate firstTo, LocalDate secondFrom, LocalDate secondTo) {
    return !firstFrom.isAfter(secondTo == null ? LocalDate.MAX : secondTo)
        && !secondFrom.isAfter(firstTo == null ? LocalDate.MAX : firstTo);
  }

  public static boolean activeOn(LocalDate from, LocalDate to, LocalDate date) {
    return !from.isAfter(date) && (to == null || !to.isBefore(date));
  }

  public static boolean defaultPaidByTenant(CashFlowType type) {
    return type == CashFlowType.ADMIN_FEE || type == CashFlowType.UTILITIES;
  }

  public static void ensurePaidByTenant(LongTermAssetCashFlow flow) {
    if (flow.getPaidByTenant() == null) flow.setPaidByTenant(defaultPaidByTenant(flow.getType()));
  }

  public static void rejectOverlap(
      List<LongTermAssetCashFlow> existing,
      LongTermAssetCashFlow candidate,
      LocalDate from,
      LocalDate to,
      Set<Long> excludedIds) {
    if (existing.stream()
        .filter(flow -> flow.getType() == candidate.getType())
        .filter(flow -> !excludedIds.contains(flow.getId()))
        .anyMatch(flow -> overlaps(flow.getValidFrom(), flow.getValidTo(), from, to)))
      throw new IllegalArgumentException("Overlapping cash-flow period");
  }

  public static boolean samePeriodIdentity(
      LocalDate from, LocalDate to, LocalDate storedFrom, LocalDate storedTo) {
    // Bootstrap identity is asset/type/from. The end date and value are mutable attributes.
    return Objects.equals(from, storedFrom);
  }
}
