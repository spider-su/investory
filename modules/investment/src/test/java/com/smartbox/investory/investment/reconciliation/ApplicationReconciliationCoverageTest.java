package com.smartbox.investory.investment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationStatus;
import com.smartbox.investory.investment.port.export.SecondaryAdapterStatusReader;
import com.smartbox.investory.investment.port.export.SecondaryAdapterStatusReader.ExportStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@DisplayName("Application Reconciliation Coverage")
class ApplicationReconciliationCoverageTest {

  private static final ReconciliationContext CONTEXT =
      new ReconciliationContext(Instant.parse("2026-08-25T10:00:00Z"), LocalDate.of(2026, 8, 25));

  @DisplayName("c6Passes Only When Dashboard Fallback Evidence Matches Canonical Reporting")
  @Test
  void c6PassesOnlyWhenDashboardFallbackEvidenceMatchesCanonicalReporting() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(250)))
        .thenReturn(java.util.List.of());

    ReconciliationCheckResult result =
        DatabaseEvidenceReconciliationCheck.forCheckpoint(jdbc, ReconciliationCheckpoint.C6)
            .execute(CONTEXT);

    assertThat(result.status()).isEqualTo(ReconciliationStatus.PASS);
    assertThat(result.evidenceSource()).contains("recon_v_portfolio_service_fallback");
  }

  @DisplayName("c1Uses Effective Tolerances And One Bounded Evidence Query")
  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void c1UsesEffectiveTolerancesAndOneBoundedEvidenceQuery() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.any(RowMapper.class), eq(250)))
        .thenReturn(java.util.List.of());

    DatabaseEvidenceReconciliationCheck.forCheckpoint(jdbc, ReconciliationCheckpoint.C1)
        .execute(CONTEXT);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.any(RowMapper.class), eq(250));
    assertThat(sql.getValue())
        .contains("reconciliation_values_match")
        .contains("recon_v_account_daily_cashflow_full_precision")
        .contains("account_cash_delta")
        .contains("COUNT(*) OVER ()")
        .contains("LIMIT ?");
  }

  @DisplayName("c7Passes For Current Yahoo Snapshot")
  @Test
  void c7PassesForCurrentYahooSnapshot() {
    SecondaryAdapterStatusReader yahoo = mock(SecondaryAdapterStatusReader.class);
    when(yahoo.status())
        .thenReturn(
            new ExportStatus(
                ZonedDateTime.of(2026, 8, 25, 12, 0, 0, 0, ZoneId.of("Europe/Warsaw")), true));

    ReconciliationCheckResult result =
        new SecondaryAdapterReconciliationCheck(yahoo).execute(CONTEXT);

    assertThat(result.status()).isEqualTo(ReconciliationStatus.PASS);
    assertThat(result.issueCount()).isZero();
  }

  @DisplayName("c7Fails Closed For Stale Yahoo Snapshot")
  @Test
  void c7FailsClosedForStaleYahooSnapshot() {
    SecondaryAdapterStatusReader yahoo = mock(SecondaryAdapterStatusReader.class);
    when(yahoo.status())
        .thenReturn(
            new ExportStatus(
                ZonedDateTime.of(2026, 8, 24, 12, 0, 0, 0, ZoneId.of("Europe/Warsaw")), false));

    ReconciliationCheckResult result =
        new SecondaryAdapterReconciliationCheck(yahoo).execute(CONTEXT);

    assertThat(result.status()).isEqualTo(ReconciliationStatus.FAIL);
    assertThat(result.issues().getFirst().checkCode()).isEqualTo("YAHOO_EXPORT_STALE");
  }

  @DisplayName("c7Reviews Missing Snapshot Instead Of Reporting Unchecked")
  @Test
  void c7ReviewsMissingSnapshotInsteadOfReportingUnchecked() {
    SecondaryAdapterStatusReader yahoo = mock(SecondaryAdapterStatusReader.class);
    when(yahoo.status()).thenReturn(new ExportStatus(null, false));

    ReconciliationCheckResult result =
        new SecondaryAdapterReconciliationCheck(yahoo).execute(CONTEXT);

    assertThat(result.status()).isEqualTo(ReconciliationStatus.REVIEW);
    assertThat(result.issues().getFirst().checkCode()).isEqualTo("YAHOO_EXPORT_NOT_CREATED");
  }
}
