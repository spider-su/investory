package com.smartbox.investory.accounting.staging;

import com.smartbox.investory.accounting.AccountingSourceStatus;
import com.smartbox.investory.accounting.AccountingVatTransaction;
import com.smartbox.investory.accounting.VatTreatment;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingStagingRepository;
import com.smartbox.investory.accounting.service.AccountingBankTransactionIngestionService;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import com.smartbox.investory.integrations.bank.BankDataProvider;
import com.smartbox.investory.integrations.bank.ExternalBankTransaction;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountingStagingPromotionService {
  private final AccountingStagingRepository repository;
  private final AccountingStagingReconciliationService reconciliation;
  private final AccountingSourceEvidenceService sources;
  private final AccountingBankTransactionIngestionService bankIngestion;
  private final AccountingInvoiceIngestionService invoiceIngestion;
  private final AccountingPocRepository canonicalRepository;

  @Transactional
  public PromotionResult promoteNew(long profileId, LocalDate taxPeriod) {
    reconciliation.reconcile(profileId, taxPeriod);
    int invoices = 0;
    int bank = 0;
    for (StagedInvoice row : repository.invoices(profileId, taxPeriod)) {
      if (row.status() != StagingReconciliationStatus.NEW) continue;
      String documentType = documentType(row);
      Long correctionTarget = correctionTarget(row, documentType);
      if ("CREDIT_NOTE".equals(documentType) && correctionTarget == null) continue;
      var invoice =
          new AccountingInvoiceIngestionService.ReviewedInvoice(
              row.taxPeriod(),
              documentType,
              row.documentDate(),
              row.documentDate(),
              row.reference(),
              row.counterpartyName(),
              java.util.Objects.requireNonNullElse(row.category(), "OTHER"),
              row.currency(),
              row.netAmount(),
              row.vatAmount(),
              row.grossAmount(),
              row.vatDeductionRatio(),
              java.util.Objects.requireNonNullElse(row.sourceQuality(), "STAGED"),
              row.message(),
              Long.toString(row.sourceId()),
              row.counterpartyTaxIdentifier(),
              row.counterpartyCountry(),
              row.ksefNumber(),
              row.ksefNumber() == null
                  ? null
                  : new com.smartbox.investory.accounting.AccountingFilingEvidence(
                      com.smartbox.investory.accounting.AccountingFilingEvidence.Type.KSEF,
                      row.ksefNumber()),
              row.dueDate(),
              row.vatRate(),
              row.correctsDocumentReference());
      invoiceIngestion.ingest(profileId, invoice);
      if (correctionTarget != null)
        canonicalRepository.linkCanonicalCorrection(profileId, row.reference(), correctionTarget);
      canonicalRepository.insertVatTransaction(
          row.profileId(),
          row.taxPeriod(),
          row.documentDate() == null ? row.taxPeriod() : row.documentDate(),
          Long.toString(row.sourceId()),
          row.reference(),
          row.documentKind().equals("EXPENSE")
              ? AccountingVatTransaction.Direction.PURCHASE
              : AccountingVatTransaction.Direction.SALE,
          VatTreatment.valueOf(row.vatTreatment()),
          row.counterpartyCountry(),
          row.counterpartyTaxIdentifier(),
          row.netAmount(),
          row.vatAmount(),
          row.documentKind().equals("EXPENSE")
              ? java.util.Objects.requireNonNullElse(row.deductibleVat(), java.math.BigDecimal.ZERO)
              : java.math.BigDecimal.ZERO,
          "STAGED_SOURCE:" + row.sourceId(),
          row.vatRate());
      canonicalRepository.upsertCanonicalVatBucket(
          row.profileId(),
          row.documentKind().equals("EXPENSE")
              ? AccountingVatTransaction.Direction.PURCHASE
              : AccountingVatTransaction.Direction.SALE,
          row.reference(),
          VatTreatment.valueOf(row.vatTreatment()),
          row.vatRate(),
          row.netAmount(),
          row.vatAmount(),
          row.documentKind().equals("EXPENSE")
              ? java.util.Objects.requireNonNullElse(row.deductibleVat(), java.math.BigDecimal.ZERO)
              : java.math.BigDecimal.ZERO);
      repository.promoted(row.profileId(), "invoice", row.id(), repository.canonicalInvoiceId(row));
      invoices++;
    }
    for (StagedBankTransaction row : repository.bankTransactions(profileId, taxPeriod)) {
      if (row.status() != StagingReconciliationStatus.NEW) continue;
      bankIngestion.ingest(
          new ExternalBankTransaction(
              BankDataProvider.valueOf(row.provider()),
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
              row.sourceReference(),
              row.sourcePayloadHash()),
          row.sourceId(),
          row.profileId());
      repository.promoted(
          row.profileId(), "bank_transaction", row.id(), repository.canonicalBankId(row));
      bank++;
    }
    updateSourceStatuses(profileId, taxPeriod);
    return new PromotionResult(invoices, bank);
  }

  private String documentType(StagedInvoice row) {
    return switch (row.documentKind()) {
      case "EXPENSE" -> "PURCHASE_INVOICE";
      case "INCOME", "SALES_INVOICE" -> "SALES_INVOICE";
      case "CREDIT_NOTE" -> "CREDIT_NOTE";
      default ->
          throw new IllegalArgumentException(
              "Unsupported staged document type: " + row.documentKind());
    };
  }

  private Long correctionTarget(StagedInvoice row, String documentType) {
    if (!"CREDIT_NOTE".equals(documentType)) return null;
    String reference = row.correctsDocumentReference();
    if (reference == null || reference.isBlank()) {
      repository.result(
          row.profileId(),
          "invoice",
          row.id(),
          StagingReconciliationStatus.MISMATCH,
          java.util.List.of("CORRECTION_TARGET_REQUIRED"),
          "Credit note requires the corrected invoice reference before promotion");
      return null;
    }
    Long target = canonicalRepository.canonicalInvoiceId(row.profileId(), "SALE", reference.trim());
    if (target != null) return target;
    repository.result(
        row.profileId(),
        "invoice",
        row.id(),
        StagingReconciliationStatus.MISMATCH,
        java.util.List.of("CORRECTION_TARGET_NOT_FOUND"),
        "Corrected invoice is not a same-profile canonical sales invoice");
    return null;
  }

  private void updateSourceStatuses(long profileId, LocalDate taxPeriod) {
    Map<Long, Boolean> sourcesComplete = new HashMap<>();
    repository
        .invoices(profileId, taxPeriod)
        .forEach(
            row ->
                sourcesComplete.merge(
                    row.sourceId(),
                    row.status() == StagingReconciliationStatus.PROMOTED,
                    Boolean::logicalAnd));
    repository
        .bankTransactions(profileId, taxPeriod)
        .forEach(
            row ->
                sourcesComplete.merge(
                    row.sourceId(),
                    row.status() == StagingReconciliationStatus.PROMOTED,
                    Boolean::logicalAnd));
    sourcesComplete.forEach(
        (sourceId, complete) ->
            sources.status(
                sourceId,
                complete ? AccountingSourceStatus.IMPORTED : AccountingSourceStatus.REVIEW_REQUIRED,
                complete ? null : "Source has staging rows requiring reconciliation"));
  }

  public record PromotionResult(int invoices, int bankTransactions) {}
}
