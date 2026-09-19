package com.smartbox.investory.ryczalt.migration;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Controlled, one-way import. Normal Ryczalt runtime reads only ryczalt_* tables. */
@Service
public class RyczaltMigrationService {
  private final JdbcTemplate jdbc;

  public RyczaltMigrationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public RyczaltMigrationReport migrate(long profileId) {
    Objects.requireNonNull(profileId);
    int periods = 0;
    int income = 0;
    int costs = 0;
    int transactions = 0;
    int obligations = 0;
    int sourceReferences = 0;
    Set<YearMonth> seenPeriods = new HashSet<>();

    for (Map<String, Object> row :
        query(
            "SELECT id, tax_period, issue_date, reference, currency, net_amount, vat_amount,"
                + " gross_amount, booked_net_pln, ryczalt_rate FROM"
                + " investory.accounting_poc_invoice WHERE profile_id=?",
            profileId)) {
      long periodId = period(profileId, date(row, "tax_period"));
      periods += seenPeriods.add(YearMonth.from(date(row, "tax_period"))) ? 1 : 0;
      long id = invoice(profileId, periodId, "INCOME", row);
      sourceReferences +=
          sourceReference(
                  profileId, "INVOICE", id, "ACCOUNTING_POC_INVOICE", row.get("id").toString())
              ? 1
              : 0;
      income++;
    }
    for (Map<String, Object> row :
        query(
            "SELECT id, tax_period, invoice_date AS issue_date, reference, currency, net_amount,"
                + " vat_amount, gross_amount, vat_deduction_ratio FROM"
                + " investory.accounting_poc_expense_invoice WHERE profile_id=?",
            profileId)) {
      long periodId = period(profileId, date(row, "tax_period"));
      periods += seenPeriods.add(YearMonth.from(date(row, "tax_period"))) ? 1 : 0;
      long id = invoice(profileId, periodId, "COST", row);
      sourceReferences +=
          sourceReference(
                  profileId,
                  "INVOICE",
                  id,
                  "ACCOUNTING_POC_EXPENSE_INVOICE",
                  row.get("id").toString())
              ? 1
              : 0;
      costs++;
    }
    for (Map<String, Object> row :
        query(
            "SELECT id, COALESCE(related_period, date_trunc('month', booking_date)::date) AS"
                + " tax_period, booking_date, reference, amount, currency, counterparty_alias, note"
                + " FROM investory.accounting_poc_bank_transaction WHERE profile_id=?",
            profileId)) {
      long periodId = period(profileId, date(row, "tax_period"));
      periods += seenPeriods.add(YearMonth.from(date(row, "tax_period"))) ? 1 : 0;
      String reference =
          row.get("reference") == null
              ? "legacy-bank-" + row.get("id")
              : row.get("reference").toString();
      Long id =
          insertReturning(
              """
              INSERT INTO investory.ryczalt_transaction
                  (period_id, profile_id, booking_date, amount, currency, reference, counterparty, description)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?)
              ON CONFLICT (profile_id, reference) DO UPDATE SET reference=EXCLUDED.reference
              RETURNING id
              """,
              periodId,
              profileId,
              date(row, "booking_date"),
              row.get("amount"),
              row.get("currency"),
              reference,
              row.get("counterparty_alias"),
              row.get("note"));
      sourceReferences +=
          sourceReference(
                  profileId,
                  "TRANSACTION",
                  id,
                  "ACCOUNTING_POC_BANK_TRANSACTION",
                  row.get("id").toString())
              ? 1
              : 0;
      transactions++;
    }
    for (Map<String, Object> row :
        query(
            "SELECT id, tax_period, obligation_type, due_date, expected_amount, status FROM"
                + " investory.accounting_poc_obligation WHERE profile_id=?",
            profileId)) {
      long periodId = period(profileId, date(row, "tax_period"));
      periods += seenPeriods.add(YearMonth.from(date(row, "tax_period"))) ? 1 : 0;
      Long id =
          insertReturning(
              """
              INSERT INTO investory.ryczalt_obligation
                  (period_id, profile_id, obligation_type, amount, currency, due_date, status)
              VALUES (?, ?, ?, ?, 'PLN', ?, ?)
              ON CONFLICT (profile_id, period_id, obligation_type) DO UPDATE SET amount=EXCLUDED.amount, due_date=EXCLUDED.due_date, status=EXCLUDED.status
              RETURNING id
              """,
              periodId,
              profileId,
              row.get("obligation_type"),
              row.get("expected_amount"),
              row.get("due_date"),
              normalizeObligationStatus(row.get("status")));
      sourceReferences +=
          sourceReference(
                  profileId,
                  "OBLIGATION",
                  id,
                  "ACCOUNTING_POC_OBLIGATION",
                  row.get("id").toString())
              ? 1
              : 0;
      obligations++;
    }
    return new RyczaltMigrationReport(
        periods, income, costs, transactions, obligations, sourceReferences);
  }

  private long period(long profileId, LocalDate date) {
    YearMonth month = YearMonth.from(date);
    return insertReturning(
        """
        INSERT INTO investory.ryczalt_period(profile_id, period_year, period_month, status)
        VALUES (?, ?, ?, 'OPEN')
        ON CONFLICT (profile_id, period_year, period_month) DO UPDATE SET profile_id=EXCLUDED.profile_id
        RETURNING id
        """,
        profileId,
        month.getYear(),
        month.getMonthValue());
  }

  private long invoice(long profileId, long periodId, String direction, Map<String, Object> row) {
    LocalDate date =
        row.get("issue_date") == null ? date(row, "tax_period") : date(row, "issue_date");
    BigDecimal deductibleVat =
        row.get("vat_deduction_ratio") == null
            ? null
            : ((BigDecimal) row.get("vat_amount"))
                .multiply((BigDecimal) row.get("vat_deduction_ratio"));
    String sourceTable =
        "INCOME".equals(direction) ? "accounting_poc_invoice" : "accounting_poc_expense_invoice";
    String sourceDate = "INCOME".equals(direction) ? "issue_date" : "invoice_date";
    String rate = "INCOME".equals(direction) ? "ryczalt_rate" : "NULL";
    String booked = "INCOME".equals(direction) ? "booked_net_pln" : "NULL";
    return insertReturning(
        """
        INSERT INTO investory.ryczalt_invoice
            (period_id, profile_id, direction, reference, issue_date, accounting_date, net_amount, vat_amount, gross_amount, currency, booked_net_pln, ryczalt_rate, deductible_vat)
        SELECT ?, ?, ?, reference, ?, ?, net_amount, vat_amount, gross_amount, currency,
               %s, %s, ?
          FROM investory.%s
         WHERE id=? AND profile_id=?
        ON CONFLICT (profile_id, direction, reference) DO UPDATE SET period_id=EXCLUDED.period_id
        RETURNING id
        """
            .formatted(booked, rate, sourceTable),
        periodId,
        profileId,
        direction,
        date,
        date,
        deductibleVat,
        row.get("id"),
        profileId);
  }

  private List<Map<String, Object>> query(String sql, long profileId) {
    return jdbc.queryForList(sql, profileId);
  }

  private boolean sourceReference(
      long profileId, String entityType, long entityId, String source, String externalId) {
    return jdbc.update(
            """
            INSERT INTO investory.ryczalt_source_reference(profile_id, entity_type, entity_id, source, external_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (profile_id, entity_type, source, external_id) DO NOTHING
            """,
            profileId,
            entityType,
            entityId,
            source,
            externalId)
        == 1;
  }

  private long insertReturning(String sql, Object... args) {
    Long id = jdbc.queryForObject(sql, Long.class, args);
    return Objects.requireNonNull(id);
  }

  private static LocalDate date(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value instanceof Date sqlDate ? sqlDate.toLocalDate() : (LocalDate) value;
  }

  private static String normalizeObligationStatus(Object value) {
    if (value == null) return "OPEN";
    return switch (value.toString().toUpperCase()) {
      case "PAID", "SETTLED" -> "PAID";
      case "FROZEN", "LOCKED" -> "FROZEN";
      default -> "OPEN";
    };
  }
}
