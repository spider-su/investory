package com.smartbox.investory.accounting.service;

import com.smartbox.investory.accounting.*;
import com.smartbox.investory.accounting.AccountingExpenseNormalizer.ExpenseImportCandidate;
import com.smartbox.investory.accounting.AccountingExpenseNormalizer.NormalizedExpense;
import com.smartbox.investory.accounting.infrastructure.persistence.*;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Normalizes reviewed invoice facts before they enter the Accounting POC tables. */
@Service
@RequiredArgsConstructor
public class AccountingInvoiceIngestionService {
  private static final BigDecimal RYCZALT_RATE = new BigDecimal("0.12");

  private final AccountingExpenseNormalizer expenseNormalizer;
  private final AccountingPocRepository repository;

  public boolean ingest(ReviewedInvoice invoice) {
    validate(invoice);
    rememberCounterparty(1L, invoice);
    invoice = canonicalizeCounterparty(1L, invoice);
    if ("SALES_INVOICE".equals(invoice.documentType())) return ingestSalesLegacy(invoice);
    if ("CREDIT_NOTE".equals(invoice.documentType())) return ingestCreditNoteLegacy(invoice);
    if ("PURCHASE_INVOICE".equals(invoice.documentType())
        || "RECEIPT".equals(invoice.documentType())) return ingestPurchaseLegacy(invoice);
    throw new IllegalArgumentException("Unsupported invoice document type");
  }

  private boolean ingestPurchaseLegacy(ReviewedInvoice invoice) {
    BigDecimal ratio =
        invoice.vatDeductionRatio() == null
            ? defaultDeductionRatio(invoice.category())
            : invoice.vatDeductionRatio();
    NormalizedExpense n =
        expenseNormalizer.normalize(
            new ExpenseImportCandidate(
                invoice.category(),
                invoice.grossAmount(),
                invoice.netAmount(),
                invoice.vatAmount(),
                ratio));
    return repository.insertExpense(
        invoice.taxPeriod(),
        firstNonNull(invoice.issueDate(), invoice.saleDate()),
        invoice.reference().trim(),
        invoice.counterpartyAlias().trim(),
        invoice.category().trim(),
        invoice.currency().trim().toUpperCase(),
        n.netAmount(),
        n.vatAmount(),
        n.grossAmount(),
        n.vatDeductionRatio(),
        invoice.sourceQuality(),
        invoice.note());
  }

  private boolean ingestSalesLegacy(ReviewedInvoice invoice) {
    String currency = invoice.currency().trim().toUpperCase();
    return repository.insertSalesInvoice(
        invoice.taxPeriod(),
        invoice.issueDate(),
        invoice.saleDate(),
        invoice.reference().trim(),
        invoice.counterpartyAlias().trim(),
        "PLN".equals(currency) ? "DOMESTIC_SERVICE" : "EU_SERVICE",
        currency,
        invoice.netAmount(),
        invoice.vatAmount(),
        invoice.grossAmount(),
        "PLN".equals(currency) ? invoice.netAmount() : null,
        RYCZALT_RATE,
        invoice.note());
  }

  private boolean ingestCreditNoteLegacy(ReviewedInvoice invoice) {
    String currency = invoice.currency().trim().toUpperCase();
    return repository.insertSalesInvoice(
        invoice.taxPeriod(),
        invoice.issueDate(),
        invoice.saleDate(),
        invoice.reference().trim(),
        invoice.counterpartyAlias().trim(),
        "CREDIT_NOTE",
        currency,
        invoice.netAmount().negate(),
        invoice.vatAmount().negate(),
        invoice.grossAmount().negate(),
        "PLN".equals(currency) ? invoice.netAmount().negate() : null,
        RYCZALT_RATE,
        invoice.note());
  }

  @Transactional
  public boolean ingest(long profileId, ReviewedInvoice invoice) {
    validate(invoice);
    rememberCounterparty(profileId, invoice);
    invoice = canonicalizeCounterparty(profileId, invoice);
    boolean inserted;
    if ("SALES_INVOICE".equals(invoice.documentType())) {
      inserted = ingestSales(profileId, invoice);
    } else if ("CREDIT_NOTE".equals(invoice.documentType())) {
      inserted = ingestCreditNote(profileId, invoice);
    } else if ("PURCHASE_INVOICE".equals(invoice.documentType())
        || "RECEIPT".equals(invoice.documentType())) {
      inserted = ingestPurchase(profileId, invoice);
    } else {
      throw new IllegalArgumentException(
          "Credit-note persistence is intentionally parked; review the document without saving it yet.");
    }
    if (!inserted && invoice.hasFilingProvenance()) {
      repository.enrichLegacyDocumentFromKsef(
          profileId,
          "PURCHASE_INVOICE".equals(invoice.documentType())
                  || "RECEIPT".equals(invoice.documentType())
              ? "PURCHASE"
              : "SALE",
          invoice.reference().trim(),
          firstNonNull(invoice.issueDate(), invoice.saleDate()),
          invoice.currency().trim().toUpperCase(),
          "CREDIT_NOTE".equals(invoice.documentType())
              ? negative(invoice.netAmount())
              : invoice.netAmount(),
          "CREDIT_NOTE".equals(invoice.documentType())
              ? negative(invoice.vatAmount())
              : invoice.vatAmount(),
          "CREDIT_NOTE".equals(invoice.documentType())
              ? negative(invoice.grossAmount())
              : invoice.grossAmount(),
          sourceIdOrNull(invoice.sourceIdentity()),
          invoice.ksefNumber(),
          invoice.counterpartyTaxIdentifier(),
          invoice.counterpartyCountry(),
          invoice.note());
    }
    upsertCanonicalDocument(profileId, invoice);
    return inserted;
  }

  private void upsertCanonicalDocument(long profileId, ReviewedInvoice invoice) {
    boolean purchase =
        "PURCHASE_INVOICE".equals(invoice.documentType())
            || "RECEIPT".equals(invoice.documentType());
    boolean creditNote = "CREDIT_NOTE".equals(invoice.documentType());
    String direction = purchase ? "PURCHASE" : "SALE";
    String currency = invoice.currency().trim().toUpperCase();
    BigDecimal netAmount = creditNote ? negative(invoice.netAmount()) : invoice.netAmount();
    BigDecimal vatAmount = creditNote ? negative(invoice.vatAmount()) : invoice.vatAmount();
    BigDecimal grossAmount = creditNote ? negative(invoice.grossAmount()) : invoice.grossAmount();
    BigDecimal deductionRatio =
        purchase
            ? (invoice.vatDeductionRatio() == null
                ? defaultDeductionRatio(invoice.category())
                : invoice.vatDeductionRatio())
            : null;
    VatTreatment treatment =
        purchase
            ? VatTreatment.DOMESTIC_PURCHASE
            : "PLN".equals(currency)
                ? VatTreatment.DOMESTIC_VAT
                : VatTreatment.EU_B2B_REVERSE_CHARGE;
    BigDecimal vatRate =
        invoice.vatRate() != null
            ? invoice.vatRate()
            : inferredVatRate(netAmount, vatAmount, treatment);
    BigDecimal deductibleVat = purchase ? vatAmount.multiply(deductionRatio) : BigDecimal.ZERO;
    CanonicalAccountingDocument canonical =
        new CanonicalAccountingDocument(
            purchase
                ? CanonicalAccountingDocument.Direction.PURCHASE
                : CanonicalAccountingDocument.Direction.SALE,
            creditNote
                ? CanonicalAccountingDocument.Kind.CREDIT_NOTE
                : CanonicalAccountingDocument.Kind.INVOICE,
            AccountingTaxPeriod.of(invoice.taxPeriod()),
            AccountingDocumentReference.of(invoice.reference()),
            currency,
            netAmount,
            vatAmount,
            grossAmount,
            java.util.List.of(
                new CanonicalAccountingDocument.VatBucket(
                    treatment, vatRate, netAmount, vatAmount, deductibleVat)));
    repository.upsertCanonicalDocument(
        profileId,
        canonical.direction().name(),
        canonical.kind().name(),
        canonical.taxPeriod().start(),
        invoice.issueDate(),
        invoice.saleDate(),
        invoice.dueDate(),
        canonical.reference().value(),
        invoice.counterpartyAlias().trim(),
        invoice.counterpartyTaxIdentifier(),
        invoice.counterpartyCountry(),
        currency,
        netAmount,
        vatAmount,
        grossAmount,
        null,
        !purchase && "PLN".equals(currency) ? netAmount : null,
        !purchase ? RYCZALT_RATE : null,
        purchase ? invoice.category().trim() : null,
        deductionRatio,
        purchase ? invoice.sourceQuality() : null,
        sourceIdOrNull(invoice.sourceIdentity()),
        invoice.ksefNumber(),
        invoice.filingEvidence(),
        invoice.note(),
        treatment,
        vatRate,
        deductibleVat);
  }

  private BigDecimal inferredVatRate(
      BigDecimal netAmount, BigDecimal vatAmount, VatTreatment treatment) {
    if (treatment != VatTreatment.DOMESTIC_VAT && treatment != VatTreatment.DOMESTIC_PURCHASE) {
      return null;
    }
    if (netAmount.signum() == 0) return BigDecimal.ZERO;
    return vatAmount
        .multiply(new BigDecimal("100"))
        .divide(netAmount, 2, java.math.RoundingMode.HALF_UP);
  }

  private ReviewedInvoice canonicalizeCounterparty(long profileId, ReviewedInvoice invoice) {
    AccountingKnownCounterparty known =
        repository.knownCounterparty(
            profileId, invoice.counterpartyTaxIdentifier(), invoice.counterpartyCountry());
    if (known == null) return invoice;
    return new ReviewedInvoice(
        invoice.taxPeriod(),
        invoice.documentType(),
        invoice.issueDate(),
        invoice.saleDate(),
        invoice.reference(),
        known.canonicalName(),
        invoice.category(),
        invoice.currency(),
        invoice.netAmount(),
        invoice.vatAmount(),
        invoice.grossAmount(),
        invoice.vatDeductionRatio(),
        invoice.sourceQuality(),
        invoice.note(),
        invoice.sourceIdentity(),
        known.taxIdentifier(),
        known.country(),
        invoice.ksefNumber(),
        invoice.filingEvidence(),
        invoice.dueDate(),
        invoice.vatRate());
  }

  private void rememberCounterparty(long profileId, ReviewedInvoice invoice) {
    repository.rememberKnownCounterparty(
        profileId,
        invoice.counterpartyTaxIdentifier(),
        invoice.counterpartyCountry(),
        invoice.counterpartyAlias());
  }

  private boolean ingestPurchase(long profileId, ReviewedInvoice invoice) {
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
    if (invoice.hasFilingProvenance()) {
      return repository.insertExpense(
          profileId,
          invoice.taxPeriod(),
          firstNonNull(invoice.issueDate(), invoice.saleDate()),
          invoice.dueDate(),
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
          sourceIdOrNull(invoice.sourceIdentity()),
          invoice.counterpartyTaxIdentifier(),
          invoice.counterpartyCountry(),
          invoice.ksefNumber(),
          invoice.filingEvidence());
    }
    if (invoice.sourceIdentity() == null || invoice.sourceIdentity().isBlank()) {
      return repository.insertExpense(
          profileId,
          invoice.taxPeriod(),
          firstNonNull(invoice.issueDate(), invoice.saleDate()),
          invoice.dueDate(),
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
          null,
          null,
          null,
          null,
          null);
    }
    return repository.insertExpense(
        profileId,
        invoice.taxPeriod(),
        firstNonNull(invoice.issueDate(), invoice.saleDate()),
        invoice.dueDate(),
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
        sourceId(invoice.sourceIdentity()),
        null,
        null,
        null,
        null);
  }

  private boolean ingestSales(long profileId, ReviewedInvoice invoice) {
    String currency = invoice.currency().trim().toUpperCase();
    String invoiceKind = "PLN".equals(currency) ? "DOMESTIC_SERVICE" : "EU_SERVICE";
    BigDecimal bookedNetPln = "PLN".equals(currency) ? invoice.netAmount() : null;
    if (invoice.hasFilingProvenance()) {
      return repository.insertSalesInvoice(
          profileId,
          invoice.taxPeriod(),
          invoice.issueDate(),
          invoice.saleDate(),
          invoice.dueDate(),
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
          sourceIdOrNull(invoice.sourceIdentity()),
          invoice.counterpartyTaxIdentifier(),
          invoice.counterpartyCountry(),
          invoice.ksefNumber(),
          invoice.filingEvidence());
    }
    if (invoice.sourceIdentity() == null || invoice.sourceIdentity().isBlank()) {
      return repository.insertSalesInvoice(
          profileId,
          invoice.taxPeriod(),
          invoice.issueDate(),
          invoice.saleDate(),
          invoice.dueDate(),
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
          null,
          null,
          null,
          null,
          null);
    }
    return repository.insertSalesInvoice(
        profileId,
        invoice.taxPeriod(),
        invoice.issueDate(),
        invoice.saleDate(),
        invoice.dueDate(),
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
        sourceId(invoice.sourceIdentity()),
        null,
        null,
        null,
        null);
  }

  private boolean ingestCreditNote(long profileId, ReviewedInvoice invoice) {
    String currency = invoice.currency().trim().toUpperCase();
    BigDecimal signedNet = negative(invoice.netAmount());
    BigDecimal signedVat = negative(invoice.vatAmount());
    BigDecimal signedGross = negative(invoice.grossAmount());
    BigDecimal bookedNetPln = "PLN".equals(currency) ? signedNet : null;
    if (invoice.sourceIdentity() == null || invoice.sourceIdentity().isBlank()) {
      return repository.insertSalesInvoice(
          profileId,
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
          null,
          null,
          null,
          null,
          null);
    }
    return repository.insertSalesInvoice(
        profileId,
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
        sourceId(invoice.sourceIdentity()),
        null,
        null,
        null,
        null);
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
        || invoice.currency() == null
        || invoice.currency().isBlank()) {
      throw new IllegalArgumentException("Reviewed invoice is missing required fields");
    }
    if (("PURCHASE_INVOICE".equals(invoice.documentType())
            || "RECEIPT".equals(invoice.documentType()))
        && (invoice.category() == null || invoice.category().isBlank())) {
      throw new IllegalArgumentException("Purchase category is required before saving");
    }
    if (isUnsupportedFixedAssetCategory(invoice.category())) {
      throw new IllegalArgumentException(
          "FIXED_ASSET_UNSUPPORTED: capitalised purchases are outside the Accounting POC");
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

  private boolean isUnsupportedFixedAssetCategory(String category) {
    if (category == null) return false;
    return switch (category.trim().toUpperCase(java.util.Locale.ROOT)) {
      case "FIXED_ASSET", "CAPITAL_ASSET" -> true;
      default -> false;
    };
  }

  private BigDecimal negative(BigDecimal value) {
    return value.signum() > 0 ? value.negate() : value;
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

  private Long sourceIdOrNull(String identity) {
    return identity == null || identity.isBlank() ? null : sourceId(identity);
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
      String sourceIdentity,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String ksefNumber,
      AccountingFilingEvidence filingEvidence,
      LocalDate dueDate,
      BigDecimal vatRate) {
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
        String note,
        String sourceIdentity,
        String counterpartyTaxIdentifier,
        String counterpartyCountry,
        String ksefNumber,
        AccountingFilingEvidence filingEvidence,
        LocalDate dueDate) {
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
          sourceIdentity,
          counterpartyTaxIdentifier,
          counterpartyCountry,
          ksefNumber,
          filingEvidence,
          dueDate,
          null);
    }

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
        String note,
        String sourceIdentity,
        String counterpartyTaxIdentifier,
        String counterpartyCountry,
        String ksefNumber,
        AccountingFilingEvidence filingEvidence) {
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
          sourceIdentity,
          counterpartyTaxIdentifier,
          counterpartyCountry,
          ksefNumber,
          filingEvidence,
          null,
          null);
    }

    public boolean hasFilingProvenance() {
      return counterpartyTaxIdentifier != null
          || counterpartyCountry != null
          || ksefNumber != null
          || filingEvidence != null;
    }

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
        String note,
        String sourceIdentity) {
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
          sourceIdentity,
          null,
          null,
          null,
          null);
    }

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
          null,
          null,
          null,
          null,
          null);
    }
  }
}
