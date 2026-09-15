package com.smartbox.investory.accounting.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** User-facing accounting contract. Internal snapshots and repositories do not cross this seam. */
public interface AccountingUserApi {
  List<MonthRef> months(long profileId);

  MonthOverview overview(long profileId, YearMonth month);

  List<IssueView> issues(long profileId, YearMonth month);

  List<DocumentView> documents(long profileId, YearMonth month);

  List<BankTransactionView> bankTransactions(long profileId, YearMonth month);

  List<PaymentView> payments(long profileId, YearMonth month);

  FilingView filings(long profileId, YearMonth month);

  List<ReconciliationView> reconciliation(long profileId, YearMonth month);

  CandidateView recognize(long profileId, String filename, String contentType, byte[] content);

  CandidateView reviewSource(long profileId, String sourceReference);

  void saveReviewed(long profileId, ReviewedDocument document);

  void importBank(
      long profileId, String filename, String contentType, byte[] content, YearMonth month);

  KsefSyncResult syncKsef(long profileId, YearMonth month);

  KsefSyncResult reimportKsef(long profileId, YearMonth month);

  KsefSyncResult syncKsefSeller(long profileId, YearMonth month);

  KsefSyncResult syncKsefThirdParty(long profileId, YearMonth month);

  FilingArtifactView generateJpk(long profileId, YearMonth month);

  Optional<FilingArtifactView> filingArtifact(long profileId, YearMonth month);

  void recordConfirmation(long profileId, ConfirmationInput confirmation);

  void confirm(long profileId, YearMonth month);

  void file(long profileId, YearMonth month);

  void settle(long profileId, YearMonth month);

  void lock(long profileId, YearMonth month);

  void reopen(long profileId, YearMonth month, String reason);

  record MonthRef(YearMonth month, String label, String lifecycle, String lifecycleLabel) {}

  record MonthOverview(
      YearMonth month,
      String lifecycle,
      String lifecycleLabel,
      String nextAction,
      String nextActionLabel,
      Summary summary,
      List<IssueView> issues,
      SourceSummary sources,
      String ksefStatus,
      DocumentSummary documentSummary,
      BankSummary bankSummary,
      PaymentSummary paymentSummary,
      FilingSummary filingSummary,
      ReconciliationSummary reconciliationSummary,
      List<String> allowedActions,
      ReferenceSummary reference) {}

  /** Monthly accounting facts; totalObligations is calculated before recorded payments. */
  record Summary(
      BigDecimal revenue,
      BigDecimal vat,
      BigDecimal ryczalt,
      BigDecimal zus,
      int documents,
      int bankTransactions,
      BigDecimal totalObligations) {
    public Summary(
        BigDecimal revenue,
        BigDecimal vat,
        BigDecimal ryczalt,
        BigDecimal zus,
        int documents,
        int bankTransactions) {
      this(revenue, vat, ryczalt, zus, documents, bankTransactions, null);
    }
  }

  record SourceSummary(int evidenceCount, int imported, int reviewRequired, int failed) {}

  record DocumentSummary(
      int salesCount, int purchaseCount, int totalCount, int reviewRequired, int failed) {}

  record KsefSyncResult(
      String status,
      int received,
      int imported,
      int duplicates,
      int reviewRequired,
      int failed,
      String message) {}

  record FilingArtifactView(
      String type,
      String filename,
      String contentType,
      byte[] content,
      String sha256,
      java.time.Instant generatedAt,
      String status) {}

  record ConfirmationInput(
      YearMonth taxPeriod,
      String obligationOrArtifactType,
      String confirmationType,
      String status,
      String externalReference,
      java.time.Instant receivedAt,
      java.math.BigDecimal amount,
      String note) {}

  record BankSummary(int transactionCount, int unmatchedCount, String importStatus) {}

  /** Issued payment instructions only; excludes obligations when instructions are not issued. */
  record PaymentSummary(
      int expectedCount,
      int outstandingCount,
      BigDecimal totalOutstanding,
      List<PaymentView> payments) {
    public PaymentSummary(int expectedCount, int outstandingCount, BigDecimal totalOutstanding) {
      this(expectedCount, outstandingCount, totalOutstanding, List.of());
    }
  }

  record FilingSummary(
      String lifecycle,
      String lifecycleLabel,
      boolean ready,
      List<String> issues,
      String jpkStatus,
      String jpkGeneratedAt,
      String upoStatus,
      String upoReference,
      String upoReceivedAt) {}

  record ReconciliationSummary(
      int rowCount, int settledCount, int mismatchCount, int missingEvidenceCount) {}

  record ReferenceSummary(
      boolean available,
      BigDecimal revenue,
      BigDecimal expenses,
      BigDecimal outputVat,
      BigDecimal deductibleInputVat,
      BigDecimal vatPayable,
      BigDecimal ryczalt,
      BigDecimal zus,
      int documentCount,
      int bankCount,
      String filingStatus) {}

  /**
   * Stable user-facing issue. The legacy fields stay present while callers migrate to the explicit
   * kind and resolution contract.
   */
  record IssueView(
      String id,
      String code,
      String severity,
      IssueKind kind,
      String title,
      String message,
      String sourceReference,
      Resolution resolution) {
    public IssueView(
        String code, String severity, String title, String message, String sourceReference) {
      this(
          code + ":" + (sourceReference == null ? "account" : sourceReference),
          code,
          severity,
          IssueKind.BLOCKED,
          title,
          message,
          sourceReference,
          new Resolution.None("No safe action is available yet."));
    }
  }

  enum IssueKind {
    NEEDS_ANSWER,
    SETUP,
    BLOCKED,
    INFO
  }

  sealed interface Resolution {
    record Choice(String command, List<Option> options) implements Resolution {}

    record Match(String command, List<Option> candidates) implements Resolution {}

    record Setup(String settingsPath, String actionLabel) implements Resolution {}

    record None(String reason) implements Resolution {}
  }

  record Option(String value, String label, boolean recommended) {}

  record DocumentView(
      long id,
      String reference,
      String direction,
      LocalDate date,
      BigDecimal grossAmount,
      String currency,
      String status,
      String sourceReference,
      String counterparty,
      String category,
      LocalDate saleDate,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String acquisitionSource,
      String sourceName,
      String acquisitionSourceLabel,
      String categoryLabel,
      String importStatus,
      String reviewStatus,
      String paymentStatus) {
    public DocumentView(
        long id,
        String reference,
        String direction,
        LocalDate date,
        BigDecimal grossAmount,
        String currency,
        String status,
        String sourceReference,
        String counterparty,
        String category,
        LocalDate saleDate,
        String counterpartyTaxIdentifier,
        String counterpartyCountry,
        String acquisitionSource,
        String sourceName) {
      this(
          id,
          reference,
          direction,
          date,
          grossAmount,
          currency,
          status,
          sourceReference,
          counterparty,
          category,
          saleDate,
          counterpartyTaxIdentifier,
          counterpartyCountry,
          acquisitionSource,
          sourceName,
          null,
          null,
          null,
          null,
          null);
    }

    public DocumentView(
        long id,
        String reference,
        String direction,
        LocalDate date,
        BigDecimal grossAmount,
        String currency,
        String status,
        String sourceReference) {
      this(
          id,
          reference,
          direction,
          date,
          grossAmount,
          currency,
          status,
          sourceReference,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }
  }

  record BankTransactionView(
      long id,
      LocalDate bookingDate,
      String reference,
      BigDecimal amount,
      String currency,
      String status) {}

  record PaymentView(
      String type,
      BigDecimal amount,
      BigDecimal referenceAmount,
      BigDecimal paidAmount,
      BigDecimal difference,
      LocalDate dueDate,
      String recipient,
      String account,
      String status) {}

  record FilingView(
      String lifecycle,
      String lifecycleLabel,
      boolean confirmed,
      boolean ready,
      List<String> issues,
      String jpkStatus,
      String jpkGeneratedAt,
      String upoStatus,
      String upoReference,
      String upoReceivedAt) {}

  record ReconciliationView(
      String reference,
      String kind,
      BigDecimal expectedAmount,
      BigDecimal matchedAmount,
      String status,
      String explanation,
      String currency,
      LocalDate paymentDate) {
    public ReconciliationView(
        String reference,
        String kind,
        BigDecimal expectedAmount,
        BigDecimal matchedAmount,
        String status,
        String explanation) {
      this(reference, kind, expectedAmount, matchedAmount, status, explanation, "PLN", null);
    }
  }

  record CandidateView(
      String sourceReference,
      String documentType,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate dueDate,
      String reference,
      String seller,
      String buyer,
      String sellerNip,
      String buyerNip,
      String category,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      String note,
      String status) {}

  record ReviewedDocument(
      String sourceReference,
      String documentType,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate dueDate,
      String reference,
      String counterpartyAlias,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String category,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal vatDeductionRatio,
      String vatTreatment,
      String note,
      YearMonth taxPeriod,
      BigDecimal vatRate) {
    public ReviewedDocument(
        String sourceReference,
        String documentType,
        LocalDate issueDate,
        LocalDate saleDate,
        LocalDate dueDate,
        String reference,
        String counterpartyAlias,
        String counterpartyTaxIdentifier,
        String counterpartyCountry,
        String category,
        String currency,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount,
        BigDecimal vatDeductionRatio,
        String vatTreatment,
        String note,
        YearMonth taxPeriod) {
      this(
          sourceReference,
          documentType,
          issueDate,
          saleDate,
          dueDate,
          reference,
          counterpartyAlias,
          counterpartyTaxIdentifier,
          counterpartyCountry,
          category,
          currency,
          netAmount,
          vatAmount,
          grossAmount,
          vatDeductionRatio,
          vatTreatment,
          note,
          taxPeriod,
          null);
    }
  }
}
