package com.smartbox.investory.ryczalt.domain;

/** Native period lifecycle. Calculation and obligation statuses remain separate concerns. */
public enum PeriodStatus {
  OPEN,
  DIRTY,
  CALCULATED,
  PAID,
  FROZEN;

  public boolean isFrozen() {
    return this == FROZEN;
  }

  public boolean canFreeze() {
    return this == CALCULATED || this == PAID;
  }
}
