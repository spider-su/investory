package com.smartbox.investory.accounting.infrastructure.persistence;

import com.smartbox.investory.accounting.*;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.accounting.api.AccountingUserApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountingPocRepository {
  private final JdbcTemplate jdbcTemplate;

  public AccountingUserApi.AutoApprovalSettings autoApprovalSettings(long profileId) {
    return jdbcTemplate.query(
        "SELECT enabled, max_amount, trusted_categories FROM investory.accounting_auto_approval_policy WHERE profile_id = ?",
        rs ->
            rs.next()
                ? new AccountingUserApi.AutoApprovalSettings(
                    rs.getBoolean("enabled"),
                    rs.getBigDecimal("max_amount"),
                    java.util.Arrays.asList(
                        (String[]) rs.getArray("trusted_categories").getArray()))
                : new AccountingUserApi.AutoApprovalSettings(false, BigDecimal.ZERO, List.of()),
        profileId);
  }

  public void updateAutoApprovalSettings(
      long profileId, AccountingUserApi.AutoApprovalSettings settings) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_auto_approval_policy (profile_id, enabled, max_amount, trusted_categories) VALUES (?, ?, ?, ?) "
            + "ON CONFLICT (profile_id) DO UPDATE SET enabled = EXCLUDED.enabled, max_amount = EXCLUDED.max_amount, "
            + "trusted_categories = EXCLUDED.trusted_categories, updated_at = CURRENT_TIMESTAMP",
        profileId,
        settings.enabled(),
        settings.maxAmount(),
        new SqlArrayValue("text", (Object[]) settings.trustedCategories().toArray(String[]::new)));
  }

  /** True only when this exact KSeF identity has already produced a canonical document. */
  public boolean canonicalDocumentExists(
      long profileId, long sourceId, String ksefNumber, String reference) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM investory.accounting_poc_invoice
               WHERE profile_id = ? AND ksef_number = ?
            ) OR EXISTS (
              SELECT 1 FROM investory.accounting_poc_expense_invoice
               WHERE profile_id = ? AND ksef_number = ?
            )
            """,
            Boolean.class,
            profileId,
            ksefNumber,
            profileId,
            ksefNumber));
  }

  /**
   * Attaches trusted KSeF provenance to one matching legacy row without changing its accounting
   * values. A match must be exact on the stable document fields; invoice reference alone is not
   * sufficient because legacy references were not globally unique across source systems.
   */
  public int enrichLegacyDocumentFromKsef(
      long profileId,
      String direction,
      String reference,
      LocalDate documentDate,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      Long sourceId,
      String ksefNumber,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String note) {
    String table =
        "SALE".equals(direction) ? "accounting_poc_invoice" : "accounting_poc_expense_invoice";
    String datePredicate =
        "SALE".equals(direction) ? "(issue_date = ? OR sale_date = ?)" : "invoice_date = ?";
    Object[] args =
        "SALE".equals(direction)
            ? new Object[] {
              sourceId,
              counterpartyTaxIdentifier,
              counterpartyCountry,
              ksefNumber,
              note,
              profileId,
              reference,
              currency,
              netAmount,
              vatAmount,
              grossAmount,
              documentDate,
              documentDate
            }
            : new Object[] {
              sourceId,
              counterpartyTaxIdentifier,
              counterpartyCountry,
              ksefNumber,
              note,
              profileId,
              reference,
              currency,
              netAmount,
              vatAmount,
              grossAmount,
              documentDate
            };
    int legacyRows =
        jdbcTemplate.update(
            "UPDATE investory."
                + table
                + " SET source_id=?, counterparty_tax_identifier=?, "
                + "counterparty_country=?, ksef_number=?, filing_evidence='KSEF', "
                + "source_quality=COALESCE(source_quality, 'KSEF_SOURCE_DOCUMENT'), "
                + "note=COALESCE(note, ?) WHERE profile_id=? AND reference=? AND currency=? "
                + "AND net_amount=? AND vat_amount=? AND gross_amount=? AND ksef_number IS NULL AND "
                + datePredicate,
            args);
    String canonicalDatePredicate =
        "SALE".equals(direction) ? "(issue_date = ? OR supply_date = ?)" : "issue_date = ?";
    Object[] canonicalArgs =
        "SALE".equals(direction)
            ? new Object[] {
              sourceId,
              counterpartyTaxIdentifier,
              counterpartyCountry,
              ksefNumber,
              note,
              profileId,
              "SALE",
              reference,
              currency,
              netAmount,
              vatAmount,
              grossAmount,
              documentDate,
              documentDate
            }
            : new Object[] {
              sourceId,
              counterpartyTaxIdentifier,
              counterpartyCountry,
              ksefNumber,
              note,
              profileId,
              "PURCHASE",
              reference,
              currency,
              netAmount,
              vatAmount,
              grossAmount,
              documentDate
            };
    int canonicalRows =
        jdbcTemplate.update(
            "UPDATE investory.accounting_document SET source_id=?, counterparty_tax_identifier=?, "
                + "counterparty_country=?, ksef_number=?, filing_evidence='KSEF', "
                + "source_quality=COALESCE(source_quality, 'KSEF_SOURCE_DOCUMENT'), "
                + "note=COALESCE(note, ?) WHERE profile_id=? AND direction=? AND reference=? "
                + "AND currency=? AND net_amount=? AND vat_amount=? AND gross_amount=? "
                + "AND ksef_number IS NULL AND "
                + canonicalDatePredicate,
            canonicalArgs);
    if (canonicalRows > 0) {
      jdbcTemplate.update(
          """
          UPDATE investory.accounting_document d
             SET counterparty_id = (
                   SELECT k.id
                     FROM investory.accounting_known_counterparty k
                    WHERE k.profile_id = d.profile_id
                      AND k.country = UPPER(d.counterparty_country)
                      AND k.tax_identifier = CASE WHEN UPPER(d.counterparty_country) = 'PL'
                                                  THEN REGEXP_REPLACE(regexp_replace(UPPER(d.counterparty_tax_identifier), '[^A-Z0-9]', '', 'g'), '^PL', '')
                                                  ELSE regexp_replace(UPPER(d.counterparty_tax_identifier), '[^A-Z0-9]', '', 'g') END)
           WHERE d.profile_id = ? AND d.ksef_number = ?
          """,
          profileId,
          ksefNumber);
    }
    return legacyRows + canonicalRows;
  }

  public boolean profileExists(long profileId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.portfolios WHERE id = ?)",
            Boolean.class,
            profileId));
  }

  public AccountingKnownCounterparty knownCounterparty(
      long profileId, String taxIdentifier, String country) {
    if (taxIdentifier == null || taxIdentifier.isBlank() || country == null || country.isBlank()) {
      return null;
    }
    String normalizedCountry = country.trim().toUpperCase();
    String normalizedTaxIdentifier =
        normalizeCounterpartyTaxIdentifier(taxIdentifier, normalizedCountry);
    return jdbcTemplate.query(
        """
        SELECT tax_identifier, country, canonical_name
          FROM investory.accounting_known_counterparty
         WHERE profile_id = ? AND country = ? AND tax_identifier = ?
        """,
        rs ->
            rs.next()
                ? new AccountingKnownCounterparty(rs.getString(1), rs.getString(2), rs.getString(3))
                : null,
        profileId,
        normalizedCountry,
        normalizedTaxIdentifier);
  }

  /**
   * Remembers the first reviewed identity; later documents must reuse it instead of overwriting it.
   */
  public void rememberKnownCounterparty(
      long profileId, String taxIdentifier, String country, String canonicalName) {
    if (taxIdentifier == null
        || taxIdentifier.isBlank()
        || country == null
        || country.isBlank()
        || canonicalName == null
        || canonicalName.isBlank()) return;
    jdbcTemplate.update(
        """
        INSERT INTO investory.accounting_known_counterparty
            (profile_id, tax_identifier, country, canonical_name)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (profile_id, country, tax_identifier) DO NOTHING
        """,
        profileId,
        normalizeCounterpartyTaxIdentifier(taxIdentifier, country),
        country.trim().toUpperCase(),
        canonicalName.trim());
  }

  public List<AccountingUserApi.CounterpartyView> counterparties(long profileId) {
    return jdbcTemplate.query(
        """
        SELECT k.id, k.tax_identifier, k.country, k.canonical_name, k.alias, COUNT(d.id) AS document_count
          FROM investory.accounting_known_counterparty k
          LEFT JOIN investory.accounting_document d
            ON d.profile_id = k.profile_id AND d.counterparty_id = k.id
         WHERE k.profile_id = ?
         GROUP BY k.id, k.tax_identifier, k.country, k.canonical_name, k.alias
         ORDER BY COALESCE(NULLIF(k.alias, ''), k.canonical_name), k.id
        """,
        (rs, rowNum) ->
            new AccountingUserApi.CounterpartyView(
                rs.getLong("id"),
                rs.getString("tax_identifier"),
                rs.getString("country"),
                rs.getString("canonical_name"),
                rs.getString("alias"),
                rs.getInt("document_count")),
        profileId);
  }

  public List<CounterpartyDocumentRow> counterpartyDocuments(long profileId, long counterpartyId) {
    return jdbcTemplate.query(
        """
        SELECT d.tax_period, d.id, d.reference, d.direction, d.document_kind,
               d.corrects_document_id, corrected.reference AS corrects_document_reference,
               COALESCE(d.issue_date, d.supply_date, d.tax_period) AS document_date,
               d.gross_amount, d.currency, d.source_id, s.external_reference,
               s.source_type, s.original_filename,
               COALESCE(NULLIF(k.alias, ''), NULLIF(k.canonical_name, ''),
                        NULLIF(d.counterparty_name, '')) AS counterparty_name,
               d.category, d.supply_date, d.counterparty_tax_identifier,
               d.counterparty_country
          FROM investory.accounting_document d
          JOIN investory.accounting_known_counterparty k
            ON k.id = d.counterparty_id AND k.profile_id = d.profile_id
          LEFT JOIN investory.accounting_source_evidence s ON s.id = d.source_id
          LEFT JOIN investory.accounting_document corrected ON corrected.id = d.corrects_document_id
         WHERE d.profile_id = ? AND d.counterparty_id = ?
         ORDER BY COALESCE(d.issue_date, d.supply_date, d.tax_period), d.id
        """,
        (rs, rowNum) ->
            new CounterpartyDocumentRow(
                rs.getObject("tax_period", LocalDate.class),
                new CanonicalDocumentRow(
                    rs.getLong("id"),
                    rs.getString("reference"),
                    rs.getString("direction"),
                    rs.getString("document_kind"),
                    rs.getObject("corrects_document_id", Long.class),
                    rs.getString("corrects_document_reference"),
                    rs.getObject("document_date", LocalDate.class),
                    rs.getBigDecimal("gross_amount"),
                    rs.getString("currency"),
                    rs.getObject("source_id", Long.class),
                    rs.getString("external_reference"),
                    rs.getString("source_type"),
                    rs.getString("original_filename"),
                    rs.getString("counterparty_name"),
                    rs.getString("category"),
                    rs.getObject("supply_date", LocalDate.class),
                    rs.getString("counterparty_tax_identifier"),
                    rs.getString("counterparty_country"))),
        profileId,
        counterpartyId);
  }

  public void updateCounterpartyAlias(long profileId, long counterpartyId, String alias) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE investory.accounting_known_counterparty
               SET alias = NULLIF(TRIM(?), '')
             WHERE profile_id = ? AND id = ?
            """,
            alias,
            profileId,
            counterpartyId);
    if (updated != 1) throw new IllegalArgumentException("Unknown accounting counterparty");
  }

  private String normalizeCounterpartyTaxIdentifier(String value, String country) {
    String normalized = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    if ("PL".equalsIgnoreCase(country) && normalized.startsWith("PL")) {
      return normalized.substring(2);
    }
    return normalized;
  }

  private AccountingFilingEvidence filingEvidence(String value, String ksefNumber) {
    if (value == null || value.isBlank()) return null;
    return new AccountingFilingEvidence(AccountingFilingEvidence.Type.valueOf(value), ksefNumber);
  }

  public PeriodState periodState(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        "SELECT confirmed_at, confirmed_calculation_hash, lifecycle_status FROM investory.accounting_poc_period_state WHERE profile_id = ? AND tax_period = ?",
        rs ->
            rs.next()
                ? new PeriodState(
                    rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                    rs.getString(2),
                    rs.getString(3) == null
                        ? PeriodLifecycleStatus.OPEN
                        : PeriodLifecycleStatus.valueOf(rs.getString(3)))
                : null,
        profileId,
        period);
  }

  public Map<LocalDate, PeriodState> periodStates(long profileId) {
    return jdbcTemplate.query(
        "SELECT tax_period, confirmed_at, confirmed_calculation_hash, lifecycle_status FROM investory.accounting_poc_period_state WHERE profile_id = ?",
        rs -> {
          Map<LocalDate, PeriodState> result = new LinkedHashMap<>();
          while (rs.next()) {
            result.put(
                rs.getObject(1, LocalDate.class),
                new PeriodState(
                    rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant(),
                    rs.getString(3),
                    rs.getString(4) == null
                        ? PeriodLifecycleStatus.OPEN
                        : PeriodLifecycleStatus.valueOf(rs.getString(4))));
          }
          return result;
        },
        profileId);
  }

  public void confirm(long profileId, LocalDate period, String hash, Instant confirmedAt) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_period_state (profile_id, tax_period, confirmed_at, confirmed_calculation_hash) VALUES (?, ?, ?, ?) ON CONFLICT (profile_id, tax_period) DO UPDATE SET confirmed_at = EXCLUDED.confirmed_at, confirmed_calculation_hash = EXCLUDED.confirmed_calculation_hash",
        profileId,
        period,
        java.sql.Timestamp.from(confirmedAt),
        hash);
  }

  public void updateLifecycleStatus(
      long profileId, LocalDate period, PeriodLifecycleStatus status) {
    PeriodLifecycleStatus current =
        jdbcTemplate.query(
            "SELECT lifecycle_status FROM investory.accounting_poc_period_state WHERE profile_id = ? AND tax_period = ?",
            rs ->
                rs.next() && rs.getString(1) != null
                    ? PeriodLifecycleStatus.valueOf(rs.getString(1))
                    : PeriodLifecycleStatus.OPEN,
            profileId,
            period);
    if (current == PeriodLifecycleStatus.LOCKED && status != PeriodLifecycleStatus.LOCKED) {
      throw new IllegalStateException("Locked accounting period cannot be changed");
    }
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_period_state (profile_id, tax_period, lifecycle_status) VALUES (?, ?, ?) ON CONFLICT (profile_id, tax_period) DO UPDATE SET lifecycle_status = EXCLUDED.lifecycle_status",
        profileId,
        period,
        status.name());
  }

  public void reopen(long profileId, LocalDate period, String reason, Instant reopenedAt) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_period_state (profile_id, tax_period, lifecycle_status, reopened_at, reopen_reason) VALUES (?, ?, 'OPEN', ?, ?) ON CONFLICT (profile_id, tax_period) DO UPDATE SET lifecycle_status = 'OPEN', confirmed_at = NULL, confirmed_calculation_hash = NULL, reopened_at = EXCLUDED.reopened_at, reopen_reason = EXCLUDED.reopen_reason",
        profileId,
        period,
        java.sql.Timestamp.from(reopenedAt),
        reason);
  }

  public void saveFilingArtifact(long profileId, AccountingFilingArtifact artifact) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_filing_artifact (profile_id, artifact_type, tax_period, schema_version, payload, payload_hash, calculation_hash, generated_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (profile_id, artifact_type, tax_period, payload_hash) DO NOTHING",
        profileId,
        artifact.type().name(),
        artifact.period(),
        artifact.schemaVersion(),
        artifact.payload(),
        artifact.payloadHash(),
        artifact.calculationHash(),
        java.sql.Timestamp.from(artifact.generatedAt()),
        artifact.status().name());
  }

  public void saveAuthorityConfirmation(long profileId, AuthorityConfirmation confirmation) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_authority_confirmation (profile_id, authority, obligation_or_artifact_type, tax_period, external_reference, confirmation_type, status, received_at, source_document_id, note, amount, calculation_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        profileId,
        confirmation.authority(),
        confirmation.obligationOrArtifactType(),
        confirmation.period(),
        confirmation.externalReference(),
        confirmation.confirmationType().name(),
        confirmation.status().name(),
        java.sql.Timestamp.from(confirmation.receivedAt()),
        confirmation.sourceDocumentId(),
        confirmation.note(),
        confirmation.amount(),
        confirmation.calculationHash());
  }

  public Optional<AccountingFilingArtifact> filingArtifact(
      long profileId, LocalDate period, String type) {
    return jdbcTemplate.query(
        "SELECT artifact_type, tax_period, schema_version, payload, payload_hash, calculation_hash, generated_at, status FROM investory.accounting_filing_artifact WHERE profile_id = ? AND tax_period = ? AND artifact_type = ? ORDER BY generated_at DESC LIMIT 1",
        rs ->
            rs.next()
                ? Optional.of(
                    new AccountingFilingArtifact(
                        AccountingFilingArtifact.Type.valueOf(rs.getString(1)),
                        rs.getDate(2).toLocalDate(),
                        rs.getString(3),
                        rs.getBytes(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getTimestamp(7).toInstant(),
                        AccountingFilingArtifact.Status.valueOf(rs.getString(8))))
                : Optional.empty(),
        profileId,
        period,
        type);
  }

  public Optional<AuthorityConfirmation> authorityConfirmation(
      long profileId, LocalDate period, String confirmationType) {
    return jdbcTemplate.query(
        "SELECT authority, obligation_or_artifact_type, tax_period, external_reference, confirmation_type, status, received_at, source_document_id, note, amount, calculation_hash FROM investory.accounting_authority_confirmation WHERE profile_id = ? AND tax_period = ? AND confirmation_type = ? ORDER BY received_at DESC LIMIT 1",
        rs ->
            rs.next()
                ? Optional.of(
                    new AuthorityConfirmation(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getDate(3).toLocalDate(),
                        rs.getString(4),
                        AuthorityConfirmation.ConfirmationType.valueOf(rs.getString(5)),
                        AuthorityConfirmation.ConfirmationStatus.valueOf(rs.getString(6)),
                        rs.getTimestamp(7).toInstant(),
                        rs.getObject(8, Long.class),
                        rs.getString(9),
                        rs.getBigDecimal(10),
                        rs.getString(11)))
                : Optional.empty(),
        profileId,
        period,
        confirmationType);
  }

  public boolean hasFilingArtifact(LocalDate period, String artifactType) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_filing_artifact WHERE tax_period = ? AND artifact_type = ? AND status IN ('VALID', 'SUBMITTED'))",
            Boolean.class,
            period,
            artifactType));
  }

  public boolean hasFilingArtifact(long profileId, LocalDate period, String artifactType) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_filing_artifact WHERE profile_id = ? AND tax_period = ? AND artifact_type = ? AND status IN ('VALID', 'SUBMITTED'))",
            Boolean.class,
            profileId,
            period,
            artifactType));
  }

  public boolean hasFilingArtifact(LocalDate period, String artifactType, String calculationHash) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_filing_artifact WHERE tax_period = ? AND artifact_type = ? AND calculation_hash = ? AND status IN ('VALID', 'SUBMITTED'))",
            Boolean.class,
            period,
            artifactType,
            calculationHash));
  }

  public boolean hasFilingArtifact(
      long profileId, LocalDate period, String artifactType, String calculationHash) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_filing_artifact WHERE profile_id = ? AND tax_period = ? AND artifact_type = ? AND calculation_hash = ? AND status IN ('VALID', 'SUBMITTED'))",
            Boolean.class,
            profileId,
            period,
            artifactType,
            calculationHash));
  }

  public boolean hasAcceptedConfirmation(LocalDate period, String confirmationType) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_authority_confirmation WHERE tax_period = ? AND confirmation_type = ? AND status IN ('ACCEPTED', 'POSTED'))",
            Boolean.class,
            period,
            confirmationType));
  }

  public boolean hasAcceptedConfirmation(
      LocalDate period, String confirmationType, String calculationHash) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_authority_confirmation WHERE tax_period = ? AND confirmation_type = ? AND calculation_hash = ? AND status IN ('ACCEPTED', 'POSTED'))",
            Boolean.class,
            period,
            confirmationType,
            calculationHash));
  }

  public boolean hasAcceptedConfirmation(
      long profileId, LocalDate period, String confirmationType, String calculationHash) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_authority_confirmation WHERE profile_id = ? AND tax_period = ? AND confirmation_type = ? AND calculation_hash = ? AND status IN ('ACCEPTED', 'POSTED'))",
            Boolean.class,
            profileId,
            period,
            confirmationType,
            calculationHash));
  }

  public boolean hasAcceptedConfirmationForAmount(
      LocalDate period, String obligationType, String confirmationType, BigDecimal expectedAmount) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_authority_confirmation WHERE tax_period = ? AND obligation_or_artifact_type = ? AND confirmation_type = ? AND status IN ('ACCEPTED', 'POSTED') AND amount IS NOT NULL AND amount = ?)",
            Boolean.class,
            period,
            obligationType,
            confirmationType,
            expectedAmount));
  }

  public boolean hasAcceptedConfirmationForAmount(
      long profileId,
      LocalDate period,
      String obligationType,
      String confirmationType,
      BigDecimal expectedAmount) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_authority_confirmation WHERE profile_id = ? AND tax_period = ? AND obligation_or_artifact_type = ? AND confirmation_type = ? AND status IN ('ACCEPTED', 'POSTED') AND amount IS NOT NULL AND amount = ?)",
            Boolean.class,
            profileId,
            period,
            obligationType,
            confirmationType,
            expectedAmount));
  }

  public record PeriodState(
      Instant confirmedAt,
      String confirmedCalculationHash,
      PeriodLifecycleStatus lifecycleStatus) {}

  public AccountingProfile accountingProfile(long profileId) {
    return jdbcTemplate.queryForObject(
        "SELECT COALESCE(legacy.has_uop, false) AS has_uop, COALESCE(legacy.auto_approve_known_counterparties, TRUE) AS auto_approve_known_counterparties, p.taxpayer_nip AS nip, p.taxpayer_full_name AS full_name, p.taxpayer_tax_office_code AS tax_office_code, p.taxpayer_email AS email, legacy.vat_payment_account, legacy.ryczalt_payment_account, p.zus_payment_account AS zus_payment_account, p.taxpayer_first_name AS first_name, p.taxpayer_surname AS surname, p.taxpayer_date_of_birth AS date_of_birth, p.tax_micro_account AS tax_micro_account FROM investory.portfolios p LEFT JOIN investory.accounting_poc_profile legacy ON legacy.profile_id = p.id WHERE p.id = ?",
        (rs, rowNum) ->
            new AccountingProfile(
                rs.getBoolean("has_uop"),
                rs.getString("nip"),
                rs.getString("full_name"),
                rs.getString("tax_office_code"),
                rs.getString("email"),
                rs.getString("vat_payment_account"),
                rs.getString("ryczalt_payment_account"),
                rs.getString("zus_payment_account"),
                rs.getString("first_name"),
                rs.getString("surname"),
                rs.getObject("date_of_birth", LocalDate.class),
                rs.getString("tax_micro_account"),
                rs.getBoolean("auto_approve_known_counterparties")),
        profileId);
  }

  public void updateHasUop(long profileId, boolean hasUop) {
    int updated =
        jdbcTemplate.update(
            "UPDATE investory.accounting_poc_profile SET has_uop = ? WHERE profile_id = ?",
            hasUop,
            profileId);
    if (updated != 1) {
      throw new IllegalStateException("Accounting POC profile is missing");
    }
  }

  public void updateAutoApproveKnownCounterparties(long profileId, boolean enabled) {
    int updated =
        jdbcTemplate.update(
            "UPDATE investory.accounting_poc_profile SET auto_approve_known_counterparties = ? WHERE profile_id = ?",
            enabled,
            profileId);
    if (updated != 1) throw new IllegalStateException("Accounting profile is missing");
  }

  public List<LocalDate> availablePeriods(long profileId) {
    return jdbcTemplate.queryForList(
        """
        SELECT DISTINCT tax_period
          FROM (
            SELECT tax_period FROM investory.accounting_reference_month WHERE profile_id = ?
            UNION
            SELECT tax_period FROM investory.accounting_poc_invoice WHERE profile_id = ?
            UNION SELECT tax_period FROM investory.accounting_poc_expense_invoice WHERE profile_id = ?
            UNION SELECT tax_period FROM investory.accounting_poc_obligation WHERE profile_id = ?
            UNION SELECT tax_period FROM investory.accounting_poc_period_state WHERE profile_id = ?
          ) periods
         ORDER BY tax_period
        """,
        LocalDate.class,
        profileId,
        profileId,
        profileId,
        profileId,
        profileId);
  }

  public List<CanonicalDocumentRow> canonicalDocumentsForPeriod(long profileId, LocalDate period) {
    try {
      return jdbcTemplate.query(
          """
          SELECT d.id, d.reference, d.direction, d.document_kind, d.corrects_document_id,
                 corrected.reference AS corrects_document_reference,
                 COALESCE(d.issue_date, d.supply_date, d.tax_period) AS document_date,
                 d.gross_amount, d.currency, d.source_id, s.external_reference,
                 s.source_type, s.original_filename,
                 COALESCE(NULLIF(k.alias, ''), NULLIF(k.canonical_name, ''), NULLIF(d.counterparty_name, '')) AS counterparty_name,
                 d.category, d.supply_date, d.counterparty_tax_identifier, d.counterparty_country
            FROM investory.accounting_document d
            LEFT JOIN investory.accounting_known_counterparty k
              ON k.id = d.counterparty_id AND k.profile_id = d.profile_id
            LEFT JOIN investory.accounting_source_evidence s ON s.id = d.source_id
            LEFT JOIN investory.accounting_document corrected ON corrected.id = d.corrects_document_id
           WHERE d.profile_id = ? AND d.tax_period = ?
           ORDER BY COALESCE(d.issue_date, d.supply_date, d.tax_period), d.id
          """,
          (rs, rowNum) ->
              new CanonicalDocumentRow(
                  rs.getLong("id"),
                  rs.getString("reference"),
                  rs.getString("direction"),
                  rs.getString("document_kind"),
                  rs.getObject("corrects_document_id", Long.class),
                  rs.getString("corrects_document_reference"),
                  rs.getObject("document_date", LocalDate.class),
                  rs.getBigDecimal("gross_amount"),
                  rs.getString("currency"),
                  rs.getObject("source_id", Long.class),
                  rs.getString("external_reference"),
                  rs.getString("source_type"),
                  rs.getString("original_filename"),
                  rs.getString("counterparty_name"),
                  rs.getString("category"),
                  rs.getObject("supply_date", LocalDate.class),
                  rs.getString("counterparty_tax_identifier"),
                  rs.getString("counterparty_country")),
          profileId,
          period);
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public Long canonicalInvoiceId(long profileId, String direction, String reference) {
    List<Long> ids =
        jdbcTemplate.queryForList(
            """
            SELECT id
              FROM investory.accounting_document
             WHERE profile_id = ? AND direction = ? AND document_kind = 'INVOICE' AND reference = ?
            """,
            Long.class,
            profileId,
            direction,
            reference);
    return ids.size() == 1 ? ids.getFirst() : null;
  }

  public void linkCanonicalCorrection(long profileId, String reference, long correctsDocumentId) {
    jdbcTemplate.update(
        """
        UPDATE investory.accounting_document
           SET corrects_document_id = ?
         WHERE profile_id = ? AND document_kind = 'CREDIT_NOTE' AND reference = ?
        """,
        correctsDocumentId,
        profileId,
        reference);
  }

  public record CanonicalDocumentRow(
      long id,
      String reference,
      String direction,
      String documentKind,
      Long correctsDocumentId,
      String correctsDocumentReference,
      LocalDate documentDate,
      BigDecimal grossAmount,
      String currency,
      Long sourceId,
      String sourceReference,
      String sourceType,
      String sourceName,
      String counterparty,
      String category,
      LocalDate saleDate,
      String counterpartyTaxIdentifier,
      String counterpartyCountry) {}

  public record CounterpartyDocumentRow(LocalDate taxPeriod, CanonicalDocumentRow document) {}

  /**
   * Filing projection from canonical documents. Empty means this period still uses legacy fixtures.
   */
  public List<AccountingFilingInput.FilingDocument> canonicalFilingDocumentsForPeriod(
      long profileId, LocalDate period) {
    try {
      return jdbcTemplate.query(
          """
          SELECT id, reference, direction, issue_date, supply_date, counterparty_tax_identifier,
                 counterparty_country, counterparty_name, net_amount, vat_amount, currency, vat_deduction_ratio,
                 filing_evidence, ksef_number
            FROM investory.accounting_document
           WHERE profile_id = ? AND tax_period = ?
           ORDER BY id
          """,
          (rs, rowNum) -> {
            long documentId = rs.getLong("id");
            List<AccountingFilingInput.FilingVatBucket> buckets =
                jdbcTemplate.query(
                    """
                    SELECT treatment, vat_rate, net_amount, vat_amount, deductible_vat
                      FROM investory.accounting_document_vat_bucket
                     WHERE document_id = ?
                     ORDER BY id
                    """,
                    (bucket, bucketRow) ->
                        new AccountingFilingInput.FilingVatBucket(
                            VatTreatment.valueOf(bucket.getString("treatment")),
                            bucket.getBigDecimal("vat_rate"),
                            bucket.getBigDecimal("net_amount"),
                            bucket.getBigDecimal("vat_amount"),
                            bucket.getBigDecimal("deductible_vat")),
                    documentId);
            AccountingFilingInput.FilingVatBucket first = buckets.getFirst();
            return new AccountingFilingInput.FilingDocument(
                rs.getString("reference"),
                rs.getObject("issue_date", LocalDate.class),
                "SALE".equals(rs.getString("direction"))
                    ? rs.getObject("supply_date", LocalDate.class)
                    : null,
                "PURCHASE".equals(rs.getString("direction"))
                    ? rs.getObject("supply_date", LocalDate.class)
                    : null,
                rs.getString("counterparty_tax_identifier"),
                rs.getString("counterparty_name"),
                rs.getBigDecimal("net_amount"),
                rs.getBigDecimal("vat_amount"),
                buckets.stream()
                    .map(AccountingFilingInput.FilingVatBucket::deductibleVat)
                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                filingEvidence(rs.getString("filing_evidence"), rs.getString("ksef_number")),
                first.treatment(),
                rs.getString("counterparty_country"),
                first.vatRate(),
                rs.getBigDecimal("vat_deduction_ratio"),
                buckets);
          },
          profileId,
          period);
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public Optional<ReferenceMonth> referenceMonth(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        "SELECT revenue, expenses, output_vat, deductible_input_vat, vat_payable, ryczalt, zus, document_count, bank_count, filing_status FROM investory.accounting_reference_month WHERE profile_id=? AND tax_period=?",
        rs ->
            rs.next()
                ? Optional.of(
                    new ReferenceMonth(
                        rs.getBigDecimal(1),
                        rs.getBigDecimal(2),
                        rs.getBigDecimal(3),
                        rs.getBigDecimal(4),
                        rs.getBigDecimal(5),
                        rs.getBigDecimal(6),
                        rs.getBigDecimal(7),
                        rs.getInt(8),
                        rs.getInt(9),
                        rs.getString(10)))
                : Optional.empty(),
        profileId,
        period);
  }

  public record ReferenceMonth(
      BigDecimal revenue,
      BigDecimal expenses,
      BigDecimal outputVat,
      BigDecimal deductibleInputVat,
      BigDecimal vatPayable,
      BigDecimal ryczalt,
      BigDecimal zus,
      int documentCount,
      int bankCount,
      String filingStatus) {}

  public BigDecimal yearToDateRevenue(long profileId, LocalDate period) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COALESCE(SUM(COALESCE(booked_net_pln, net_amount)), 0)
         FROM investory.accounting_poc_invoice
         WHERE profile_id = ? AND tax_period >= DATE_TRUNC('year', ?::date)::date AND tax_period <= ?
           AND invoice_kind IN ('SALES_INVOICE', 'DOMESTIC_SERVICE', 'EU_SERVICE', 'CREDIT_NOTE')
        """,
        BigDecimal.class,
        profileId,
        period,
        period);
  }

  public BigDecimal oldestAvailableYearRevenue(long profileId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT SUM(COALESCE(booked_net_pln, net_amount))
          FROM investory.accounting_poc_invoice
         WHERE profile_id = ?
           AND tax_period >= (
                 SELECT DATE_TRUNC('year', MIN(tax_period))::date
                   FROM investory.accounting_poc_invoice
                  WHERE profile_id = ?
                    AND invoice_kind IN ('SALES_INVOICE', 'DOMESTIC_SERVICE', 'EU_SERVICE', 'CREDIT_NOTE'))
           AND tax_period < (
                 SELECT DATE_TRUNC('year', MIN(tax_period))::date + INTERVAL '1 year'
                   FROM investory.accounting_poc_invoice
                  WHERE profile_id = ?
                    AND invoice_kind IN ('SALES_INVOICE', 'DOMESTIC_SERVICE', 'EU_SERVICE', 'CREDIT_NOTE'))
           AND invoice_kind IN ('SALES_INVOICE', 'DOMESTIC_SERVICE', 'EU_SERVICE', 'CREDIT_NOTE')
        """,
        BigDecimal.class,
        profileId,
        profileId,
        profileId);
  }

  public List<InvoiceRow> invoicesForPeriod(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT i.id, i.tax_period, i.issue_date, i.sale_date, i.fx_rate_date, i.reference,
               COALESCE(NULLIF(linked.alias, ''), linked.canonical_name,
                        NULLIF(k.alias, ''), k.canonical_name, i.customer_alias) AS customer_alias, i.invoice_kind,
               i.currency, i.net_amount, i.vat_amount, i.gross_amount, i.correction_net_amount,
               i.correction_vat_amount, i.correction_gross_amount, i.expected_receivable,
               i.booked_net_pln, i.ryczalt_rate, i.note, i.counterparty_tax_identifier, i.counterparty_country, i.ksef_number, i.filing_evidence
          FROM investory.accounting_poc_invoice i
          LEFT JOIN investory.accounting_document d
            ON d.profile_id = i.profile_id
           AND d.source_id = i.source_id
           AND d.reference = i.reference
          LEFT JOIN investory.accounting_known_counterparty linked
            ON linked.profile_id = d.profile_id
           AND linked.id = d.counterparty_id
          LEFT JOIN investory.accounting_known_counterparty k
            ON k.profile_id = i.profile_id
           AND k.country = UPPER(i.counterparty_country)
           AND k.tax_identifier = CASE WHEN UPPER(i.counterparty_country) = 'PL'
                                       THEN REGEXP_REPLACE(UPPER(REGEXP_REPLACE(i.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')), '^PL', '')
                                       ELSE UPPER(REGEXP_REPLACE(i.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')) END
         WHERE i.profile_id = ? AND i.tax_period = ?
         ORDER BY i.id
        """,
        (rs, rowNum) ->
            new InvoiceRow(
                rs.getLong("id"),
                rs.getObject("tax_period", LocalDate.class),
                rs.getObject("issue_date", LocalDate.class),
                rs.getObject("sale_date", LocalDate.class),
                rs.getObject("fx_rate_date", LocalDate.class),
                rs.getString("reference"),
                rs.getString("customer_alias"),
                rs.getString("invoice_kind"),
                rs.getString("currency"),
                rs.getBigDecimal("net_amount"),
                rs.getBigDecimal("vat_amount"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("correction_net_amount"),
                rs.getBigDecimal("correction_vat_amount"),
                rs.getBigDecimal("correction_gross_amount"),
                rs.getBigDecimal("expected_receivable"),
                rs.getBigDecimal("booked_net_pln"),
                rs.getBigDecimal("ryczalt_rate"),
                rs.getString("note"),
                rs.getString("counterparty_tax_identifier"),
                rs.getString("counterparty_country"),
                rs.getString("ksef_number"),
                filingEvidence(rs.getString("filing_evidence"), rs.getString("ksef_number"))),
        profileId,
        period);
  }

  public boolean insertSalesInvoice(
      long profileId,
      LocalDate taxPeriod,
      LocalDate issueDate,
      LocalDate saleDate,
      String reference,
      String customerAlias,
      String invoiceKind,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal bookedNetPln,
      BigDecimal ryczaltRate,
      String note,
      Long sourceId,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String ksefNumber,
      AccountingFilingEvidence filingEvidence) {
    return insertSalesInvoice(
        profileId,
        taxPeriod,
        issueDate,
        saleDate,
        null,
        reference,
        customerAlias,
        invoiceKind,
        currency,
        netAmount,
        vatAmount,
        grossAmount,
        bookedNetPln,
        ryczaltRate,
        note,
        sourceId,
        counterpartyTaxIdentifier,
        counterpartyCountry,
        ksefNumber,
        filingEvidence);
  }

  public boolean insertSalesInvoice(
      long profileId,
      LocalDate taxPeriod,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate dueDate,
      String reference,
      String customerAlias,
      String invoiceKind,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal bookedNetPln,
      BigDecimal ryczaltRate,
      String note,
      Long sourceId,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String ksefNumber,
      AccountingFilingEvidence filingEvidence) {
    return jdbcTemplate.update(
            """
        INSERT INTO investory.accounting_poc_invoice
             (profile_id, tax_period, issue_date, sale_date, due_date, reference, customer_alias, invoice_kind, currency,
             net_amount, vat_amount, gross_amount, expected_receivable, booked_net_pln,
             ryczalt_rate, note, source_id, counterparty_tax_identifier, counterparty_country,
             ksef_number, filing_evidence)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (profile_id, reference) DO NOTHING
        """,
            profileId,
            taxPeriod,
            issueDate,
            saleDate,
            dueDate,
            reference,
            customerAlias,
            invoiceKind,
            currency,
            netAmount,
            vatAmount,
            grossAmount,
            grossAmount,
            bookedNetPln,
            ryczaltRate,
            note,
            sourceId,
            counterpartyTaxIdentifier,
            counterpartyCountry,
            ksefNumber,
            filingEvidence == null ? null : filingEvidence.type().name())
        == 1;
  }

  public List<ExpenseRow> expensesForPeriod(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT i.id, i.tax_period, i.invoice_date, i.reference,
               COALESCE(NULLIF(k.alias, ''), k.canonical_name, i.supplier_alias) AS supplier_alias, i.category, i.currency,
               net_amount, vat_amount, gross_amount, vat_deduction_ratio,
               ROUND(vat_amount * vat_deduction_ratio, 2) AS deductible_vat,
               source_quality, note, counterparty_tax_identifier, counterparty_country, ksef_number, filing_evidence
          FROM investory.accounting_poc_expense_invoice i
          LEFT JOIN investory.accounting_known_counterparty k
            ON k.profile_id = i.profile_id
           AND k.country = UPPER(i.counterparty_country)
           AND k.tax_identifier = CASE WHEN UPPER(i.counterparty_country) = 'PL'
                                       THEN REGEXP_REPLACE(UPPER(REGEXP_REPLACE(i.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')), '^PL', '')
                                       ELSE UPPER(REGEXP_REPLACE(i.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')) END
         WHERE i.profile_id = ? AND i.tax_period = ?
         ORDER BY invoice_date NULLS LAST, id
        """,
        (rs, rowNum) ->
            new ExpenseRow(
                rs.getLong("id"),
                rs.getObject("tax_period", LocalDate.class),
                rs.getObject("invoice_date", LocalDate.class),
                rs.getString("reference"),
                rs.getString("supplier_alias"),
                rs.getString("category"),
                rs.getString("currency"),
                rs.getBigDecimal("net_amount"),
                rs.getBigDecimal("vat_amount"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("vat_deduction_ratio"),
                rs.getBigDecimal("deductible_vat"),
                rs.getString("source_quality"),
                rs.getString("note"),
                rs.getString("counterparty_tax_identifier"),
                rs.getString("counterparty_country"),
                rs.getString("ksef_number"),
                filingEvidence(rs.getString("filing_evidence"), rs.getString("ksef_number"))),
        profileId,
        period);
  }

  public boolean insertExpense(
      long profileId,
      LocalDate taxPeriod,
      LocalDate invoiceDate,
      String reference,
      String supplierAlias,
      String category,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal vatDeductionRatio,
      String sourceQuality,
      String note,
      Long sourceId,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String ksefNumber,
      AccountingFilingEvidence filingEvidence) {
    return insertExpense(
        profileId,
        taxPeriod,
        invoiceDate,
        null,
        reference,
        supplierAlias,
        category,
        currency,
        netAmount,
        vatAmount,
        grossAmount,
        vatDeductionRatio,
        sourceQuality,
        note,
        sourceId,
        counterpartyTaxIdentifier,
        counterpartyCountry,
        ksefNumber,
        filingEvidence);
  }

  public boolean insertExpense(
      long profileId,
      LocalDate taxPeriod,
      LocalDate invoiceDate,
      LocalDate dueDate,
      String reference,
      String supplierAlias,
      String category,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal vatDeductionRatio,
      String sourceQuality,
      String note,
      Long sourceId,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String ksefNumber,
      AccountingFilingEvidence filingEvidence) {
    return jdbcTemplate.update(
            """
        INSERT INTO investory.accounting_poc_expense_invoice
            (profile_id, tax_period, invoice_date, due_date, reference, supplier_alias, category, currency,
             net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality, note, source_id,
             counterparty_tax_identifier, counterparty_country, ksef_number, filing_evidence)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (profile_id, reference) DO NOTHING
        """,
            profileId,
            taxPeriod,
            invoiceDate,
            dueDate,
            reference,
            supplierAlias,
            category,
            currency,
            netAmount,
            vatAmount,
            grossAmount,
            vatDeductionRatio,
            sourceQuality,
            note,
            sourceId,
            counterpartyTaxIdentifier,
            counterpartyCountry,
            ksefNumber,
            filingEvidence == null ? null : filingEvidence.type().name())
        == 1;
  }

  public List<BankRow> bankTransactionsForPeriod(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT id, booking_date, related_period, reference, counterparty_alias, currency, amount,
               transaction_type, scope, note
          FROM investory.accounting_poc_bank_transaction
         WHERE profile_id = ? AND (related_period = ?
            OR (related_period IS NULL AND booking_date >= ? AND booking_date < ?)
            OR (related_period IS NULL AND transaction_type = 'CUSTOMER_RECEIPT'
                AND booking_date >= ? AND booking_date <= CURRENT_DATE)
         )
         ORDER BY booking_date, id
        """,
        (rs, rowNum) ->
            new BankRow(
                rs.getLong("id"),
                rs.getObject("booking_date", LocalDate.class),
                rs.getObject("related_period", LocalDate.class),
                rs.getString("reference"),
                rs.getString("counterparty_alias"),
                rs.getString("currency"),
                rs.getBigDecimal("amount"),
                rs.getString("transaction_type"),
                rs.getString("scope"),
                rs.getString("note")),
        profileId,
        period,
        period,
        period.plusMonths(1),
        period);
  }

  public List<LocalDate> zusPaymentPeriodsUpTo(long profileId, LocalDate period) {
    return jdbcTemplate.queryForList(
        "SELECT DISTINCT related_period FROM investory.accounting_poc_bank_transaction WHERE profile_id = ? AND transaction_type = 'ZUS_PAYMENT' AND booking_date <= ? AND related_period IS NOT NULL ORDER BY related_period",
        LocalDate.class,
        profileId,
        period.withDayOfMonth(period.lengthOfMonth()));
  }

  /** Projects persisted ZUS payments against the obligation for their own contribution period. */
  public PaidContributionProjection paidContributionsUpTo(
      long profileId, LocalDate period, java.util.Map<LocalDate, ZusAmounts> obligationsByPeriod) {
    List<PaidContribution> contributions = new java.util.ArrayList<>();
    List<AccountingIssue> issues = new java.util.ArrayList<>();
    jdbcTemplate.query(
        """
            SELECT id, booking_date, related_period, amount, reference
             FROM investory.accounting_poc_bank_transaction
             WHERE profile_id = ? AND transaction_type = 'ZUS_PAYMENT'
               AND booking_date <= ? AND related_period IS NOT NULL
             ORDER BY id
            """,
        (rs, rowNum) -> {
          BigDecimal paid = rs.getBigDecimal("amount").abs().setScale(2);
          LocalDate paymentDate = rs.getObject("booking_date", LocalDate.class);
          LocalDate contributionPeriod = rs.getObject("related_period", LocalDate.class);
          long id = rs.getLong("id");
          String reference = rs.getString("reference");
          ZusAmounts obligation = obligationsByPeriod.get(contributionPeriod);
          if (obligation == null) {
            if (period.equals(contributionPeriod)) {
              issues.add(
                  reviewIssue(reference, "No calculated ZUS obligation for contribution period."));
            }
            return null;
          }
          BigDecimal total = obligation.social().add(obligation.health()).setScale(2);
          if (paid.setScale(0, java.math.RoundingMode.HALF_UP)
                      .compareTo(total.setScale(0, java.math.RoundingMode.HALF_UP))
                  == 0
              && total.signum() > 0) {
            if (obligation.social().signum() > 0)
              contributions.add(
                  new PaidContribution(
                      "SOCIAL",
                      contributionPeriod,
                      paymentDate,
                      obligation.social(),
                      obligation.deductibleSocial(),
                      id));
            if (obligation.health().signum() > 0)
              contributions.add(
                  new PaidContribution(
                      "HEALTH",
                      contributionPeriod,
                      paymentDate,
                      obligation.health(),
                      obligation.health(),
                      id));
          } else if (obligation.social().signum() == 0
              && paid.setScale(0, java.math.RoundingMode.HALF_UP)
                      .compareTo(obligation.health().setScale(0, java.math.RoundingMode.HALF_UP))
                  == 0
              && obligation.health().signum() > 0) {
            contributions.add(
                new PaidContribution(
                    "HEALTH",
                    contributionPeriod,
                    paymentDate,
                    obligation.health(),
                    obligation.health(),
                    id));
          } else {
            issues.add(
                reviewIssue(
                    reference, "ZUS payment does not match its contribution-period obligation."));
          }
          return null;
        },
        profileId,
        period.withDayOfMonth(period.lengthOfMonth()));
    return new PaidContributionProjection(List.copyOf(contributions), List.copyOf(issues));
  }

  private AccountingIssue reviewIssue(String reference, String message) {
    return new AccountingIssue(
        "PAID_CONTRIBUTION_REVIEW_REQUIRED", "REVIEW_REQUIRED", reference, message);
  }

  public record ZusAmounts(BigDecimal social, BigDecimal deductibleSocial, BigDecimal health) {}

  public record PaidContributionProjection(
      List<PaidContribution> contributions, List<AccountingIssue> issues) {}

  public boolean insertBankTransaction(
      java.time.LocalDate bookingDate,
      java.time.LocalDate relatedPeriod,
      String reference,
      String counterparty,
      String currency,
      java.math.BigDecimal amount,
      String transactionType,
      String scope,
      String note,
      long sourceId,
      String sourceRowIdentity) {
    return insertBankTransaction(
        bookingDate,
        relatedPeriod,
        reference,
        counterparty,
        currency,
        amount,
        transactionType,
        scope,
        note,
        sourceId,
        "CSV",
        "LEGACY_SOURCE",
        sourceRowIdentity,
        null);
  }

  public boolean insertBankTransaction(
      LocalDate bookingDate,
      LocalDate relatedPeriod,
      String reference,
      String counterparty,
      String currency,
      java.math.BigDecimal amount,
      String transactionType,
      String scope,
      String note,
      long sourceId,
      String provider,
      String externalAccountId,
      String externalTransactionId,
      String sourcePayloadHash) {
    return insertBankTransaction(
        bookingDate,
        relatedPeriod,
        reference,
        counterparty,
        currency,
        amount,
        transactionType,
        scope,
        note,
        sourceId,
        null,
        provider,
        externalAccountId,
        externalTransactionId,
        sourcePayloadHash);
  }

  public boolean insertBankTransaction(
      LocalDate bookingDate,
      LocalDate relatedPeriod,
      String reference,
      String counterparty,
      String currency,
      java.math.BigDecimal amount,
      String transactionType,
      String scope,
      String note,
      long sourceId,
      Long profileId,
      String provider,
      String externalAccountId,
      String externalTransactionId,
      String sourcePayloadHash) {
    String sourceRowIdentity =
        String.join(
            ":",
            Objects.requireNonNullElse(provider, ""),
            Objects.requireNonNullElse(externalAccountId, ""),
            Objects.requireNonNullElse(externalTransactionId, ""));
    return jdbcTemplate.update(
            """
            INSERT INTO investory.accounting_poc_bank_transaction
                (profile_id, booking_date, related_period, reference, counterparty_alias, currency, amount,
                 transaction_type, scope, note, source_id, source_row_identity,
                 provider, external_account_id, external_transaction_id, source_payload_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
            profileId,
            bookingDate,
            relatedPeriod,
            reference,
            counterparty,
            currency,
            amount,
            transactionType,
            scope,
            note,
            sourceId,
            sourceRowIdentity,
            provider,
            externalAccountId,
            externalTransactionId,
            sourcePayloadHash)
        == 1;
  }

  public List<ObligationRow> obligationsForPeriod(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note
          FROM investory.accounting_poc_obligation
         WHERE profile_id = ? AND tax_period = ?
        UNION ALL
        SELECT tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note
          FROM investory.accounting_reference_obligation reference
         WHERE reference.profile_id = ? AND reference.tax_period = ?
           AND NOT EXISTS (
                 SELECT 1
                   FROM investory.accounting_poc_obligation operational
                  WHERE operational.profile_id = reference.profile_id
                    AND operational.tax_period = reference.tax_period)
        ORDER BY obligation_type
        """,
        (rs, rowNum) ->
            new ObligationRow(
                rs.getObject("tax_period", LocalDate.class),
                rs.getString("obligation_type"),
                rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("expected_amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getObject("payment_date", LocalDate.class),
                rs.getString("status"),
                rs.getString("note")),
        profileId,
        period,
        profileId,
        period);
  }

  public List<TaxInputRow> taxInputsForPeriod(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT input_type, amount, note
          FROM investory.accounting_poc_tax_input
         WHERE profile_id = ? AND tax_period = ?
         ORDER BY input_type
        """,
        (rs, rowNum) ->
            new TaxInputRow(
                rs.getString("input_type"), rs.getBigDecimal("amount"), rs.getString("note")),
        profileId,
        period);
  }

  public List<TaxInputRow> taxInputsUpTo(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT tax_period, input_type, amount, note
          FROM investory.accounting_poc_tax_input
         WHERE profile_id = ?
           AND tax_period >= DATE_TRUNC('year', ?::date)::date
           AND tax_period <= ?
         ORDER BY tax_period, input_type
        """,
        (rs, rowNum) ->
            new TaxInputRow(
                rs.getString("input_type"), rs.getBigDecimal("amount"), rs.getString("note")),
        profileId,
        period,
        period);
  }

  public List<AccountingVatAdjustment> vatAdjustmentsForPeriod(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT tax_period, adjustment_type, amount, source_system, source_reference, affects
          FROM investory.accounting_vat_adjustment
         WHERE profile_id = ? AND tax_period = ?
         ORDER BY id
        """,
        (rs, rowNum) ->
            new AccountingVatAdjustment(
                rs.getObject("tax_period", LocalDate.class),
                rs.getString("adjustment_type"),
                rs.getBigDecimal("amount"),
                rs.getString("source_system"),
                rs.getString("source_reference"),
                rs.getString("affects")),
        profileId,
        period);
  }

  public List<EmploymentInsurancePeriod> employmentPeriods(long profileId) {
    try {
      return jdbcTemplate.query(
          "SELECT date_from, date_to, qualifies_as_primary_social_insurance FROM investory.employment_period WHERE profile_id = ? AND employment_type = 'UOP' ORDER BY date_from, id",
          (rs, rowNum) ->
              new EmploymentInsurancePeriod(
                  rs.getObject(1, LocalDate.class),
                  rs.getObject(2, LocalDate.class),
                  rs.getBoolean(3)),
          profileId);
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public List<BusinessActivityPeriod> businessActivityPeriods(long profileId) {
    try {
      return jdbcTemplate.query(
          "SELECT date_from, date_to FROM investory.employment_period WHERE profile_id = ? AND employment_type = 'JDG' ORDER BY date_from, id",
          (rs, rowNum) ->
              new BusinessActivityPeriod(
                  rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class)),
          profileId);
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public List<AccountingTaxProfilePeriod> taxProfilePeriods(long profileId) {
    try {
      return jdbcTemplate.query(
          "SELECT valid_from, valid_to, jdg_active, ryczalt_rate, vat_registered, vat_eu_registered, zus_regime, voluntary_sickness FROM investory.accounting_tax_profile_period WHERE profile_id = ? ORDER BY valid_from",
          (rs, rowNum) ->
              new AccountingTaxProfilePeriod(
                  rs.getObject("valid_from", LocalDate.class),
                  rs.getObject("valid_to", LocalDate.class),
                  rs.getBoolean("jdg_active"),
                  rs.getBigDecimal("ryczalt_rate"),
                  rs.getBoolean("vat_registered"),
                  rs.getBoolean("vat_eu_registered"),
                  rs.getString("zus_regime"),
                  rs.getBoolean("voluntary_sickness")),
          profileId);
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public List<AccountingVatTransaction> vatTransactionsForPeriod(long profileId, LocalDate period) {
    try {
      return jdbcTemplate.query(
          "SELECT tax_date, source_document_id, reference, direction, treatment, counterparty_country, counterparty_tax_identifier, identifier_type, vat_eu_number, vies_verified_at, vies_status, net_amount, vat_amount, deductible_vat, evidence, vat_rate FROM investory.accounting_vat_transaction WHERE profile_id = ? AND tax_period = ? ORDER BY id",
          (rs, rowNum) ->
              new AccountingVatTransaction(
                  rs.getObject("tax_date", LocalDate.class),
                  rs.getString("source_document_id"),
                  rs.getString("reference"),
                  AccountingVatTransaction.Direction.valueOf(rs.getString("direction")),
                  VatTreatment.valueOf(rs.getString("treatment")),
                  rs.getString("counterparty_country"),
                  rs.getString("counterparty_tax_identifier"),
                  rs.getString("identifier_type"),
                  rs.getString("vat_eu_number"),
                  rs.getObject("vies_verified_at", LocalDate.class),
                  rs.getString("vies_status"),
                  rs.getBigDecimal("net_amount"),
                  rs.getBigDecimal("vat_amount"),
                  rs.getBigDecimal("deductible_vat"),
                  rs.getString("evidence"),
                  rs.getBigDecimal("vat_rate")),
          profileId,
          period);
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public void upsertCanonicalDocument(
      long profileId,
      String direction,
      String documentKind,
      LocalDate taxPeriod,
      LocalDate issueDate,
      LocalDate supplyDate,
      LocalDate dueDate,
      String reference,
      String counterpartyName,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      LocalDate fxRateDate,
      BigDecimal bookedNetPln,
      BigDecimal ryczaltRate,
      String category,
      BigDecimal vatDeductionRatio,
      String sourceQuality,
      Long sourceId,
      String ksefNumber,
      AccountingFilingEvidence filingEvidence,
      String note,
      VatTreatment vatTreatment,
      BigDecimal vatRate,
      BigDecimal deductibleVat) {
    jdbcTemplate.update(
        """
        INSERT INTO investory.accounting_document
            (profile_id, direction, document_kind, tax_period, issue_date, supply_date, due_date,
             reference, counterparty_id, counterparty_name, counterparty_tax_identifier, counterparty_country, currency,
             net_amount, vat_amount, gross_amount, fx_rate_date, booked_net_pln, ryczalt_rate,
             category, vat_deduction_ratio, source_quality, source_id, ksef_number, filing_evidence, note)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?,
                (SELECT id FROM investory.accounting_known_counterparty
                  WHERE profile_id = ?
                    AND country = UPPER(?)
                    AND tax_identifier = regexp_replace(UPPER(?), '[^A-Z0-9]', '', 'g')),
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (profile_id, direction, reference) DO NOTHING
        """,
        profileId,
        direction,
        documentKind,
        taxPeriod,
        issueDate,
        supplyDate,
        dueDate,
        reference,
        profileId,
        counterpartyCountry,
        counterpartyTaxIdentifier,
        counterpartyName,
        counterpartyTaxIdentifier,
        counterpartyCountry,
        currency,
        netAmount,
        vatAmount,
        grossAmount,
        fxRateDate,
        bookedNetPln,
        ryczaltRate,
        category,
        vatDeductionRatio,
        sourceQuality,
        sourceId,
        ksefNumber,
        filingEvidence == null ? null : filingEvidence.type().name(),
        note);
    jdbcTemplate.update(
        """
        INSERT INTO investory.accounting_document_vat_bucket
            (document_id, treatment, vat_rate, net_amount, vat_amount, deductible_vat)
        SELECT id, ?, ?, ?, ?, ?
          FROM investory.accounting_document
         WHERE profile_id = ? AND direction = ? AND reference = ?
        ON CONFLICT (document_id, treatment, vat_rate)
            DO UPDATE SET net_amount = EXCLUDED.net_amount,
                          vat_amount = EXCLUDED.vat_amount,
                          deductible_vat = EXCLUDED.deductible_vat
        """,
        vatTreatment.name(),
        vatRate,
        netAmount,
        vatAmount,
        deductibleVat,
        profileId,
        direction,
        reference);
  }

  public void upsertCanonicalVatBucket(
      long profileId,
      AccountingVatTransaction.Direction direction,
      String reference,
      VatTreatment treatment,
      BigDecimal vatRate,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal deductibleVat) {
    jdbcTemplate.update(
        """
        INSERT INTO investory.accounting_document_vat_bucket
            (document_id, treatment, vat_rate, net_amount, vat_amount, deductible_vat)
        SELECT id, ?, ?, ?, ?, ?
          FROM investory.accounting_document
         WHERE profile_id = ? AND direction = ? AND reference = ?
        ON CONFLICT (document_id, treatment, vat_rate)
            DO UPDATE SET net_amount = EXCLUDED.net_amount,
                          vat_amount = EXCLUDED.vat_amount,
                          deductible_vat = EXCLUDED.deductible_vat
        """,
        treatment.name(),
        vatRate,
        netAmount,
        vatAmount,
        deductibleVat,
        profileId,
        direction.name(),
        reference);
  }

  public void insertVatTransaction(
      long profileId,
      LocalDate taxPeriod,
      LocalDate taxDate,
      String sourceDocumentId,
      String reference,
      AccountingVatTransaction.Direction direction,
      VatTreatment treatment,
      String counterpartyCountry,
      String counterpartyTaxIdentifier,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal deductibleVat,
      String evidence,
      BigDecimal vatRate) {
    jdbcTemplate.update(
        """
        INSERT INTO investory.accounting_vat_transaction
          (profile_id,tax_period,tax_date,source_document_id,reference,direction,treatment,counterparty_country,
           counterparty_tax_identifier,net_amount,vat_amount,deductible_vat,evidence,vat_rate)
        SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?
         WHERE NOT EXISTS (
           SELECT 1
             FROM investory.accounting_vat_transaction
            WHERE profile_id = ? AND tax_period = ?
              AND source_document_id = ?
              AND reference = ?
              AND direction = ?
              AND treatment = ?
         )
        """,
        profileId,
        taxPeriod,
        taxDate,
        sourceDocumentId,
        reference,
        direction.name(),
        treatment.name(),
        counterpartyCountry,
        counterpartyTaxIdentifier,
        netAmount,
        vatAmount,
        deductibleVat,
        evidence,
        vatRate,
        profileId,
        taxPeriod,
        sourceDocumentId,
        reference,
        direction.name(),
        treatment.name());
  }

  public List<AccountingIssue> sourceIssuesForPeriod(long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT external_reference, processing_error
          FROM investory.accounting_source_evidence
         WHERE profile_id = ? AND processing_status IN ('REVIEW_REQUIRED', 'FAILED')
           AND (document_date >= ? AND document_date < (? + INTERVAL '1 month'))
         ORDER BY id
        """,
        (rs, rowNum) ->
            new AccountingIssue(
                "SOURCE_REVIEW_REQUIRED",
                "REVIEW_REQUIRED",
                rs.getString("external_reference"),
                rs.getString("processing_error") == null
                    ? "Source document requires review."
                    : rs.getString("processing_error")),
        profileId,
        period,
        period);
  }
}
