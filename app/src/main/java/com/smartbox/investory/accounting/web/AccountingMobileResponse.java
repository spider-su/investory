package com.smartbox.investory.accounting.web;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Explicit versioned response contract for read-only mobile accounting clients. */
public record AccountingMobileResponse(
    YearMonth month,
    String lifecycle,
    String lifecycleLabel,
    String nextAction,
    String nextActionLabel,
    Summary summary,
    List<Issue> issues,
    Sources sources,
    String ksefStatus,
    DocumentSummary documentSummary,
    BankSummary bankSummary,
    PaymentSummary paymentSummary,
    FilingSummary filingSummary,
    ReconciliationSummary reconciliationSummary,
    List<String> allowedActions) {

  static AccountingMobileResponse from(AccountingUserApi.MonthOverview source) {
    return new AccountingMobileResponse(
        source.month(),
        source.lifecycle(),
        source.lifecycleLabel(),
        source.nextAction(),
        source.nextActionLabel(),
        Summary.from(source.summary()),
        source.issues().stream().map(Issue::from).toList(),
        Sources.from(source.sources()),
        source.ksefStatus(),
        DocumentSummary.from(source.documentSummary()),
        BankSummary.from(source.bankSummary()),
        PaymentSummary.from(source.paymentSummary()),
        FilingSummary.from(source.filingSummary()),
        ReconciliationSummary.from(source.reconciliationSummary()),
        source.allowedActions());
  }

  public record Summary(
      BigDecimal revenue,
      BigDecimal vat,
      BigDecimal ryczalt,
      BigDecimal zus,
      int documents,
      int bankTransactions,
      BigDecimal totalObligations) {
    static Summary from(AccountingUserApi.Summary source) {
      return new Summary(
          source.revenue(),
          source.vat(),
          source.ryczalt(),
          source.zus(),
          source.documents(),
          source.bankTransactions(),
          source.totalObligations());
    }
  }

  public record Sources(int evidenceCount, int imported, int reviewRequired, int failed) {
    static Sources from(AccountingUserApi.SourceSummary source) {
      return new Sources(
          source.evidenceCount(), source.imported(), source.reviewRequired(), source.failed());
    }
  }

  public record DocumentSummary(
      int salesCount, int purchaseCount, int totalCount, int reviewRequired, int failed) {
    static DocumentSummary from(AccountingUserApi.DocumentSummary source) {
      return new DocumentSummary(
          source.salesCount(),
          source.purchaseCount(),
          source.totalCount(),
          source.reviewRequired(),
          source.failed());
    }
  }

  public record BankSummary(int transactionCount, int unmatchedCount, String importStatus) {
    static BankSummary from(AccountingUserApi.BankSummary source) {
      return new BankSummary(
          source.transactionCount(), source.unmatchedCount(), source.importStatus());
    }
  }

  public record PaymentSummary(
      int expectedCount,
      int outstandingCount,
      BigDecimal totalOutstanding,
      List<Payment> payments) {
    static PaymentSummary from(AccountingUserApi.PaymentSummary source) {
      return new PaymentSummary(
          source.expectedCount(),
          source.outstandingCount(),
          source.totalOutstanding(),
          source.payments().stream().map(Payment::from).toList());
    }
  }

  public record Payment(
      String obligationType,
      BigDecimal amount,
      BigDecimal paidAmount,
      BigDecimal outstandingAmount,
      java.time.LocalDate dueDate,
      String status) {
    static Payment from(AccountingUserApi.PaymentView source) {
      BigDecimal expected = source.amount() == null ? BigDecimal.ZERO : source.amount();
      BigDecimal paid = source.paidAmount() == null ? BigDecimal.ZERO : source.paidAmount();
      return new Payment(
          source.type(),
          expected,
          paid,
          expected.subtract(paid).max(BigDecimal.ZERO),
          source.dueDate(),
          source.status());
    }
  }

  public record FilingSummary(
      String lifecycle,
      String lifecycleLabel,
      boolean ready,
      List<String> issues,
      String jpkStatus,
      String jpkGeneratedAt,
      String upoStatus,
      String upoReference,
      String upoReceivedAt) {
    static FilingSummary from(AccountingUserApi.FilingSummary source) {
      return new FilingSummary(
          source.lifecycle(),
          source.lifecycleLabel(),
          source.ready(),
          source.issues(),
          source.jpkStatus(),
          source.jpkGeneratedAt(),
          source.upoStatus(),
          source.upoReference(),
          source.upoReceivedAt());
    }
  }

  public record ReconciliationSummary(
      int rowCount, int settledCount, int mismatchCount, int missingEvidenceCount) {
    static ReconciliationSummary from(AccountingUserApi.ReconciliationSummary source) {
      return new ReconciliationSummary(
          source.rowCount(),
          source.settledCount(),
          source.mismatchCount(),
          source.missingEvidenceCount());
    }
  }

  public record Issue(
      String id,
      String code,
      String severity,
      AccountingUserApi.IssueKind kind,
      String title,
      String message,
      String sourceReference,
      Resolution resolution) {
    static Issue from(AccountingUserApi.IssueView source) {
      return new Issue(
          source.id(),
          source.code(),
          source.severity(),
          source.kind(),
          source.title(),
          source.message(),
          source.sourceReference(),
          Resolution.from(source.resolution()));
    }
  }

  public record Resolution(
      String type,
      String command,
      List<Option> options,
      String settingsPath,
      String actionLabel,
      String reason) {
    static Resolution from(AccountingUserApi.Resolution source) {
      return switch (source) {
        case AccountingUserApi.Resolution.Choice choice ->
            new Resolution(
                "CHOICE",
                choice.command(),
                choice.options().stream().map(Option::from).toList(),
                null,
                null,
                null);
        case AccountingUserApi.Resolution.Match match ->
            new Resolution(
                "MATCH",
                match.command(),
                match.candidates().stream().map(Option::from).toList(),
                null,
                null,
                null);
        case AccountingUserApi.Resolution.Setup setup ->
            new Resolution(
                "SETUP", null, List.of(), setup.settingsPath(), setup.actionLabel(), null);
        case AccountingUserApi.Resolution.None none ->
            new Resolution("NONE", null, List.of(), null, null, none.reason());
      };
    }
  }

  public record Option(String value, String label, boolean recommended) {
    static Option from(AccountingUserApi.Option source) {
      return new Option(source.value(), source.label(), source.recommended());
    }
  }

  public record Document(
      long id,
      String type,
      String counterparty,
      String documentNumber,
      LocalDate issueDate,
      LocalDate saleDate,
      String category,
      String source,
      BigDecimal amount,
      String currency,
      String status,
      String sourceType,
      String sourceTypeLabel,
      String categoryLabel,
      String importStatus,
      String reviewStatus,
      String paymentStatus,
      String documentKind,
      Long correctsDocumentId,
      String correctsDocumentReference) {
    static Document from(AccountingUserApi.DocumentView source) {
      return new Document(
          source.id(),
          type(source.direction()),
          source.counterparty(),
          source.reference(),
          source.date(),
          source.saleDate(),
          source.category(),
          source.sourceName() != null ? source.sourceName() : source.acquisitionSource(),
          source.grossAmount(),
          source.currency(),
          source.status(),
          source.acquisitionSource(),
          source.acquisitionSourceLabel(),
          source.categoryLabel(),
          source.importStatus(),
          source.reviewStatus(),
          source.paymentStatus(),
          source.documentKind(),
          source.correctsDocumentId(),
          source.correctsDocumentReference());
    }

    private static String type(String direction) {
      return "SALES".equals(direction)
          ? "SALE"
          : "PURCHASE".equals(direction) ? "PURCHASE" : direction;
    }
  }
}
