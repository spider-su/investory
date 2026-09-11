package com.smartbox.investory.poc.accounting;

import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ReconciliationRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingFactService {
  private static final LocalDate JULY_2026 = LocalDate.of(2026, 7, 1);

  private final AccountingFactRepository factRepository;
  private final AccountingPocRepository pocRepository;

  public List<AccountingFact> facts() {
    return factRepository.findAll();
  }

  public AccountingMonthSnapshot july2026() {
    return snapshot(JULY_2026);
  }

  AccountingMonthSnapshot snapshot(LocalDate period) {
    List<InvoiceRow> invoices = pocRepository.invoicesForPeriod(period);
    List<BankRow> bankTransactions = pocRepository.bankTransactionsForPeriod(period);
    List<ObligationRow> obligations = pocRepository.obligationsForPeriod(period);

    BigDecimal domesticRevenue =
        invoices.stream()
            .filter(invoice -> invoice.taxPeriod().equals(period))
            .filter(invoice -> "PLN".equals(invoice.currency()))
            .map(InvoiceRow::bookedNetPln)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal foreignRevenuePln =
        invoices.stream()
            .filter(invoice -> invoice.taxPeriod().equals(period))
            .filter(invoice -> !"PLN".equals(invoice.currency()))
            .map(InvoiceRow::bookedNetPln)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal foreignSourceEur =
        invoices.stream()
            .filter(invoice -> invoice.taxPeriod().equals(period))
            .filter(invoice -> "EUR".equals(invoice.currency()))
            .map(InvoiceRow::netAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<ReconciliationRow> reconciliations =
        reconcile(invoices, bankTransactions, obligations);

    return new AccountingMonthSnapshot(
        period,
        domesticRevenue,
        foreignRevenuePln,
        foreignSourceEur,
        domesticRevenue.add(foreignRevenuePln),
        invoices,
        reconciliations,
        obligations,
        bankTransactions);
  }

  private List<ReconciliationRow> reconcile(
      List<InvoiceRow> invoices,
      List<BankRow> bankTransactions,
      List<ObligationRow> obligations) {
    List<ReconciliationRow> result = new ArrayList<>();

    for (InvoiceRow invoice : invoices) {
      BankRow match =
          bankTransactions.stream()
              .filter(row -> "BUSINESS".equals(row.scope()))
              .filter(row -> "CUSTOMER_RECEIPT".equals(row.transactionType()))
              .filter(row -> invoice.currency().equals(row.currency()))
              .filter(row -> invoice.expectedReceivable().compareTo(row.amount()) == 0)
              .filter(
                  row ->
                      invoice.reference().equalsIgnoreCase(row.reference())
                          || invoice.customerAlias().equals(row.counterpartyAlias()))
              .findFirst()
              .orElse(null);

      String explanation =
          invoice.correctionGrossAmount().signum() == 0
              ? "Exact expected receivable matched to a business customer receipt."
              : "Exact corrected receivable matched; original invoice remains preserved.";

      result.add(
          new ReconciliationRow(
              invoice.reference(),
              "INVOICE_PAYMENT",
              invoice.expectedReceivable(),
              invoice.currency(),
              match == null ? BigDecimal.ZERO : match.amount(),
              match == null ? null : match.bookingDate(),
              match == null ? "UNMATCHED" : "MATCHED",
              match == null ? "No exact business receipt found." : explanation));
    }

    for (ObligationRow obligation : obligations) {
      result.add(
          new ReconciliationRow(
              obligation.obligationType(),
              "OBLIGATION_PAYMENT",
              obligation.expectedAmount(),
              "PLN",
              obligation.paidAmount(),
              obligation.paymentDate(),
              obligation.status(),
              obligation.note()));
    }

    return List.copyOf(result);
  }
}
