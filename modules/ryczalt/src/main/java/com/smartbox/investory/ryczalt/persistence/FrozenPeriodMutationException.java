package com.smartbox.investory.ryczalt.persistence;

/** Raised when a canonical fact is changed after the period was frozen. */
public class FrozenPeriodMutationException extends IllegalStateException {
  public FrozenPeriodMutationException(long profileId, int year, int month) {
    super("Period is frozen: profile=" + profileId + ", period=" + year + "-" + month);
  }
}
