package com.smartbox.investory.ryczalt.application.query;

import java.time.YearMonth;

public class RyczaltPeriodNotFoundException extends IllegalArgumentException {
  public RyczaltPeriodNotFoundException(long profileId, YearMonth month) {
    super("Ryczalt period does not exist for profile " + profileId + ": " + month);
  }
}
