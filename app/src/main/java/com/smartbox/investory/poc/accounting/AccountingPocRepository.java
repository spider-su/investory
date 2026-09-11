package com.smartbox.investory.poc.accounting;

import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.TaxInputRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountingPocRepository {
  private final JdbcTemplate jdbcTemplate;

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
               booked_net_pln, ryczalt_rate, note
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
                rs.getString("note")),
        period);
  }

  public List<ExpenseRow> expensesForPeriod(LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT id, tax_period, invoice_date, reference, supplier_alias, category, currency,
               net_amount, vat_amount, gross_amount, vat_deduction_ratio,
               ROUND(vat_amount * vat_deduction_ratio, 2) AS deductible_vat,
               source_quality, note
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
                rs.getString("note")),
        period);
  }

  public void insertExpense(
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
    jdbcTemplate.update(
        """
        INSERT INTO investory.accounting_poc_expense_invoice
            (tax_period, invoice_date, reference, supplier_alias, category, currency,
             net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality, note)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        note);
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

  public List<ObligationRow> obligationsForPeriod(LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note
          FROM investory.accounting_poc_obligation
         WHERE tax_period = ?
         ORDER BY obligation_type
        """,
        (rs, rowNum) ->
            new ObligationRow(
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
}
