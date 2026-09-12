package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountingPocRepository {
  private final JdbcTemplate jdbcTemplate;

  private AccountingFilingEvidence filingEvidence(String value, String ksefNumber) {
    if (value == null || value.isBlank()) return null;
    return new AccountingFilingEvidence(AccountingFilingEvidence.Type.valueOf(value), ksefNumber);
  }

  public PeriodState periodState(LocalDate period) {
    return jdbcTemplate.query(
        "SELECT confirmed_at, confirmed_calculation_hash, lifecycle_status FROM investory.accounting_poc_period_state WHERE tax_period = ?",
        rs ->
            rs.next()
                ? new PeriodState(
                    rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                    rs.getString(2),
                    rs.getString(3) == null
                        ? PeriodLifecycleStatus.OPEN
                        : PeriodLifecycleStatus.valueOf(rs.getString(3)))
                : null,
        period);
  }

  public void confirm(LocalDate period, String hash, Instant confirmedAt) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_period_state (tax_period, confirmed_at, confirmed_calculation_hash) VALUES (?, ?, ?) ON CONFLICT (tax_period) DO UPDATE SET confirmed_at = EXCLUDED.confirmed_at, confirmed_calculation_hash = EXCLUDED.confirmed_calculation_hash",
        period,
        java.sql.Timestamp.from(confirmedAt),
        hash);
  }

  public void updateLifecycleStatus(LocalDate period, PeriodLifecycleStatus status) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_period_state (tax_period, lifecycle_status) VALUES (?, ?) ON CONFLICT (tax_period) DO UPDATE SET lifecycle_status = EXCLUDED.lifecycle_status",
        period,
        status.name());
  }

  public void reopen(LocalDate period, String reason, Instant reopenedAt) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_period_state (tax_period, lifecycle_status, reopened_at, reopen_reason) VALUES (?, 'OPEN', ?, ?) ON CONFLICT (tax_period) DO UPDATE SET lifecycle_status = 'OPEN', confirmed_at = NULL, confirmed_calculation_hash = NULL, reopened_at = EXCLUDED.reopened_at, reopen_reason = EXCLUDED.reopen_reason",
        period,
        java.sql.Timestamp.from(reopenedAt),
        reason);
  }

  public void saveFilingArtifact(AccountingFilingArtifact artifact) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_filing_artifact (artifact_type, tax_period, schema_version, payload, payload_hash, generated_at, status) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (artifact_type, tax_period, payload_hash) DO NOTHING",
        artifact.type().name(),
        artifact.period(),
        artifact.schemaVersion(),
        artifact.payload(),
        artifact.payloadHash(),
        java.sql.Timestamp.from(artifact.generatedAt()),
        artifact.status().name());
  }

  public void saveAuthorityConfirmation(AuthorityConfirmation confirmation) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_authority_confirmation (authority, obligation_or_artifact_type, tax_period, external_reference, confirmation_type, status, received_at, source_document_id, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        confirmation.authority(),
        confirmation.obligationOrArtifactType(),
        confirmation.period(),
        confirmation.externalReference(),
        confirmation.confirmationType().name(),
        confirmation.status().name(),
        java.sql.Timestamp.from(confirmation.receivedAt()),
        confirmation.sourceDocumentId(),
        confirmation.note());
  }

  public boolean hasFilingArtifact(LocalDate period, String artifactType) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_filing_artifact WHERE tax_period = ? AND artifact_type = ? AND status IN ('VALID', 'SUBMITTED'))",
            Boolean.class,
            period,
            artifactType));
  }

  public boolean hasAcceptedConfirmation(LocalDate period, String confirmationType) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM investory.accounting_authority_confirmation WHERE tax_period = ? AND confirmation_type = ? AND status IN ('ACCEPTED', 'POSTED'))",
            Boolean.class,
            period,
            confirmationType));
  }

  public record PeriodState(
      Instant confirmedAt,
      String confirmedCalculationHash,
      PeriodLifecycleStatus lifecycleStatus) {}

  public AccountingProfile accountingProfile() {
    return jdbcTemplate.queryForObject(
        "SELECT has_uop, nip, full_name, tax_office_code, email, vat_payment_account, ryczalt_payment_account, zus_payment_account, first_name, surname, date_of_birth FROM investory.accounting_poc_profile WHERE id = 1",
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
                rs.getObject("date_of_birth", LocalDate.class)));
  }

  public void updateHasUop(boolean hasUop) {
    int updated =
        jdbcTemplate.update(
            "UPDATE investory.accounting_poc_profile SET has_uop = ? WHERE id = 1", hasUop);
    if (updated != 1) {
      throw new IllegalStateException("Accounting POC profile is missing");
    }
  }

  public List<LocalDate> availablePeriods() {
    return jdbcTemplate.queryForList(
        """
        SELECT period
          FROM (
                SELECT DISTINCT tax_period AS period
                  FROM investory.accounting_poc_invoice
                 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2027-01-01'
                UNION
                SELECT DISTINCT tax_period AS period
                  FROM investory.accounting_poc_obligation
                 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2027-01-01'
                UNION
                SELECT DISTINCT tax_period AS period
                  FROM investory.accounting_poc_expense_invoice
                 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2027-01-01'
               ) months
         ORDER BY period
        """,
        LocalDate.class);
  }

  public BigDecimal yearToDateRevenue(LocalDate period) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COALESCE(SUM(COALESCE(booked_net_pln, net_amount)), 0)
          FROM investory.accounting_poc_invoice
         WHERE tax_period >= DATE '2026-01-01' AND tax_period < ?
           AND invoice_kind IN ('SALES_INVOICE', 'DOMESTIC_SERVICE', 'EU_SERVICE')
        """,
        BigDecimal.class,
        period);
  }

  public List<InvoiceRow> invoicesForPeriod(LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT id, tax_period, issue_date, sale_date, fx_rate_date, reference, customer_alias, invoice_kind,
               currency, net_amount, vat_amount, gross_amount, correction_net_amount,
               correction_vat_amount, correction_gross_amount, expected_receivable,
               booked_net_pln, ryczalt_rate, note, counterparty_tax_identifier, counterparty_country, ksef_number, filing_evidence
          FROM investory.accounting_poc_invoice
         WHERE tax_period = ?
         ORDER BY id
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
        period);
  }

  public boolean insertSalesInvoice(
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
      String note) {
    return insertSalesInvoice(
        taxPeriod,
        issueDate,
        saleDate,
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
        null);
  }

  public boolean insertSalesInvoice(
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
      Long sourceId) {
    return insertSalesInvoice(
        taxPeriod,
        issueDate,
        saleDate,
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
        null,
        null,
        null,
        null);
  }

  public boolean insertSalesInvoice(
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
    return jdbcTemplate.update(
            """
        INSERT INTO investory.accounting_poc_invoice
            (tax_period, issue_date, sale_date, reference, customer_alias, invoice_kind, currency,
             net_amount, vat_amount, gross_amount, expected_receivable, booked_net_pln,
             ryczalt_rate, note, source_id, counterparty_tax_identifier, counterparty_country,
             ksef_number, filing_evidence)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (reference) DO NOTHING
        """,
            taxPeriod,
            issueDate,
            saleDate,
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

  public List<ExpenseRow> expensesForPeriod(LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT id, tax_period, invoice_date, reference, supplier_alias, category, currency,
               net_amount, vat_amount, gross_amount, vat_deduction_ratio,
               ROUND(vat_amount * vat_deduction_ratio, 2) AS deductible_vat,
               source_quality, note, counterparty_tax_identifier, counterparty_country, ksef_number, filing_evidence
          FROM investory.accounting_poc_expense_invoice
         WHERE tax_period = ?
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
        period);
  }

  public boolean insertExpense(
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
      String note) {
    return insertExpense(
        taxPeriod,
        invoiceDate,
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
        null);
  }

  public boolean insertExpense(
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
      Long sourceId) {
    return insertExpense(
        taxPeriod,
        invoiceDate,
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
        null,
        null,
        null,
        null);
  }

  public boolean insertExpense(
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
    return jdbcTemplate.update(
            """
        INSERT INTO investory.accounting_poc_expense_invoice
            (tax_period, invoice_date, reference, supplier_alias, category, currency,
             net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality, note, source_id,
             counterparty_tax_identifier, counterparty_country, ksef_number, filing_evidence)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (reference) DO NOTHING
        """,
            taxPeriod,
            invoiceDate,
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

  public List<BankRow> bankTransactionsForPeriod(LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT id, booking_date, related_period, reference, counterparty_alias, currency, amount,
               transaction_type, scope, note
          FROM investory.accounting_poc_bank_transaction
         WHERE related_period = ?
            OR (booking_date >= ? AND booking_date < ?)
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
        period,
        period,
        period.plusMonths(1));
  }

  /**
   * Projects only exact, persisted ZUS payments; obligations are never inferred from payment rows.
   */
  public List<PaidContribution> paidContributionsUpTo(
      LocalDate period, BigDecimal socialObligation, BigDecimal healthObligation) {
    BigDecimal social = socialObligation == null ? BigDecimal.ZERO : socialObligation;
    BigDecimal health = healthObligation == null ? BigDecimal.ZERO : healthObligation;
    BigDecimal total = social.add(health).setScale(2);
    List<List<PaidContribution>> rows =
        jdbcTemplate.query(
            """
            SELECT id, booking_date, related_period, amount, reference
              FROM investory.accounting_poc_bank_transaction
             WHERE transaction_type = 'ZUS_PAYMENT'
               AND booking_date <= ?
             ORDER BY id
            """,
            (rs, rowNum) -> {
              BigDecimal paid = rs.getBigDecimal("amount").abs().setScale(2);
              LocalDate paymentDate = rs.getObject("booking_date", LocalDate.class);
              LocalDate contributionPeriod = rs.getObject("related_period", LocalDate.class);
              long id = rs.getLong("id");
              String reference = rs.getString("reference");
              if (paid.compareTo(total) == 0 && total.signum() > 0) {
                var result = new java.util.ArrayList<PaidContribution>();
                if (social.signum() > 0)
                  result.add(
                      new PaidContribution(
                          "SOCIAL", contributionPeriod, paymentDate, social, social, id));
                if (health.signum() > 0)
                  result.add(
                      new PaidContribution(
                          "HEALTH", contributionPeriod, paymentDate, health, health, id));
                return result;
              }
              if (social.signum() == 0 && paid.compareTo(health) == 0 && health.signum() > 0)
                return List.of(
                    new PaidContribution(
                        "HEALTH", contributionPeriod, paymentDate, health, health, id));
              return List.of();
            },
            period.withDayOfMonth(period.lengthOfMonth()));
    return rows.stream().flatMap(List::stream).toList();
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
      String sourceRowIdentity) {
    return jdbcTemplate.update(
            """
            INSERT INTO investory.accounting_poc_bank_transaction
                (booking_date, related_period, reference, counterparty_alias, currency, amount,
                 transaction_type, scope, note, source_id, source_row_identity)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
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
            sourceRowIdentity)
        == 1;
  }

  public List<ObligationRow> obligationsForPeriod(LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note
          FROM investory.accounting_poc_obligation
         WHERE tax_period = ?
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
        period);
  }

  public List<TaxInputRow> taxInputsForPeriod(LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT input_type, amount, note
          FROM investory.accounting_poc_tax_input
         WHERE tax_period = ?
         ORDER BY input_type
        """,
        (rs, rowNum) ->
            new TaxInputRow(
                rs.getString("input_type"), rs.getBigDecimal("amount"), rs.getString("note")),
        period);
  }

  public List<EmploymentInsurancePeriod> employmentPeriods() {
    try {
      return jdbcTemplate.query(
          "SELECT date_from, date_to FROM investory.employment_period WHERE profile_id = 1 AND employment_type = 'UOP' ORDER BY date_from, id",
          (rs, rowNum) ->
              new EmploymentInsurancePeriod(
                  rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class), true));
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public List<BusinessActivityPeriod> businessActivityPeriods() {
    try {
      return jdbcTemplate.query(
          "SELECT date_from, date_to FROM investory.employment_period WHERE profile_id = 1 AND employment_type = 'JDG' ORDER BY date_from, id",
          (rs, rowNum) ->
              new BusinessActivityPeriod(
                  rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class)));
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public List<AccountingTaxProfilePeriod> taxProfilePeriods() {
    try {
      return jdbcTemplate.query(
          "SELECT valid_from, valid_to, jdg_active, ryczalt_rate, vat_registered, vat_eu_registered, zus_regime, voluntary_sickness FROM investory.accounting_tax_profile_period WHERE profile_id = 1 ORDER BY valid_from",
          (rs, rowNum) ->
              new AccountingTaxProfilePeriod(
                  rs.getObject("valid_from", LocalDate.class),
                  rs.getObject("valid_to", LocalDate.class),
                  rs.getBoolean("jdg_active"),
                  rs.getBigDecimal("ryczalt_rate"),
                  rs.getBoolean("vat_registered"),
                  rs.getBoolean("vat_eu_registered"),
                  rs.getString("zus_regime"),
                  rs.getBoolean("voluntary_sickness")));
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public List<AccountingVatTransaction> vatTransactionsForPeriod(LocalDate period) {
    try {
      return jdbcTemplate.query(
          "SELECT tax_date, source_document_id, reference, direction, treatment, counterparty_country, counterparty_tax_identifier, identifier_type, vat_eu_number, vies_verified_at, vies_status, net_amount, vat_amount, deductible_vat, evidence FROM investory.accounting_vat_transaction WHERE tax_period = ? ORDER BY id",
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
                  rs.getString("evidence")),
          period);
    } catch (DataAccessException ignored) {
      return List.of();
    }
  }

  public List<AccountingIssue> sourceIssuesForPeriod(LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT external_reference, processing_error
          FROM investory.accounting_source_evidence
         WHERE processing_status IN ('REVIEW_REQUIRED', 'FAILED')
           AND (document_date = ? OR document_date IS NULL)
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
        period);
  }
}
