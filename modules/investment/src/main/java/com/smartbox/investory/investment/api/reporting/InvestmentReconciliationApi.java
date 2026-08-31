package com.smartbox.investory.investment.api.reporting;

import java.time.LocalDate;

/** UI-facing reconciliation report boundary. */
public interface InvestmentReconciliationApi {
  Object load(String mode, LocalDate asOf);
}
