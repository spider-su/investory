package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingExpenseNormalizer.ExpenseImportCandidate;
import com.smartbox.investory.accounting.AccountingExpenseNormalizer.NormalizedExpense;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Normalizes reviewed invoice facts before they enter the Accounting POC tables. */
@Service
@RequiredArgsConstructor
public class AccountingInvoiceIngestionService {
  private static final BigDecimal RYCZALT_RATE = new BigDecimal("0.12");

  private final AccountingExpenseNormalizer expenseNormalizer;
  private final AccountingPocRepository repository;

  public boolean ingest(ReviewedInvoice invoice) {
    validate(invoice);
    if ("SALES_INVOICE".equals(invoice.documentType())) {
      return ingestSales(invoice);
    }
    if ("CREDIT_NOTE".equals(invoice.documentType())) {
      return ingestCreditNote(invoice);
    }
    if ("PURCHASE_INVOICE".equals(invoice.documentType())
        || "RECEIPT".equals(invoice.documentType())) {
      return ingestPurchase(invoice);
    }
    throw new IllegalArgumentException(
        "Credit-note persistence is intentionally parked; review the document without saving it yet.");
  }

  private boolean ingestPurchase(ReviewedInvoice invoice) {
    BigDecimal deductionRatio =
        invoice.vatDeductionRatio() == null
            ? defaultDeductionRatio(invoice.category())
            : invoice.vatDeductionRatio();
    NormalizedExpense normalized =
        expenseNormalizer.normalize(
            new ExpenseImportCandidate(
                invoice.category(),
                invoice.grossAmount(),
                invoice.netAmount(),
                invoice.vatAmount(),
                deductionRatio));
    if (invoice.sourceIdentity() == null || invoice.sourceIdentity().isBlank()) {
      return repository.insertExpense(
          invoice.taxPeriod(),
          firstNonNull(invoice.issueDate(), invoice.saleDate()),
          invoice.reference().trim(),
          invoice.counterpartyAlias().trim(),
          invoice.category().trim(),
          invoice.currency().trim().toUpperCase(),
          normalized.netAmount(),
          normalized.vatAmount(),
          normalized.grossAmount(),
          normalized.vatDeductionRatio(),
          invoice.sourceQuality(),
          invoice.note());
    }
    return repository.insertExpense(
        invoice.taxPeriod(),
        firstNonNull(invoice.issueDate(), invoice.saleDate()),
        invoice.reference().trim(),
        invoice.counterpartyAlias().trim(),
        invoice.category().trim(),
        invoice.currency().trim().toUpperCase(),
        normalized.netAmount(),
        normalized.vatAmount(),
        normalized.grossAmount(),
        normalized.vatDeductionRatio(),
        invoice.sourceQuality(),
        invoice.note(),
        sourceId(invoice.sourceIdentity()));
  }

  private boolean ingestSales(ReviewedInvoice invoice) {
    String currency = invoice.currency().trim().toUpperCase();
    String invoiceKind = "PLN".equals(currency) ? "DOMESTIC_SERVICE" : "EU_SERVICE";
    BigDecimal bookedNetPln = "PLN".equals(currency) ? invoice.netAmount() : null;
    if (invoice.sourceIdentity() == null || invoice.sourceIdentity().isBlank()) {
      return repository.insertSalesInvoice(
          invoice.taxPeriod(),
          invoice.issueDate(),
          invoice.saleDate(),
          invoice.reference().trim(),
          invoice.counterpartyAlias().trim(),
          invoiceKind,
          currency,
          invoice.netAmount(),
          invoice.vatAmount(),
          invoice.grossAmount(),
          bookedNetPln,
          RYCZALT_RATE,
          invoice.note());
    }
    return repository.insertSalesInvoice(
        invoice.taxPeriod(),
        invoice.issueDate(),
        invoice.saleDate(),
        invoice.reference().trim(),
        invoice.counterpartyAlias().trim(),
        invoiceKind,
        currency,
        invoice.netAmount(),
        invoice.vatAmount(),
        invoice.grossAmount(),
        bookedNetPln,
        RYCZALT_RATE,
        invoice.note(),
        sourceId(invoice.sourceIdentity()));
  }

  private boolean ingestCreditNote(ReviewedInvoice invoice) {
    String currency = invoice.currency().trim().toUpperCase();
    BigDecimal signedNet = invoice.netAmount().negate();
    BigDecimal signedVat = invoice.vatAmount().negate();
    BigDecimal signedGross = invoice.grossAmount().negate();
    BigDecimal bookedNetPln = "PLN".equals(currency) ? signedNet : null;
    if (invoice.sourceIdentity() == null || invoice.sourceIdentity().isBlank()) {
      return repository.insertSalesInvoice(
          invoice.taxPeriod(),
          invoice.issueDate(),
          invoice.saleDate(),
          invoice.reference().trim(),
          invoice.counterpartyAlias().trim(),
          "CREDIT_NOTE",
          currency,
          signedNet,
          signedVat,
          signedGross,
          bookedNetPln,
          RYCZALT_RATE,
          invoice.note());
    }
    return repository.insertSalesInvoice(
        invoice.taxPeriod(),
        invoice.issueDate(),
        invoice.saleDate(),
        invoice.reference().trim(),
        invoice.counterpartyAlias().trim(),
        "CREDIT_NOTE",
        currency,
        signedNet,
        signedVat,
        signedGross,
        bookedNetPln,
        RYCZALT_RATE,
        invoice.note(),
        sourceId(invoice.sourceIdentity()));
  }

  private void validate(ReviewedInvoice invoice) {
    if (invoice == null
        || invoice.taxPeriod() == null
        || invoice.documentType() == null
        || invoice.documentType().isBlank()
        || invoice.reference() == null
        || invoice.reference().isBlank()
        || invoice.counterpartyAlias() == null
        || invoice.counterpartyAlias().isBlank()
        || invoice.category() == null
        || invoice.category().isBlank()
        || invoice.currency() == null
        || invoice.currency().isBlank()) {
      throw new IllegalArgumentException("Reviewed invoice is missing required fields");
    }
    if (invoice.netAmount() == null
        || invoice.vatAmount() == null
        || invoice.grossAmount() == null) {
      throw new IllegalArgumentException("Net, VAT and gross amounts are required before saving");
    }
    if (invoice.netAmount().add(invoice.vatAmount()).compareTo(invoice.grossAmount()) != 0) {
      throw new IllegalArgumentException("Net + VAT must equal gross before saving");
    }
  }

  private BigDecimal defaultDeductionRatio(String category) {
    return "VEHICLE_FUEL".equals(category) ? new BigDecimal("0.50") : BigDecimal.ONE;
  }

  private LocalDate firstNonNull(LocalDate first, LocalDate second) {
    return first != null ? first : second;
  }

  private Long sourceId(String identity) {
    if (identity == null || identity.isBlank()) return null;
    try {
      return Long.valueOf(identity);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Source identity must be a numeric source ID", exception);
    }
  }

  public record ReviewedInvoice(
      LocalDate taxPeriod,
      String documentType,
      LocalDate issueDate,
      LocalDate saleDate,
      String reference,
      String counterpartyAlias,
      String category,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal vatDeductionRatio,
      String sourceQuality,
      String note,
      String sourceIdentity) {
    public ReviewedInvoice(
        LocalDate taxPeriod,
        String documentType,
        LocalDate issueDate,
        LocalDate saleDate,
        String reference,
        String counterpartyAlias,
        String category,
        String currency,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount,
        BigDecimal vatDeductionRatio,
        String sourceQuality,
        String note) {
      this(
          taxPeriod,
          documentType,
          issueDate,
          saleDate,
          reference,
          counterpartyAlias,
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
  }
}
