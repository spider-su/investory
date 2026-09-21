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
      String source = "ACCOUNTING_POC_INVOICE";
      String externalId = row.get("id").toString();
      Long id = sourceReferenceId(profileId, "INVOICE", source, externalId);
      if (id == null) {
        id = invoice(profileId, periodId, "INCOME", row);
        sourceReferences += sourceReference(profileId, "INVOICE", id, source, externalId) ? 1 : 0;
      }
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
      String source = "ACCOUNTING_POC_EXPENSE_INVOICE";
      String externalId = row.get("id").toString();
      Long id = sourceReferenceId(profileId, "INVOICE", source, externalId);
      if (id == null) {
        id = invoice(profileId, periodId, "COST", row);
        sourceReferences += sourceReference(profileId, "INVOICE", id, source, externalId) ? 1 : 0;
      }
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
      java.util.List<Map<String, Object>> existing =
          jdbc.queryForList(
              "SELECT id FROM investory.ryczalt_transaction WHERE profile_id=? AND reference=?",
              profileId,
              reference);
      Long id;
      if (!existing.isEmpty()) {
        // Preserve historical collapse-by-reference: distinct legacy rows sharing one reference map
        // to a single migrated transaction, keeping certified counts stable.
        id = ((Number) existing.getFirst().get("id")).longValue();
      } else {
        id =
            insertReturning(
                """
                INSERT INTO investory.ryczalt_transaction
                    (period_id, profile_id, booking_date, amount, currency, reference, counterparty, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
      }
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
    sourceReferences += migrateCalculationSnapshots(profileId);
    return new RyczaltMigrationReport(
        periods, income, costs, transactions, obligations, sourceReferences);
  }

  /**
   * Copies persisted historical results. These values are evidence, not fresh calculator output.
   * The legacy obligation table is intentionally not used for this mapping.
   */
  private int migrateCalculationSnapshots(long profileId) {
    int sourceReferences = 0;
    for (Map<String, Object> row :
        query(
            "SELECT tax_period, calculation_hash, calculated_at,"
                + " payload->'ryczalt' AS ryczalt, payload->'vat' AS vat, payload->'zus' AS zus"
                + " FROM investory.accounting_calculation_snapshot WHERE profile_id=?"
                + " ORDER BY tax_period",
            profileId)) {
      LocalDate taxPeriod = date(row, "tax_period");
      long periodId = period(profileId, taxPeriod);
      String fingerprint = row.get("calculation_hash").toString();
      Object calculatedAt = row.get("calculated_at");
      String status = periodStatus(profileId, periodId);
      for (Map.Entry<String, String> section :
          Map.of("ryczalt", "RYCZALT", "vat", "VAT", "zus", "ZUS").entrySet()) {
        String resultJson = jsonValue(row.get(section.getKey()));
        if (resultJson == null) continue;
        long calculationId =
            calculation(
                profileId,
                periodId,
                section.getValue(),
                resultJson,
                fingerprint,
                status,
                calculatedAt);
        sourceReferences +=
            sourceReference(
                    profileId,
                    "CALCULATION",
                    calculationId,
                    "ACCOUNTING_CALCULATION_SNAPSHOT",
                    taxPeriod + "|" + section.getValue())
                ? 1
                : 0;
        long obligationId =
            obligationFromSnapshot(
                profileId,
                periodId,
                section.getValue(),
                resultJson,
                calculationId,
                status,
                calculatedAt);
        sourceReferences +=
            sourceReference(
                    profileId,
                    "OBLIGATION",
                    obligationId,
                    "ACCOUNTING_CALCULATION_SNAPSHOT",
                    taxPeriod + "|" + section.getValue())
                ? 1
                : 0;
      }
    }
    return sourceReferences;
  }

  private long calculation(
      long profileId,
      long periodId,
      String type,
      String resultJson,
      String fingerprint,
      String status,
      Object calculatedAt) {
    Map<String, Object> existing =
        jdbc.query(
            "SELECT id, input_fingerprint FROM investory.ryczalt_calculation"
                + " WHERE profile_id=? AND period_id=? AND calculation_type=?",
            rs ->
                rs.next()
                    ? Map.of(
                        "id",
                        rs.getLong("id"),
                        "input_fingerprint",
                        rs.getString("input_fingerprint"))
                    : null,
            profileId,
            periodId,
            type);
    if (existing != null && !fingerprint.equals(existing.get("input_fingerprint"))) {
      throw new IllegalStateException(
          "Accounting snapshot conflicts with native calculation profile="
              + profileId
              + " periodId="
              + periodId
              + " type="
              + type);
    }
    return insertReturning(
        """
        INSERT INTO investory.ryczalt_calculation
            (period_id, profile_id, calculation_type, status, result_json, input_fingerprint,
             rule_version, calculator_version, calculated_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?, 'LEGACY_ACCOUNTING_SNAPSHOT_V1', 'LEGACY_ACCOUNTING', ?)
        ON CONFLICT (profile_id, period_id, calculation_type) WHERE is_current DO UPDATE SET
            status=EXCLUDED.status, result_json=EXCLUDED.result_json,
            input_fingerprint=EXCLUDED.input_fingerprint,
            rule_version=EXCLUDED.rule_version, calculator_version=EXCLUDED.calculator_version,
            calculated_at=EXCLUDED.calculated_at
        RETURNING id
        """,
        periodId,
        profileId,
        type,
        status,
        resultJson,
        fingerprint,
        calculatedAt);
  }

  private long obligationFromSnapshot(
      long profileId,
      long periodId,
      String type,
      String resultJson,
      long calculationId,
      String status,
      Object calculatedAt) {
    String obligationStatus = "FROZEN".equals(status) ? "FROZEN" : "OPEN";
    BigDecimal amount =
        jdbc.queryForObject(
            "SELECT COALESCE((?::jsonb ->> CASE ? WHEN 'RYCZALT' THEN 'calculatedTax'"
                + " WHEN 'VAT' THEN 'calculatedVat' ELSE 'totalZus' END)::numeric, 0)",
            BigDecimal.class,
            resultJson,
            type);
    Map<String, Object> existing =
        jdbc.query(
            "SELECT id, amount, status FROM investory.ryczalt_obligation"
                + " WHERE profile_id=? AND period_id=? AND obligation_type=?",
            rs ->
                rs.next()
                    ? Map.of(
                        "id",
                        rs.getLong("id"),
                        "amount",
                        rs.getBigDecimal("amount"),
                        "status",
                        rs.getString("status"))
                    : null,
            profileId,
            periodId,
            type);
    if (existing != null
        && (amount.compareTo((BigDecimal) existing.get("amount")) != 0
            || !obligationStatus.equals(existing.get("status")))) {
      throw new IllegalStateException(
          "Accounting snapshot conflicts with native obligation profile="
              + profileId
              + " periodId="
              + periodId
              + " type="
              + type);
    }
    return insertReturning(
        """
        INSERT INTO investory.ryczalt_obligation
            (period_id, profile_id, obligation_type, amount, currency, due_date, status, calculation_id)
        VALUES (?, ?, ?, ?, 'PLN', NULL, ?, ?)
        ON CONFLICT (profile_id, period_id, obligation_type) DO UPDATE SET
            amount=EXCLUDED.amount, status=EXCLUDED.status, calculation_id=EXCLUDED.calculation_id
        RETURNING id
        """,
        periodId,
        profileId,
        type,
        amount,
        obligationStatus,
        calculationId);
  }

  private String periodStatus(long profileId, long periodId) {
    return jdbc.queryForObject(
        "SELECT CASE WHEN status='FROZEN' THEN 'FROZEN' ELSE 'CURRENT' END"
            + " FROM investory.ryczalt_period WHERE profile_id=? AND id=?",
        String.class,
        profileId,
        periodId);
  }

  private static String jsonValue(Object value) {
    if (value == null) return null;
    String json = value.toString();
    return "null".equals(json) ? null : json;
  }

  private long period(long profileId, LocalDate date) {
    YearMonth month = YearMonth.from(date);
    return insertReturning(
        """
        INSERT INTO investory.ryczalt_period(profile_id, period_year, period_month, status)
        VALUES (
            ?, ?, ?,
            CASE WHEN EXISTS (
                SELECT 1
                  FROM investory.accounting_poc_period_state
                 WHERE profile_id=? AND tax_period=make_date(?, ?, 1)
                   AND lifecycle_status IN ('LOCKED', 'FROZEN')
            ) THEN 'FROZEN' ELSE 'OPEN' END
        )
        ON CONFLICT (profile_id, period_year, period_month) DO UPDATE SET
            status = CASE
                WHEN EXCLUDED.status = 'FROZEN' THEN 'FROZEN'
                ELSE investory.ryczalt_period.status
            END
        RETURNING id
        """,
        profileId,
        month.getYear(),
        month.getMonthValue(),
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

  private Long sourceReferenceId(
      long profileId, String entityType, String source, String externalId) {
    return jdbc
        .query(
            """
        SELECT entity_id
          FROM investory.ryczalt_source_reference
         WHERE profile_id=? AND entity_type=? AND source=? AND external_id=?
        """,
            (result, rowNum) -> result.getLong("entity_id"),
            profileId,
            entityType,
            source,
            externalId)
        .stream()
        .findFirst()
        .orElse(null);
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
