package com.smartbox.investory.accounting.staging;

import com.smartbox.investory.accounting.AccountingExpenseNormalizer;
import com.smartbox.investory.accounting.AccountingInvoiceIngestionService.ReviewedInvoice;
import com.smartbox.investory.integrations.bank.ExternalBankTransaction;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** New acquisition boundary. It only writes inspectable staging rows. */
@Service
@RequiredArgsConstructor
public class AccountingStagingAcquisitionService {
  private final AccountingStagingRepository repository;
  private final AccountingExpenseNormalizer expenseNormalizer;

  public long stageInvoice(long profileId, ReviewedInvoice invoice) {
    validate(invoice);
    long sourceId = sourceId(invoice.sourceIdentity());
    boolean expense =
        invoice.documentType().equals("PURCHASE_INVOICE")
            || invoice.documentType().equals("RECEIPT");
    var normalized =
        expense
            ? expenseNormalizer.normalize(
                new AccountingExpenseNormalizer.ExpenseImportCandidate(
                    invoice.category(),
                    invoice.grossAmount(),
                    invoice.netAmount(),
                    invoice.vatAmount(),
                    invoice.vatDeductionRatio()))
            : null;
    return repository.insertInvoice(
        profileId,
        invoice.taxPeriod(),
        sourceId,
        "UPLOAD",
        invoice.sourceIdentity(),
        expense ? "EXPENSE" : "SALES",
        expense ? first(invoice.issueDate(), invoice.saleDate()) : invoice.issueDate(),
        invoice.reference().trim(),
        invoice.counterpartyAlias().trim(),
        invoice.counterpartyTaxIdentifier(),
        invoice.counterpartyCountry(),
        invoice.currency().trim().toUpperCase(),
        normalized == null ? invoice.netAmount() : normalized.netAmount(),
        normalized == null ? invoice.vatAmount() : normalized.vatAmount(),
        normalized == null ? invoice.grossAmount() : normalized.grossAmount(),
        normalized == null ? null : normalized.vatDeductionRatio(),
        normalized == null ? null : normalized.deductibleVat(),
        null,
        invoice.ksefNumber());
  }

  public long stageBank(
      long profileId,
      LocalDate taxPeriod,
      ExternalBankTransaction row,
      long sourceId,
      String sourceReference) {
    return repository.insertBank(
        profileId,
        taxPeriod,
        sourceId,
        "BANK",
        sourceReference,
        row.provider().name(),
        row.externalAccountId(),
        row.externalTransactionId(),
        row.bookingDate(),
        row.valueDate(),
        row.amount(),
        row.currency(),
        row.counterpartyName(),
        row.counterpartyAccount(),
        row.remittanceInformation(),
        row.sourcePayloadHash());
  }

  private void validate(ReviewedInvoice invoice) {
    if (invoice == null
        || invoice.taxPeriod() == null
        || invoice.documentType() == null
        || invoice.reference() == null
        || invoice.reference().isBlank()
        || invoice.counterpartyAlias() == null
        || invoice.counterpartyAlias().isBlank()
        || invoice.currency() == null
        || invoice.currency().isBlank()
        || invoice.sourceIdentity() == null
        || invoice.sourceIdentity().isBlank())
      throw new IllegalArgumentException("Staged invoice is missing required fields");
    if (invoice.netAmount() == null
        || invoice.vatAmount() == null
        || invoice.grossAmount() == null
        || invoice.netAmount().add(invoice.vatAmount()).compareTo(invoice.grossAmount()) != 0)
      throw new IllegalArgumentException("Net + VAT must equal gross before staging");
  }

  private long sourceId(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Source identity must be a numeric source ID", e);
    }
  }

  private static LocalDate first(LocalDate one, LocalDate two) {
    return one != null ? one : two;
  }
}
