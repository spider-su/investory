package com.smartbox.investory.ryczalt.integration.fx;

import java.time.LocalDate;

public interface FxRateSourcePort {
  FxRate fetch(String currency, LocalDate effectiveDate);
}
