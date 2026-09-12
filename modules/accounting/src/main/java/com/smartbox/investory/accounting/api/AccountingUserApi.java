package com.smartbox.investory.accounting.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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

  void saveReviewed(long profileId, ReviewedDocument document);

  void importBank(
      long profileId, String filename, String contentType, byte[] content, YearMonth month);

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
      SourceSummary sources) {}

  record Summary(
      BigDecimal revenue,
      BigDecimal vat,
      BigDecimal ryczalt,
      BigDecimal zus,
      int documents,
      int bankTransactions) {}

  record SourceSummary(int imported, int reviewRequired, int failed) {}

  record IssueView(
      String code, String severity, String title, String message, String sourceReference) {}

  record DocumentView(
      long id,
      String reference,
      String direction,
      LocalDate date,
      BigDecimal grossAmount,
      String currency,
      String status,
      String sourceReference) {}

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
      LocalDate dueDate,
      String recipient,
      String account,
      String status) {}

  record FilingView(
      String lifecycle,
      String lifecycleLabel,
      boolean confirmed,
      boolean ready,
      List<String> issues) {}

  record ReconciliationView(
      String reference,
      String kind,
      BigDecimal expectedAmount,
      BigDecimal matchedAmount,
      String status,
      String explanation) {}

  record CandidateView(
      String sourceReference,
      String documentType,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate dueDate,
      String reference,
      String seller,
      String buyer,
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
      String category,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal vatDeductionRatio,
      String note,
      YearMonth taxPeriod) {}
}
