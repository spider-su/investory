package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.application.RyczaltInvoiceConflictException;

/** Raised when a canonical fact is changed after the period was frozen. */
public class FrozenPeriodMutationException extends RyczaltInvoiceConflictException {
  public FrozenPeriodMutationException(long profileId, int year, int month) {
    super("Period is frozen: profile=" + profileId + ", period=" + year + "-" + month);
  }
}
