package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.CalculationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

public record RyczaltPeriodReadModel(
    YearMonth month,
    PeriodStatus periodStatus,
    List<CalculationState> calculations,
    Summary summary,
    Audit audit,
    Documents documents,
    SettlementSummary settlement,
    ReconciliationSummary reconciliation,
    Completeness completeness,
    Set<PeriodAction> allowedActions) {
  public RyczaltPeriodReadModel {
    calculations = List.copyOf(calculations);
    summary = summary == null ? Summary.empty() : summary;
    audit = audit == null ? Audit.empty() : audit;
    documents = documents == null ? new Documents(0, 0) : documents;
    settlement = settlement == null ? SettlementSummary.empty() : settlement;
    reconciliation = reconciliation == null ? ReconciliationSummary.empty() : reconciliation;
    completeness = completeness == null ? Completeness.empty() : completeness;
    allowedActions = Set.copyOf(allowedActions);
  }

  public RyczaltPeriodReadModel(
      YearMonth month,
      PeriodStatus periodStatus,
      List<CalculationState> calculations,
      BigDecimal revenue,
      BigDecimal ryczaltAmount,
      BigDecimal vatAmount,
      BigDecimal zusAmount,
      BigDecimal totalObligations,
      int invoiceCount,
      int transactionCount,
      ObligationTotals obligationTotals,
      Completeness completeness,
      Set<PeriodAction> allowedActions) {
    this(
        month,
        periodStatus,
        calculations,
        new Summary(revenue, ryczaltAmount, vatAmount, zusAmount),
        Audit.empty(),
        new Documents(invoiceCount, transactionCount),
        new SettlementSummary(
            obligationTotals.expectedCount(),
            obligationTotals.paidCount(),
            obligationTotals.expectedCount() - obligationTotals.paidCount(),
            totalObligations,
            obligationTotals.paidAmount(),
            obligationTotals.outstandingAmount(),
            obligationTotals.outstandingAmount().signum() == 0),
        ReconciliationSummary.empty(),
        completeness,
        allowedActions);
  }

  public BigDecimal revenue() {
    return summary.revenue();
  }

  public BigDecimal ryczaltAmount() {
    return summary.ryczalt();
  }

  public BigDecimal vatAmount() {
    return summary.vat();
  }

  public BigDecimal zusAmount() {
    return summary.zus();
  }

  public BigDecimal totalObligations() {
    return settlement.totalExpected();
  }

  public int invoiceCount() {
    return documents.invoiceCount();
  }

  public int transactionCount() {
    return documents.transactionCount();
  }

  public ObligationTotals obligationTotals() {
    return new ObligationTotals(
        settlement.expectedCount(),
        settlement.paidCount(),
        settlement.totalPaid(),
        settlement.totalOutstanding());
  }

  public record CalculationState(
      String type,
      CalculationStatus status,
      BigDecimal amount,
      String ruleVersion,
      String calculatorVersion,
      Instant calculatedAt) {}

  public record Summary(BigDecimal revenue, BigDecimal ryczalt, BigDecimal vat, BigDecimal zus) {
    public static Summary empty() {
      return new Summary(BigDecimal.ZERO, null, null, null);
    }
  }

  public record Audit(
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
      BigDecimal finalPayable) {
    public Audit {
      revenue = value(revenue);
      socialDeduction = value(socialDeduction);
      healthDeduction = value(healthDeduction);
      otherDeduction = value(otherDeduction);
      taxableBase = value(taxableBase);
      cumulativeTax = value(cumulativeTax);
      monthlyAdvance = value(monthlyAdvance);
      outputVat = value(outputVat);
      inputVat = value(inputVat);
      vatAdjustments = value(vatAdjustments);
      finalPayable = value(finalPayable);
    }

    public static Audit empty() {
      return new Audit(
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO);
    }

    private static BigDecimal value(BigDecimal value) {
      return value == null ? BigDecimal.ZERO : value;
    }
  }

  public record Documents(int invoiceCount, int transactionCount) {}

  public record SettlementSummary(
      int expectedCount,
      int paidCount,
      int outstandingCount,
      BigDecimal totalExpected,
      BigDecimal totalPaid,
      BigDecimal totalOutstanding,
      boolean fullySettled) {
    public static SettlementSummary empty() {
      return new SettlementSummary(
          0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
    }
  }

  public record ReconciliationSummary(
      int rowCount, int settledCount, int mismatchCount, int missingEvidenceCount) {
    public static ReconciliationSummary empty() {
      return new ReconciliationSummary(0, 0, 0, 0);
    }
  }

  public record ObligationTotals(
      int expectedCount, int paidCount, BigDecimal paidAmount, BigDecimal outstandingAmount) {
    public static ObligationTotals empty() {
      return new ObligationTotals(0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }
  }

  public record Completeness(String status, int blockingIssueCount) {
    public int issueCount() {
      return blockingIssueCount;
    }

    public static Completeness empty() {
      return new Completeness("UNKNOWN", 0);
    }
  }

  public enum PeriodAction {
    FREEZE,
    REOPEN
  }
}
