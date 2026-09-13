package com.smartbox.investory.accounting.staging;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountingStagingRepository {
  private final JdbcTemplate jdbc;

  public boolean profileExists(long profileId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.portfolios WHERE id = ?)",
            Boolean.class,
            profileId));
  }

  public long insertInvoice(
      long profileId,
      LocalDate taxPeriod,
      long sourceId,
      String sourceType,
      String sourceReference,
      String documentKind,
      LocalDate documentDate,
      String reference,
      String counterpartyName,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String currency,
      BigDecimal net,
      BigDecimal vat,
      BigDecimal gross,
      BigDecimal deductionRatio,
      BigDecimal deductibleVat,
      String vatTreatment,
      String ksefNumber) {
    return insertInvoice(
        profileId,
        taxPeriod,
        sourceId,
        sourceType,
        sourceReference,
        documentKind,
        documentDate,
        null,
        reference,
        counterpartyName,
        counterpartyTaxIdentifier,
        counterpartyCountry,
        currency,
        net,
        vat,
        gross,
        deductionRatio,
        deductibleVat,
        vatTreatment,
        ksefNumber);
  }

  public long insertInvoice(
      long profileId,
      LocalDate taxPeriod,
      long sourceId,
      String sourceType,
      String sourceReference,
      String documentKind,
      LocalDate documentDate,
      LocalDate dueDate,
      String reference,
      String counterpartyName,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String currency,
      BigDecimal net,
      BigDecimal vat,
      BigDecimal gross,
      BigDecimal deductionRatio,
      BigDecimal deductibleVat,
      String vatTreatment,
      String ksefNumber) {
    jdbc.update(
        """
        INSERT INTO investory.accounting_tmp_invoice
          (profile_id,tax_period,source_id,source_type,source_reference,document_kind,document_date,due_date,
           reference,counterparty_name,counterparty_tax_identifier,counterparty_country,currency,
           net_amount,vat_amount,gross_amount,vat_deduction_ratio,deductible_vat,vat_treatment,ksef_number)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT (profile_id, source_id, reference) DO NOTHING
        """,
        profileId,
        taxPeriod,
        sourceId,
        sourceType,
        sourceReference,
        documentKind,
        documentDate,
        dueDate,
        reference,
        counterpartyName,
        counterpartyTaxIdentifier,
        counterpartyCountry,
        currency,
        net,
        vat,
        gross,
        deductionRatio,
        deductibleVat,
        vatTreatment,
        ksefNumber);
    return jdbc.queryForObject(
        "SELECT id FROM investory.accounting_tmp_invoice WHERE profile_id = ? AND source_id = ? AND reference = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        profileId,
        sourceId,
        reference);
  }

  public long insertBank(
      long profileId,
      LocalDate taxPeriod,
      long sourceId,
      String sourceType,
      String sourceReference,
      String provider,
      String externalAccountId,
      String externalTransactionId,
      LocalDate bookingDate,
      LocalDate valueDate,
      BigDecimal amount,
      String currency,
      String counterpartyName,
      String counterpartyAccount,
      String remittanceInformation,
      String sourcePayloadHash) {
    return insertBank(
        profileId,
        taxPeriod,
        sourceId,
        sourceType,
        sourceReference,
        provider,
        externalAccountId,
        externalTransactionId,
        bookingDate,
        valueDate,
        null,
        amount,
        currency,
        counterpartyName,
        counterpartyAccount,
        remittanceInformation,
        sourcePayloadHash);
  }

  public long insertBank(
      long profileId,
      LocalDate taxPeriod,
      long sourceId,
      String sourceType,
      String sourceReference,
      String provider,
      String externalAccountId,
      String externalTransactionId,
      LocalDate bookingDate,
      LocalDate valueDate,
      LocalDate relatedPeriod,
      BigDecimal amount,
      String currency,
      String counterpartyName,
      String counterpartyAccount,
      String remittanceInformation,
      String sourcePayloadHash) {
    jdbc.update(
        """
        INSERT INTO investory.accounting_tmp_bank_transaction
          (profile_id,tax_period,source_id,source_type,source_reference,provider,external_account_id,
           external_transaction_id,booking_date,value_date,related_period,amount,currency,counterparty_name,
           counterparty_account,remittance_information,source_payload_hash)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT (profile_id, provider, external_account_id, external_transaction_id) DO NOTHING
        """,
        profileId,
        taxPeriod,
        sourceId,
        sourceType,
        sourceReference,
        provider,
        externalAccountId,
        externalTransactionId,
        bookingDate,
        valueDate,
        relatedPeriod,
        amount,
        currency,
        counterpartyName,
        counterpartyAccount,
        remittanceInformation,
        sourcePayloadHash);
    return jdbc.queryForObject(
        "SELECT id FROM investory.accounting_tmp_bank_transaction WHERE profile_id = ? AND provider = ? AND external_account_id IS NOT DISTINCT FROM ? AND external_transaction_id = ? ORDER BY id LIMIT 1",
        Long.class,
        profileId,
        provider,
        externalAccountId,
        externalTransactionId);
  }

  public List<StagedInvoice> invoices(long profileId, LocalDate taxPeriod) {
    return jdbc.query(
        "SELECT * FROM investory.accounting_tmp_invoice WHERE profile_id = ? AND tax_period = ? ORDER BY id",
        (rs, n) ->
            new StagedInvoice(
                rs.getLong("id"),
                rs.getLong("profile_id"),
                rs.getObject("tax_period", LocalDate.class),
                rs.getLong("source_id"),
                rs.getString("source_reference"),
                rs.getString("document_kind"),
                rs.getObject("document_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getString("reference"),
                rs.getString("counterparty_name"),
                rs.getString("counterparty_tax_identifier"),
                rs.getString("counterparty_country"),
                rs.getString("currency"),
                rs.getBigDecimal("net_amount"),
                rs.getBigDecimal("vat_amount"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("vat_deduction_ratio"),
                rs.getBigDecimal("deductible_vat"),
                rs.getString("vat_treatment"),
                rs.getString("ksef_number"),
                status(rs.getString("reconciliation_status")),
                strings(rs.getArray("reconciliation_reason_codes")),
                rs.getString("reconciliation_message"),
                instant(rs.getTimestamp("promoted_at")),
                rs.getObject("canonical_id", Long.class)),
        profileId,
        taxPeriod);
  }

  public List<StagedBankTransaction> bankTransactions(long profileId, LocalDate taxPeriod) {
    return jdbc.query(
        "SELECT * FROM investory.accounting_tmp_bank_transaction WHERE profile_id = ? AND tax_period = ? ORDER BY id",
        (rs, n) ->
            new StagedBankTransaction(
                rs.getLong("id"),
                rs.getLong("profile_id"),
                rs.getObject("tax_period", LocalDate.class),
                rs.getLong("source_id"),
                rs.getString("source_reference"),
                rs.getString("provider"),
                rs.getString("external_account_id"),
                rs.getString("external_transaction_id"),
                rs.getObject("booking_date", LocalDate.class),
                rs.getObject("value_date", LocalDate.class),
                rs.getObject("related_period", LocalDate.class),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("counterparty_name"),
                rs.getString("counterparty_account"),
                rs.getString("remittance_information"),
                rs.getString("source_payload_hash"),
                status(rs.getString("reconciliation_status")),
                strings(rs.getArray("reconciliation_reason_codes")),
                rs.getString("reconciliation_message"),
                instant(rs.getTimestamp("promoted_at")),
                rs.getObject("canonical_id", Long.class)),
        profileId,
        taxPeriod);
  }

  public void result(
      long profileId,
      String table,
      long id,
      StagingReconciliationStatus status,
      List<String> reasons,
      String message) {
    if (!table.equals("invoice") && !table.equals("bank_transaction"))
      throw new IllegalArgumentException("Unsupported staging table");
    jdbc.update(
        "UPDATE investory.accounting_tmp_"
            + table
            + " SET reconciliation_status = ?, reconciliation_reason_codes = ?::varchar[], reconciliation_message = ? WHERE profile_id = ? AND id = ?",
        status.name(),
        reasons.toArray(new String[0]),
        message,
        profileId,
        id);
  }

  public void result(
      String table,
      long id,
      StagingReconciliationStatus status,
      List<String> reasons,
      String message) {
    result(1L, table, id, status, reasons, message);
  }

  public void promoted(long profileId, String table, long id, long canonicalId) {
    jdbc.update(
        "UPDATE investory.accounting_tmp_"
            + table
            + " SET reconciliation_status='PROMOTED', promoted_at=CURRENT_TIMESTAMP, canonical_id=? WHERE profile_id=? AND id=?",
        canonicalId,
        profileId,
        id);
  }

  public void promoted(String table, long id, long canonicalId) {
    promoted(1L, table, id, canonicalId);
  }

  public Long canonicalInvoiceId(StagedInvoice row) {
    String table =
        row.documentKind().equals("EXPENSE")
            ? "accounting_poc_expense_invoice"
            : "accounting_poc_invoice";
    return jdbc.queryForObject(
        "SELECT id FROM investory." + table + " WHERE profile_id = ? AND reference = ?",
        Long.class,
        row.profileId(),
        row.reference());
  }

  public Long canonicalBankId(StagedBankTransaction row) {
    return jdbc.queryForObject(
        "SELECT id FROM investory.accounting_poc_bank_transaction "
            + "WHERE profile_id = ? AND provider = ? AND external_account_id = ? "
            + "AND external_transaction_id = ?",
        Long.class,
        row.profileId(),
        row.provider(),
        row.externalAccountId(),
        row.externalTransactionId());
  }

  private static StagingReconciliationStatus status(String value) {
    return StagingReconciliationStatus.valueOf(value);
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private static String bankSourceIdentity(StagedBankTransaction row) {
    return String.join(
        ":",
        Objects.requireNonNullElse(row.provider(), ""),
        Objects.requireNonNullElse(row.externalAccountId(), ""),
        Objects.requireNonNullElse(row.externalTransactionId(), ""));
  }

  private static List<String> strings(Array value) {
    if (value == null) return List.of();
    try {
      return Arrays.stream((Object[]) value.getArray()).map(String::valueOf).toList();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot read staging reason codes", e);
    }
  }
}
