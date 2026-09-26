package com.smartbox.investory.ryczalt.application.onboarding;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RyczaltReadinessService {
  private final JdbcTemplate jdbc;

  public RyczaltReadinessService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public RyczaltReadiness get(long profileId, YearMonth month) {
    Snapshot s = snapshot(profileId, month);
    boolean company = s.nip != null && s.companyName != null;
    boolean accounting =
        "RYCZALT".equals(s.taxation)
            && s.rate != null
            && "MONTHLY".equals(s.pit)
            && "MONTHLY".equals(s.vat);
    boolean zus = Boolean.TRUE.equals(s.jdg) && s.zusRegime != null && s.healthMethod != null;
    boolean noActivity =
        s.confirmed
            && s.invoiceCount == s.confirmedInvoiceCount
            && s.transactionCount == s.confirmedTransactionCount;
    boolean calculationsReady =
        s.periodExists
            && s.periodStatus != null
            && List.of("CALCULATED", "PAID", "FROZEN").contains(s.periodStatus)
            && s.readyCalculations >= 3;
    String periodState =
        !"COMPLETED".equals(s.onboardingState)
            ? "SETUP_REQUIRED"
            : noActivity
                ? "NO_ACTIVITY_CONFIRMED"
                : s.invoiceCount == 0
                    ? "NO_INVOICES"
                    : !calculationsReady ? "CALCULATION_INCOMPLETE" : "READY";
    List<RyczaltReadiness.Item> items = new ArrayList<>();
    items.add(
        new RyczaltReadiness.Item(
            "COMPANY_CONFIGURATION",
            company ? "COMPLETE" : "ACTION_REQUIRED",
            company ? null : "REVIEW_CONFIGURATION"));
    items.add(
        new RyczaltReadiness.Item(
            "ACCOUNTING_CONFIGURATION",
            accounting ? "COMPLETE" : "ACTION_REQUIRED",
            accounting ? null : "REVIEW_CONFIGURATION"));
    items.add(
        new RyczaltReadiness.Item(
            "ZUS_CONFIGURATION",
            zus ? "COMPLETE" : "ACTION_REQUIRED",
            zus ? null : "REVIEW_CONFIGURATION"));
    items.add(
        new RyczaltReadiness.Item(
            "PERIOD_DATA",
            noActivity || calculationsReady ? "COMPLETE" : "ACTION_REQUIRED",
            noActivity || calculationsReady
                ? null
                : s.invoiceCount > 0 ? "REVIEW_PERIOD_DATA" : "ADD_INVOICE"));
    items.add(
        new RyczaltReadiness.Item(
            "KSEF_CONNECTION",
            "CONNECTED".equals(s.ksef) ? "COMPLETE" : "OPTIONAL",
            "CONNECTED".equals(s.ksef) ? null : "CONNECT_KSEF"));
    List<RyczaltReadiness.Calculation> calculations =
        List.of(
            new RyczaltReadiness.Calculation("RYCZALT", s.calculationStatus("RYCZALT"), List.of()),
            new RyczaltReadiness.Calculation("VAT", s.calculationStatus("VAT"), List.of()),
            new RyczaltReadiness.Calculation("ZUS", s.calculationStatus("ZUS"), List.of()));
    return new RyczaltReadiness(
        "COMPLETED".equals(s.onboardingState),
        company,
        accounting,
        zus,
        new RyczaltReadiness.Ksef(s.ksef, s.ksefSync),
        new RyczaltReadiness.Period(month, periodState, noActivity),
        calculations,
        items);
  }

  @Transactional
  public RyczaltReadiness.NoActivityConfirmation confirmNoActivity(
      long profileId, YearMonth month, String actor) {
    Snapshot s = snapshot(profileId, month);
    String by = actor == null || actor.isBlank() ? "authenticated-user" : actor;
    jdbc.update(
        """
        INSERT INTO investory.ryczalt_period_activity_confirmation
          (profile_id, period_year, period_month, confirmation_type, confirmed_at, confirmed_by, invoice_count_at_confirmation, transaction_count_at_confirmation)
        VALUES (?, ?, ?, 'NO_REVENUE', CURRENT_TIMESTAMP, ?, ?, ?)
        ON CONFLICT (profile_id, period_year, period_month) DO UPDATE SET confirmed_at = EXCLUDED.confirmed_at, confirmed_by = EXCLUDED.confirmed_by,
          invoice_count_at_confirmation = EXCLUDED.invoice_count_at_confirmation, transaction_count_at_confirmation = EXCLUDED.transaction_count_at_confirmation
        """,
        profileId,
        month.getYear(),
        month.getMonthValue(),
        by,
        s.invoiceCount,
        s.transactionCount);
    return jdbc.queryForObject(
        """
        SELECT period_year, period_month, confirmation_type, confirmed_at, confirmed_by, invoice_count_at_confirmation, transaction_count_at_confirmation
          FROM investory.ryczalt_period_activity_confirmation WHERE profile_id = ? AND period_year = ? AND period_month = ?
        """,
        (rs, row) ->
            new RyczaltReadiness.NoActivityConfirmation(
                YearMonth.of(rs.getInt(1), rs.getInt(2)),
                rs.getString(3),
                rs.getTimestamp(4).toInstant(),
                rs.getString(5),
                rs.getInt(6) + ":" + rs.getInt(7)),
        profileId,
        month.getYear(),
        month.getMonthValue());
  }

  private Snapshot snapshot(long profileId, YearMonth month) {
    return jdbc.queryForObject(
        """
        SELECT o.state, o.nip, o.company_name, o.taxation_method, o.ryczalt_rate, o.pit_frequency, o.vat_frequency, o.jdg_active, o.zus_regime, o.zus_health_method, o.ksef_state,
          (SELECT COUNT(*) FROM investory.ryczalt_invoice i WHERE i.profile_id = o.profile_id AND i.accounting_date >= make_date(?, ?, 1) AND i.accounting_date < make_date(?, ?, 1)),
          (SELECT COUNT(*) FROM investory.ryczalt_transaction t WHERE t.profile_id = o.profile_id AND t.booking_date >= make_date(?, ?, 1) AND t.booking_date < make_date(?, ?, 1)),
          EXISTS (SELECT 1 FROM investory.ryczalt_period p WHERE p.profile_id = o.profile_id AND p.period_year = ? AND p.period_month = ?),
          (SELECT p.status FROM investory.ryczalt_period p WHERE p.profile_id = o.profile_id AND p.period_year = ? AND p.period_month = ?),
          (SELECT COUNT(*) FROM investory.ryczalt_calculation c JOIN investory.ryczalt_period p ON p.id = c.period_id WHERE c.profile_id = o.profile_id AND p.period_year = ? AND p.period_month = ? AND c.is_current AND c.status IN ('CURRENT','CALCULATED','FROZEN')),
          (SELECT COALESCE(string_agg(c.calculation_type || ':' || c.status, ','), '') FROM investory.ryczalt_calculation c JOIN investory.ryczalt_period p ON p.id = c.period_id WHERE c.profile_id = o.profile_id AND p.period_year = ? AND p.period_month = ? AND c.is_current),
          EXISTS (SELECT 1 FROM investory.ryczalt_period_activity_confirmation a WHERE a.profile_id = o.profile_id AND a.period_year = ? AND a.period_month = ?),
          COALESCE((SELECT a.invoice_count_at_confirmation FROM investory.ryczalt_period_activity_confirmation a WHERE a.profile_id = o.profile_id AND a.period_year = ? AND a.period_month = ?), -1),
          COALESCE((SELECT a.transaction_count_at_confirmation FROM investory.ryczalt_period_activity_confirmation a WHERE a.profile_id = o.profile_id AND a.period_year = ? AND a.period_month = ?), -1),
          COALESCE((SELECT s.status FROM investory.ryczalt_ksef_sync_status s WHERE s.profile_id=o.profile_id AND s.period_year=? AND s.period_month=?), 'NOT_AVAILABLE')
        FROM investory.ryczalt_onboarding o WHERE o.profile_id = ?
        """,
        (rs, row) ->
            new Snapshot(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getBigDecimal(5),
                rs.getString(6),
                rs.getString(7),
                (Boolean) rs.getObject(8),
                rs.getString(9),
                rs.getString(10),
                rs.getString(11),
                rs.getInt(12),
                rs.getInt(13),
                rs.getBoolean(14),
                rs.getString(15),
                rs.getInt(16),
                rs.getString(17),
                rs.getBoolean(18),
                rs.getInt(19),
                rs.getInt(20),
                rs.getString(21)),
        month.getYear(),
        month.getMonthValue(),
        month.plusMonths(1).getYear(),
        month.plusMonths(1).getMonthValue(),
        month.getYear(),
        month.getMonthValue(),
        month.plusMonths(1).getYear(),
        month.plusMonths(1).getMonthValue(),
        month.getYear(),
        month.getMonthValue(),
        month.getYear(),
        month.getMonthValue(),
        month.getYear(),
        month.getMonthValue(),
        month.getYear(),
        month.getMonthValue(),
        month.getYear(),
        month.getMonthValue(),
        month.getYear(),
        month.getMonthValue(),
        month.getYear(),
        month.getMonthValue(),
        profileId);
  }

  private record Snapshot(
      String onboardingState,
      String nip,
      String companyName,
      String taxation,
      java.math.BigDecimal rate,
      String pit,
      String vat,
      Boolean jdg,
      String zusRegime,
      String healthMethod,
      String ksef,
      int invoiceCount,
      int transactionCount,
      boolean periodExists,
      String periodStatus,
      int readyCalculations,
      String calculationStatuses,
      boolean confirmed,
      int confirmedInvoiceCount,
      int confirmedTransactionCount,
      String ksefSync) {
    String calculationStatus(String type) {
      String raw =
          java.util.Arrays.stream(calculationStatuses.split(","))
              .filter(value -> value.startsWith(type + ":"))
              .map(value -> value.substring(type.length() + 1))
              .findFirst()
              .orElse(null);
      return raw == null
          ? "UNAVAILABLE"
          : List.of("CURRENT", "CALCULATED", "FROZEN").contains(raw) ? "COMPLETE" : "INCOMPLETE";
    }
  }
}
