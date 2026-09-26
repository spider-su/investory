package com.smartbox.investory.ryczalt.application;

import java.time.YearMonth;

/** Calculation cannot safely continue until a tax fact is reviewed. */
public class NeedsReviewException extends IllegalStateException {
  public NeedsReviewException(YearMonth month, String reason) {
    super("NEEDS_REVIEW for " + month + ": " + reason);
  }
}
