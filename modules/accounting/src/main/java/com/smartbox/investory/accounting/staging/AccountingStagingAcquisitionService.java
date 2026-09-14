package com.smartbox.investory.accounting.staging;

import com.smartbox.investory.accounting.AccountingExpenseNormalizer;
import com.smartbox.investory.accounting.VatTreatment;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingStagingRepository;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService.ReviewedInvoice;
import com.smartbox.investory.integrations.bank.ExternalBankTransaction;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** New acquisition boundary. It only writes inspectable staging rows. */
@Service
@RequiredArgsConstructor
public class AccountingStagingAcquisitionService {
  private static final Set<String> SALES_DOCUMENT_TYPES = Set.of("SALES_INVOICE", "CREDIT_NOTE");
  private static final Set<String> PURCHASE_DOCUMENT_TYPES = Set.of("PURCHASE_INVOICE", "RECEIPT");
  private static final Set<VatTreatment> SALES_VAT_TREATMENTS =
      Set.of(
          VatTreatment.DOMESTIC_VAT,
          VatTreatment.EU_B2B_REVERSE_CHARGE,
          VatTreatment.NON_EU_B2B_OUTSIDE_POLAND,
          VatTreatment.VAT_EXEMPT);
  private static final Set<VatTreatment> PURCHASE_VAT_TREATMENTS =
      Set.of(
          VatTreatment.DOMESTIC_PURCHASE,
          VatTreatment.IMPORT_OF_SERVICES_EU,
          VatTreatment.IMPORT_OF_SERVICES_NON_EU);
  private final AccountingStagingRepository repository;
  private final AccountingExpenseNormalizer expenseNormalizer;

  public long stageInvoice(long profileId, ReviewedInvoice invoice, String vatTreatment) {
    VatTreatment treatment = validate(invoice, vatTreatment);
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
    boolean creditNote = "CREDIT_NOTE".equals(invoice.documentType());
    var sign = creditNote ? java.math.BigDecimal.ONE.negate() : java.math.BigDecimal.ONE;
    var net = (normalized == null ? invoice.netAmount() : normalized.netAmount()).multiply(sign);
    var vat = (normalized == null ? invoice.vatAmount() : normalized.vatAmount()).multiply(sign);
    var gross =
        (normalized == null ? invoice.grossAmount() : normalized.grossAmount()).multiply(sign);
    var deductionRatio = normalized == null ? null : normalized.vatDeductionRatio();
    var deductibleVat = normalized == null ? null : normalized.deductibleVat();
    if (invoice.vatRate() == null) {
      return repository.insertInvoice(
          profileId,
          invoice.taxPeriod(),
          sourceId,
          invoice.ksefNumber() == null ? "UPLOAD" : "KSEF",
          invoice.ksefNumber() == null ? invoice.sourceIdentity() : invoice.ksefNumber(),
          expense ? "EXPENSE" : invoice.documentType(),
          expense ? first(invoice.issueDate(), invoice.saleDate()) : invoice.issueDate(),
          invoice.dueDate(),
          invoice.reference().trim(),
          invoice.counterpartyAlias().trim(),
          invoice.counterpartyTaxIdentifier(),
          invoice.counterpartyCountry(),
          invoice.currency().trim().toUpperCase(),
          net,
          vat,
          gross,
          deductionRatio,
          deductibleVat,
          treatment.name(),
          invoice.ksefNumber());
    }
    return repository.insertInvoice(
        profileId,
        invoice.taxPeriod(),
        sourceId,
        invoice.ksefNumber() == null ? "UPLOAD" : "KSEF",
        invoice.ksefNumber() == null ? invoice.sourceIdentity() : invoice.ksefNumber(),
        expense ? "EXPENSE" : invoice.documentType(),
        expense ? first(invoice.issueDate(), invoice.saleDate()) : invoice.issueDate(),
        invoice.dueDate(),
        invoice.reference().trim(),
        invoice.counterpartyAlias().trim(),
        invoice.counterpartyTaxIdentifier(),
        invoice.counterpartyCountry(),
        invoice.currency().trim().toUpperCase(),
        net,
        vat,
        gross,
        deductionRatio,
        deductibleVat,
        treatment.name(),
        invoice.vatRate(),
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
        row.relatedPeriod(),
        row.amount(),
        row.currency(),
        row.counterpartyName(),
        row.counterpartyAccount(),
        row.remittanceInformation(),
        row.sourcePayloadHash());
  }

  private VatTreatment validate(ReviewedInvoice invoice, String vatTreatment) {
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
    if (!SALES_DOCUMENT_TYPES.contains(invoice.documentType())
        && !PURCHASE_DOCUMENT_TYPES.contains(invoice.documentType())) {
      throw new IllegalArgumentException(
          "Choose sales, purchase, receipt, or correction before staging");
    }
    if (vatTreatment == null || vatTreatment.isBlank()) {
      throw new IllegalArgumentException("Staged invoice requires explicit VAT treatment");
    }
    final VatTreatment treatment;
    try {
      treatment = VatTreatment.valueOf(vatTreatment);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unsupported VAT treatment: " + vatTreatment, exception);
    }
    boolean salesDocument = SALES_DOCUMENT_TYPES.contains(invoice.documentType());
    if ((salesDocument && !SALES_VAT_TREATMENTS.contains(treatment))
        || (!salesDocument && !PURCHASE_VAT_TREATMENTS.contains(treatment))) {
      throw new IllegalArgumentException(
          "VAT treatment " + treatment + " is not valid for " + invoice.documentType());
    }
    return treatment;
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
