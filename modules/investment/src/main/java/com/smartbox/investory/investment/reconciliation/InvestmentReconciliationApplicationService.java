package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.InvestmentReconciliationApi;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Adapts reconciliation reports to the public Investment API. */
@Service
@RequiredArgsConstructor
public class InvestmentReconciliationApplicationService implements InvestmentReconciliationApi {
  private final ReconciliationReportService reports;

  @Override
  public Object load(String mode, LocalDate asOf) {
    ReconciliationMode selected = ReconciliationMode.valueOf(mode);
    return selected == ReconciliationMode.QUICK && asOf == null
        ? reports.load()
        : reports.load(
            new ReconciliationContext(
                selected, Instant.now(), asOf == null ? LocalDate.now() : asOf));
  }
}
