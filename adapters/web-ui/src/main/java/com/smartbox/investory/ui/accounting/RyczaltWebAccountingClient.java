package com.smartbox.investory.ui.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Factual Web seam for the native Ryczalt accounting boundary. */
public interface RyczaltWebAccountingClient {
  List<Month> periods(long profileId);

  Period period(long profileId, YearMonth month);

  Reference reference(long profileId, YearMonth month);

  List<Invoice> invoices(long profileId, YearMonth month);

  List<Transaction> transactions(long profileId, YearMonth month);

  List<Obligation> obligations(long profileId, YearMonth month);

  List<Issue> issues(long profileId, YearMonth month);

  List<PaymentHistory> paymentHistory(long profileId, YearMonth from, YearMonth to, String type);

  List<Counterparty> counterparties(long profileId);

  Counterparty counterparty(long profileId, long id);

  List<Rule> rules(long profileId, long counterpartyId);

  void alias(long profileId, long counterpartyId, String alias);

  void settle(long profileId, YearMonth month);

  void freeze(long profileId, YearMonth month, String reason);

  void reopen(long profileId, YearMonth month, String reason);

  record Month(YearMonth month, String status) {}

  record Period(
      YearMonth month,
      String status,
      List<Calculation> calculations,
      Summary summary,
      Audit audit,
      Documents documents,
      Settlement settlement,
      Reconciliation reconciliation,
      Completeness completeness,
      List<String> allowedActions) {}

  record Reference(
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

  record Calculation(String type, String status, BigDecimal amount) {}

  record Summary(BigDecimal revenue, BigDecimal ryczalt, BigDecimal vat, BigDecimal zus) {}

  record Audit(
      BigDecimal revenue,
      BigDecimal socialDeduction,
      BigDecimal healthDeduction,
      BigDecimal otherDeduction,
      BigDecimal taxableBase,
      BigDecimal cumulativeTax,
      BigDecimal monthlyAdvance,
      BigDecimal outputVat,
      BigDecimal inputVat,
      BigDecimal vatAdjustments,
      BigDecimal finalPayable) {}

  record Documents(int invoices, int transactions) {}

  record Settlement(
      int expected,
      int paid,
      int outstanding,
      BigDecimal totalExpected,
      BigDecimal totalPaid,
      BigDecimal totalOutstanding,
      boolean fullySettled) {}

  record Reconciliation(int rows, int settled, int mismatches, int missingEvidence) {}

  record Completeness(String status, int blockingIssues) {}

  record Invoice(
      long id,
      String direction,
      String reference,
      LocalDate issueDate,
      LocalDate accountingDate,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      String currency,
      String approvalStatus,
      String approvalMethod,
      String paymentVerificationPolicy,
      String paymentStatus,
      String counterparty) {}

  record Transaction(
      long id,
      LocalDate bookingDate,
      BigDecimal amount,
      String currency,
      String reference,
      String counterparty,
      String description,
      BigDecimal matchedAmount) {}

  record Obligation(
      long id,
      String type,
      BigDecimal expected,
      BigDecimal paid,
      BigDecimal outstanding,
      String currency,
      LocalDate dueDate,
      String status) {}

  record PaymentHistory(
      String type,
      YearMonth period,
      BigDecimal expected,
      BigDecimal paid,
      BigDecimal outstanding,
      LocalDate dueDate,
      LocalDate paymentDate,
      String status) {}

  record Issue(
      String id,
      String code,
      String severity,
      String kind,
      String title,
      String message,
      String sourceReference) {}

  record Counterparty(
      long id,
      String legalName,
      String alias,
      String displayName,
      String taxIdentifier,
      String country,
      long ruleCount,
      long invoiceCount) {}

  record Rule(
      long id,
      String name,
      String sourceType,
      String documentType,
      String serviceKey,
      String classification,
      String vatTreatment,
      String vatDeductionRatio,
      String ryczaltRate,
      boolean autoApprove,
      String paymentVerificationPolicy) {}
}
