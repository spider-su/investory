package com.example.demo.infrastructure.repository.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(AccountMonthlyPerformanceId.class)
@EqualsAndHashCode(of = {"accountId", "month"})
@Table(name = "account_monthly_mv")
public class AccountMonthlyPerformance {

  @Id
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Id
  @Column(name = "month", nullable = false)
  private LocalDate month;

  @Column(name = "first_date", nullable = false)
  private LocalDate firstDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "opening_equity", nullable = false)
  private Double startEquity;

  @Column(name = "closing_equity", nullable = false)
  private Double endEquity;

  @Column(name = "valuation_currency", nullable = false)
  private String valuationCurrency;

  @Column(name = "deposits", nullable = false)
  private Double depositFlow;

  @Column(name = "withdrawals", nullable = false)
  private Double withdrawalFlow;

  @Column(name = "dividends", nullable = false)
  private Double dividends;

  @Column(name = "interest", nullable = false)
  private Double interest;

  @Column(name = "fees", nullable = false)
  private Double fees;

  @Column(name = "taxes", nullable = false)
  private Double taxes;

  @Column(name = "realized_profit", nullable = false)
  private Double realizedProfit;

  @Column(name = "total_profit", nullable = false)
  private Double profit;

  @Column(name = "compounded_monthly_return")
  private Double returnPct;

  @Column(name = "updated_at", nullable = false)
  private java.time.ZonedDateTime updatedAt;

  public Double getNetCashflow() {
    return (depositFlow == null ? 0.0 : depositFlow) - (withdrawalFlow == null ? 0.0 : withdrawalFlow);
  }

  public AccountMonthlyPerformance(
      String ignoredId,
      Long accountId,
      LocalDate month,
      LocalDate endDate,
      double startEquity,
      double endEquity,
      double depositFlow,
      double withdrawalFlow,
      double netCashflow,
      double profit,
      double returnPct,
      java.time.ZonedDateTime updatedAt) {
    this.accountId = accountId;
    this.month = month;
    this.firstDate = month;
    this.endDate = endDate;
    this.startEquity = startEquity;
    this.endEquity = endEquity;
    this.valuationCurrency = "USD";
    this.depositFlow = depositFlow;
    this.withdrawalFlow = withdrawalFlow;
    this.dividends = 0.0;
    this.interest = 0.0;
    this.fees = 0.0;
    this.taxes = 0.0;
    this.realizedProfit = netCashflow;
    this.profit = profit;
    this.returnPct = returnPct;
    this.updatedAt = updatedAt;
  }
}
