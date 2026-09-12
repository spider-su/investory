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
        "SELECT confirmed_at, confirmed_calculation_hash FROM investory.accounting_poc_period_state WHERE tax_period = ?",
        rs ->
            rs.next()
                ? new PeriodState(
                    rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                    rs.getString(2))
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

  public record PeriodState(Instant confirmedAt, String confirmedCalculationHash) {}

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
    return jdbcTemplate.update(
            """
        INSERT INTO investory.accounting_poc_invoice
            (tax_period, issue_date, sale_date, reference, customer_alias, invoice_kind, currency,
             net_amount, vat_amount, gross_amount, expected_receivable, booked_net_pln,
             ryczalt_rate, note, source_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            sourceId)
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
    return jdbcTemplate.update(
            """
        INSERT INTO investory.accounting_poc_expense_invoice
            (tax_period, invoice_date, reference, supplier_alias, category, currency,
             net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality, note, source_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            sourceId)
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
