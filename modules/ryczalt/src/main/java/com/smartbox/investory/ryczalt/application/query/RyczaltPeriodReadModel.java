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
  public RyczaltPeriodReadModel {
    calculations = List.copyOf(calculations);
    obligationTotals = obligationTotals == null ? ObligationTotals.empty() : obligationTotals;
    completeness = completeness == null ? Completeness.empty() : completeness;
    allowedActions = Set.copyOf(allowedActions);
  }

  public record CalculationState(
      String type,
      CalculationStatus status,
      BigDecimal amount,
      String ruleVersion,
      String calculatorVersion,
      Instant calculatedAt) {}

  public record ObligationTotals(
      int expectedCount, int paidCount, BigDecimal paidAmount, BigDecimal outstandingAmount) {
    public static ObligationTotals empty() {
      return new ObligationTotals(0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }
  }

  public record Completeness(String status, int issueCount) {
    public static Completeness empty() {
      return new Completeness("UNKNOWN", 0);
    }
  }

  public enum PeriodAction {
    SETTLE,
    FREEZE,
    REOPEN
  }
}
