package com.smartbox.investory.longterm.application.service;

import java.time.LocalDate;
import java.util.Objects;

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

  public static boolean samePeriodIdentity(
      LocalDate from, LocalDate to, LocalDate storedFrom, LocalDate storedTo) {
    // Bootstrap identity is asset/type/from. The end date and value are mutable attributes.
    return Objects.equals(from, storedFrom);
  }
}
