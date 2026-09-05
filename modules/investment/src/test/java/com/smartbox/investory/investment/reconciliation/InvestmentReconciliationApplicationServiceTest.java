package com.smartbox.investory.investment.reconciliation;

import static org.mockito.Mockito.verify;

import com.smartbox.investory.shared.time.ClockApplicationTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Investment Reconciliation Application Service")
class InvestmentReconciliationApplicationServiceTest {
  private static final ClockApplicationTime TIME =
      new ClockApplicationTime(
          Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC),
          ZoneId.of("Europe/Warsaw"));

  @Mock private ReconciliationReportService reports;

  @Mock
  private com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService refresh;

  @DisplayName("loads Portfolio Report")
  @Test
  void loadsPortfolioReport() {
    var service = new InvestmentReconciliationApplicationService(reports, refresh, TIME);

    service.loadReconciliationReport(7L);

    verify(reports).generateReport(org.mockito.ArgumentMatchers.any(ReconciliationContext.class));
  }

  @Test
  void rejectsMissingOrInvalidPortfolioId() {
    var service = new InvestmentReconciliationApplicationService(reports, refresh, TIME);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.loadReconciliationReport(null))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.loadReconciliationReport(0L))
        .isInstanceOf(IllegalArgumentException.class);
    org.mockito.Mockito.verifyNoInteractions(reports);
  }
}
