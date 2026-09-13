package com.smartbox.investory.accounting.staging;

import com.smartbox.investory.accounting.VatTreatment;
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
    jdbc.update(
        """
        INSERT INTO investory.accounting_tmp_invoice
          (profile_id,tax_period,source_id,source_type,source_reference,document_kind,document_date,
           reference,counterparty_name,counterparty_tax_identifier,counterparty_country,currency,
           net_amount,vat_amount,gross_amount,vat_deduction_ratio,deductible_vat,vat_treatment,ksef_number)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT (source_id, reference) DO NOTHING
        """,
        profileId,
        taxPeriod,
        sourceId,
        sourceType,
        sourceReference,
        documentKind,
        documentDate,
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
        "SELECT id FROM investory.accounting_tmp_invoice WHERE source_id = ? AND reference = ? ORDER BY id DESC LIMIT 1",
        Long.class,
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
    jdbc.update(
        """
        INSERT INTO investory.accounting_tmp_bank_transaction
          (profile_id,tax_period,source_id,source_type,source_reference,provider,external_account_id,
           external_transaction_id,booking_date,value_date,amount,currency,counterparty_name,
           counterparty_account,remittance_information,source_payload_hash)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT (source_id, external_transaction_id) DO NOTHING
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
        amount,
        currency,
        counterpartyName,
        counterpartyAccount,
        remittanceInformation,
        sourcePayloadHash);
    return jdbc.queryForObject(
        "SELECT id FROM investory.accounting_tmp_bank_transaction WHERE source_id = ? AND external_transaction_id = ?",
        Long.class,
        sourceId,
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
            + " SET reconciliation_status = ?, reconciliation_reason_codes = ?::varchar[], reconciliation_message = ? WHERE id = ?",
        status.name(),
        reasons.toArray(new String[0]),
        message,
        id);
  }

  public void promoted(String table, long id, long canonicalId) {
    jdbc.update(
        "UPDATE investory.accounting_tmp_"
            + table
            + " SET reconciliation_status='PROMOTED', promoted_at=CURRENT_TIMESTAMP, canonical_id=? WHERE id=?",
        canonicalId,
        id);
  }

  public Long promoteInvoice(StagedInvoice row) {
    if (row.vatTreatment() == null || row.vatTreatment().isBlank()) {
      throw new IllegalStateException(
          "Staged invoice requires explicit VAT treatment before promotion: " + row.reference());
    }
    if (row.documentKind().equals("EXPENSE")) {
      jdbc.update(
          """
          INSERT INTO investory.accounting_poc_expense_invoice
            (tax_period,invoice_date,reference,supplier_alias,category,currency,net_amount,vat_amount,gross_amount,vat_deduction_ratio,source_quality,note,source_id,counterparty_tax_identifier,counterparty_country,ksef_number)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (reference) DO NOTHING
          """,
          row.taxPeriod(),
          row.documentDate(),
          row.reference(),
          row.counterpartyName(),
          "OTHER",
          row.currency(),
          row.netAmount(),
          row.vatAmount(),
          row.grossAmount(),
          Objects.requireNonNullElse(row.vatDeductionRatio(), BigDecimal.ONE),
          "STAGED",
          row.message(),
          row.sourceId(),
          row.counterpartyTaxIdentifier(),
          row.counterpartyCountry(),
          row.ksefNumber());
    } else {
      jdbc.update(
          """
          INSERT INTO investory.accounting_poc_invoice
            (tax_period,issue_date,sale_date,reference,customer_alias,invoice_kind,currency,net_amount,vat_amount,gross_amount,expected_receivable,booked_net_pln,ryczalt_rate,note,source_id,counterparty_tax_identifier,counterparty_country,ksef_number)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (reference) DO NOTHING
          """,
          row.taxPeriod(),
          row.documentDate(),
          row.documentDate(),
          row.reference(),
          row.counterpartyName(),
          "PLN".equals(row.currency()) ? "DOMESTIC_SERVICE" : "EU_SERVICE",
          row.currency(),
          row.netAmount(),
          row.vatAmount(),
          row.grossAmount(),
          row.grossAmount(),
          "PLN".equals(row.currency()) ? row.netAmount() : null,
          new BigDecimal("0.12"),
          row.message(),
          row.sourceId(),
          row.counterpartyTaxIdentifier(),
          row.counterpartyCountry(),
          row.ksefNumber());
    }
    String canonicalTable =
        row.documentKind().equals("EXPENSE")
            ? "accounting_poc_expense_invoice"
            : "accounting_poc_invoice";
    Long canonicalId =
        jdbc.queryForObject(
            "SELECT id FROM investory." + canonicalTable + " WHERE reference = ?",
            Long.class,
            row.reference());
    jdbc.update(
        """
        INSERT INTO investory.accounting_vat_transaction
          (tax_period,tax_date,source_document_id,reference,direction,treatment,counterparty_country,
           counterparty_tax_identifier,net_amount,vat_amount,deductible_vat,evidence)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT DO NOTHING
        """,
        row.taxPeriod(),
        row.documentDate() == null ? row.taxPeriod() : row.documentDate(),
        Long.toString(row.sourceId()),
        row.reference(),
        row.documentKind().equals("EXPENSE") ? "PURCHASE" : "SALE",
        VatTreatment.valueOf(row.vatTreatment()).name(),
        row.counterpartyCountry(),
        row.counterpartyTaxIdentifier(),
        row.netAmount(),
        row.vatAmount(),
        row.documentKind().equals("EXPENSE")
            ? Objects.requireNonNullElse(row.deductibleVat(), BigDecimal.ZERO)
            : BigDecimal.ZERO,
        "STAGED_SOURCE:" + row.sourceId());
    return canonicalId;
  }

  public Long promoteBank(StagedBankTransaction row) {
    jdbc.update(
        """
        INSERT INTO investory.accounting_poc_bank_transaction
          (booking_date,related_period,reference,counterparty_alias,currency,amount,transaction_type,scope,note,source_id,source_row_identity,provider,external_account_id,external_transaction_id,source_payload_hash)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
        """,
        row.bookingDate(),
        row.taxPeriod(),
        row.remittanceInformation(),
        row.counterpartyName(),
        row.currency(),
        row.amount(),
        "UNKNOWN",
        "REVIEW_REQUIRED",
        row.remittanceInformation(),
        row.sourceId(),
        bankSourceIdentity(row),
        row.provider(),
        row.externalAccountId(),
        row.externalTransactionId(),
        row.sourcePayloadHash());
    return jdbc.queryForObject(
        "SELECT id FROM investory.accounting_poc_bank_transaction WHERE source_row_identity = ?",
        Long.class,
        bankSourceIdentity(row));
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
