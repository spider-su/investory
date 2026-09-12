package com.smartbox.investory.accounting.staging;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountingStagingReconciliationService {
  private final AccountingStagingRepository repository;
  private final JdbcTemplate jdbc;

  @Transactional
  public Summary reconcile(long profileId, LocalDate taxPeriod) {
    repository.invoices(profileId, taxPeriod).forEach(this::reconcileInvoice);
    repository.bankTransactions(profileId, taxPeriod).forEach(this::reconcileBank);
    return summary(profileId, taxPeriod);
  }

  public Summary summary(long profileId, LocalDate taxPeriod) {
    return new Summary(
        count("invoice", profileId, taxPeriod, StagingReconciliationStatus.MATCH),
        count("invoice", profileId, taxPeriod, StagingReconciliationStatus.NEW),
        count("invoice", profileId, taxPeriod, StagingReconciliationStatus.MISMATCH),
        count("invoice", profileId, taxPeriod, StagingReconciliationStatus.AMBIGUOUS),
        count("bank_transaction", profileId, taxPeriod, StagingReconciliationStatus.MATCH),
        count("bank_transaction", profileId, taxPeriod, StagingReconciliationStatus.NEW),
        count("bank_transaction", profileId, taxPeriod, StagingReconciliationStatus.MISMATCH),
        count("bank_transaction", profileId, taxPeriod, StagingReconciliationStatus.AMBIGUOUS));
  }

  private int count(
      String table, long profileId, LocalDate period, StagingReconciliationStatus status) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM investory.accounting_tmp_"
            + table
            + " WHERE profile_id=? AND tax_period=? AND reconciliation_status=?",
        Integer.class,
        profileId,
        period,
        status.name());
  }

  private void reconcileInvoice(StagedInvoice row) {
    String table =
        row.documentKind().equals("EXPENSE")
            ? "accounting_poc_expense_invoice"
            : "accounting_poc_invoice";
    String ksefColumn = row.documentKind().equals("EXPENSE") ? "ksef_number" : "ksef_number";
    List<Long> candidates =
        row.ksefNumber() == null || row.ksefNumber().isBlank()
            ? jdbc.queryForList(
                "SELECT id FROM investory." + table + " WHERE reference = ?",
                Long.class,
                row.reference())
            : jdbc.queryForList(
                "SELECT id FROM investory." + table + " WHERE ksef_number = ? OR reference = ?",
                Long.class,
                row.ksefNumber(),
                row.reference());
    if (candidates.isEmpty()) {
      repository.result(
          "invoice",
          row.id(),
          StagingReconciliationStatus.NEW,
          List.of(),
          "No canonical invoice candidate");
      return;
    }
    if (candidates.size() > 1) {
      repository.result(
          "invoice",
          row.id(),
          StagingReconciliationStatus.AMBIGUOUS,
          List.of("MULTIPLE_CANONICAL_MATCHES"),
          "Multiple canonical invoice candidates");
      return;
    }
    Long id = candidates.getFirst();
    String sql =
        row.documentKind().equals("EXPENSE")
            ? "SELECT invoice_date, supplier_alias, currency, net_amount, vat_amount, gross_amount, vat_deduction_ratio FROM investory.accounting_poc_expense_invoice WHERE id=?"
            : "SELECT COALESCE(issue_date,sale_date), customer_alias, currency, net_amount, vat_amount, gross_amount, NULL FROM investory.accounting_poc_invoice WHERE id=?";
    var differences =
        jdbc.queryForObject(
            sql,
            (rs, n) -> {
              var reasons = new ArrayList<String>();
              LocalDate date = rs.getObject(1, LocalDate.class);
              if (row.documentDate() != null && date != null && !row.documentDate().equals(date))
                reasons.add("DATE_MISMATCH");
              if (!equalsText(row.counterpartyName(), rs.getString(2)))
                reasons.add("COUNTERPARTY_MISMATCH");
              if (!equalsText(row.currency(), rs.getString(3))) reasons.add("CURRENCY_MISMATCH");
              if (!equalMoney(row.netAmount(), rs.getBigDecimal(4))) reasons.add("NET_MISMATCH");
              if (!equalMoney(row.vatAmount(), rs.getBigDecimal(5))) reasons.add("VAT_MISMATCH");
              if (!equalMoney(row.grossAmount(), rs.getBigDecimal(6)))
                reasons.add("GROSS_MISMATCH");
              if (row.vatDeductionRatio() != null
                  && !equalMoney(row.vatDeductionRatio(), rs.getBigDecimal(7)))
                reasons.add("VAT_TREATMENT_MISMATCH");
              return reasons;
            },
            id);
    repository.result(
        "invoice",
        row.id(),
        differences.isEmpty()
            ? StagingReconciliationStatus.MATCH
            : StagingReconciliationStatus.MISMATCH,
        differences,
        differences.isEmpty() ? "Canonical invoice matches" : "Canonical invoice differs");
  }

  private void reconcileBank(StagedBankTransaction row) {
    List<Long> exact =
        row.externalTransactionId() == null
            ? List.of()
            : jdbc.queryForList(
                "SELECT id FROM investory.accounting_poc_bank_transaction WHERE provider=? AND external_account_id IS NOT DISTINCT FROM ? AND external_transaction_id=?",
                Long.class,
                row.provider(),
                row.externalAccountId(),
                row.externalTransactionId());
    if (exact.isEmpty()) {
      List<Long> fallback =
          jdbc.queryForList(
              "SELECT id FROM investory.accounting_poc_bank_transaction WHERE booking_date=? AND amount=? AND currency=? AND (reference=? OR note LIKE ?)",
              Long.class,
              row.bookingDate(),
              row.amount(),
              row.currency(),
              row.remittanceInformation(),
              "%" + row.remittanceInformation() + "%");
      repository.result(
          "bank_transaction",
          row.id(),
          fallback.size() > 1
              ? StagingReconciliationStatus.AMBIGUOUS
              : StagingReconciliationStatus.NEW,
          fallback.size() > 1 ? List.of("MULTIPLE_CANONICAL_MATCHES") : List.of(),
          fallback.isEmpty()
              ? "No canonical bank candidate"
              : "Legacy fallback candidate requires explicit review");
      return;
    }
    if (exact.size() > 1) {
      repository.result(
          "bank_transaction",
          row.id(),
          StagingReconciliationStatus.AMBIGUOUS,
          List.of("MULTIPLE_CANONICAL_MATCHES"),
          "Multiple canonical bank candidates");
      return;
    }
    var differences =
        jdbc.queryForObject(
            "SELECT booking_date, amount, currency, reference FROM investory.accounting_poc_bank_transaction WHERE id=?",
            (rs, n) -> {
              var reasons = new ArrayList<String>();
              if (!row.bookingDate().equals(rs.getObject(1, LocalDate.class)))
                reasons.add("BOOKING_DATE_MISMATCH");
              if (!equalMoney(row.amount(), rs.getBigDecimal(2))) reasons.add("AMOUNT_MISMATCH");
              if (!equalsText(row.currency(), rs.getString(3))) reasons.add("CURRENCY_MISMATCH");
              if (row.remittanceInformation() != null
                  && !equalsText(row.remittanceInformation(), rs.getString(4)))
                reasons.add("REFERENCE_MISMATCH");
              return reasons;
            },
            exact.getFirst());
    repository.result(
        "bank_transaction",
        row.id(),
        differences.isEmpty()
            ? StagingReconciliationStatus.MATCH
            : StagingReconciliationStatus.MISMATCH,
        differences,
        differences.isEmpty()
            ? "Canonical bank transaction matches"
            : "Canonical bank transaction differs");
  }

  private static boolean equalMoney(BigDecimal left, BigDecimal right) {
    return left != null && right != null && left.compareTo(right) == 0;
  }

  private static boolean equalsText(String left, String right) {
    return left == null ? right == null : left.equalsIgnoreCase(right);
  }

  public record Summary(
      int invoiceMatched,
      int invoiceNew,
      int invoiceMismatch,
      int invoiceAmbiguous,
      int bankMatched,
      int bankNew,
      int bankMismatch,
      int bankAmbiguous) {
    public int readyToPromote() {
      return invoiceNew + bankNew;
    }

    public int blockingCount() {
      return invoiceMismatch + invoiceAmbiguous + bankMismatch + bankAmbiguous;
    }
  }
}
