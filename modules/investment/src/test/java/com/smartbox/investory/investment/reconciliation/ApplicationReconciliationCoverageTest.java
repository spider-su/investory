package com.smartbox.investory.investment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.integration.export.yahoo.YahooExportService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationReconciliationCoverageTest {

  private static final ReconciliationContext CONTEXT =
      new ReconciliationContext(
          ReconciliationMode.QUICK, Instant.parse("2026-08-25T10:00:00Z"), LocalDate.of(2026, 8, 25));

  @Test
  void c6PassesOnlyWhenDashboardFallbackEvidenceMatchesCanonicalReporting() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

    ReconciliationCheckResult result =
        DatabaseEvidenceReconciliationCheck.forCheckpoint(jdbc, ReconciliationCheckpoint.C6)
            .execute(CONTEXT);

    assertThat(result.status()).isEqualTo(ReconciliationStatus.PASS);
    assertThat(result.evidenceSource()).contains("v_portfolio_service_fallback_reconciliation");
  }

  @Test
  void c7PassesForCurrentYahooSnapshot() {
    YahooExportService yahoo = mock(YahooExportService.class);
    when(yahoo.status())
        .thenReturn(
            new YahooExportService.YahooExportStatus(
                ZonedDateTime.of(2026, 8, 25, 12, 0, 0, 0, ZoneId.of("Europe/Warsaw")), true));

    ReconciliationCheckResult result =
        new SecondaryAdapterReconciliationCheck(yahoo).execute(CONTEXT);

    assertThat(result.status()).isEqualTo(ReconciliationStatus.PASS);
    assertThat(result.issueCount()).isZero();
  }

  @Test
  void c7FailsClosedForStaleYahooSnapshot() {
    YahooExportService yahoo = mock(YahooExportService.class);
    when(yahoo.status())
        .thenReturn(
            new YahooExportService.YahooExportStatus(
                ZonedDateTime.of(2026, 8, 24, 12, 0, 0, 0, ZoneId.of("Europe/Warsaw")), false));

    ReconciliationCheckResult result =
        new SecondaryAdapterReconciliationCheck(yahoo).execute(CONTEXT);

    assertThat(result.status()).isEqualTo(ReconciliationStatus.FAIL);
    assertThat(result.issues().getFirst().checkCode()).isEqualTo("YAHOO_EXPORT_STALE");
  }

  @Test
  void c7ReviewsMissingSnapshotInsteadOfReportingUnchecked() {
    YahooExportService yahoo = mock(YahooExportService.class);
    when(yahoo.status()).thenReturn(new YahooExportService.YahooExportStatus(null, false));

    ReconciliationCheckResult result =
        new SecondaryAdapterReconciliationCheck(yahoo).execute(CONTEXT);

    assertThat(result.status()).isEqualTo(ReconciliationStatus.REVIEW);
    assertThat(result.issues().getFirst().checkCode()).isEqualTo("YAHOO_EXPORT_NOT_CREATED");
  }
}
