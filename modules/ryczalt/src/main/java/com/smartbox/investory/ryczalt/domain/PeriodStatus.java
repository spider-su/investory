package com.smartbox.investory.ryczalt.domain;

/** Preliminary accounting-period lifecycle. Payment and closing semantics remain future work. */
public enum PeriodStatus {
  OPEN,
  DIRTY,
  CALCULATED,
  PAID,
  FROZEN
}
