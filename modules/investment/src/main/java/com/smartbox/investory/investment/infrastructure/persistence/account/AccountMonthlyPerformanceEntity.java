package com.smartbox.investory.investment.infrastructure.persistence.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
@IdClass(AccountMonthlyPerformanceId.class)
@EqualsAndHashCode(of = {"accountId", "month"})
@Table(name = "app_v_account_monthly_benchmark")
public class AccountMonthlyPerformanceEntity {

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

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "opening_equity", nullable = false, precision = 20, scale = 8)
  private BigDecimal startEquity;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "closing_equity", nullable = false, precision = 20, scale = 8)
  private BigDecimal endEquity;

  @Column(name = "valuation_currency", nullable = false)
  private String valuationCurrency;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "deposits", nullable = false, precision = 20, scale = 8)
  private BigDecimal depositFlow;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "withdrawals", nullable = false, precision = 20, scale = 8)
  private BigDecimal withdrawalFlow;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "dividends", nullable = false, precision = 20, scale = 8)
  private BigDecimal dividends;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "interest", nullable = false, precision = 20, scale = 8)
  private BigDecimal interest;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "fees", nullable = false, precision = 20, scale = 8)
  private BigDecimal fees;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "taxes", nullable = false, precision = 20, scale = 8)
  private BigDecimal taxes;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "realized_profit", nullable = false, precision = 20, scale = 8)
  private BigDecimal realizedProfit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_profit", precision = 20, scale = 8)
  private BigDecimal profit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "compounded_monthly_return", precision = 20, scale = 8)
  private BigDecimal returnPct;

  @Column(name = "updated_at", nullable = false)
  private java.time.ZonedDateTime updatedAt;

  public Double getNetCashflow() {
    BigDecimal deposits = depositFlow == null ? BigDecimal.ZERO : depositFlow;
    BigDecimal withdrawals = withdrawalFlow == null ? BigDecimal.ZERO : withdrawalFlow;
    return deposits.subtract(withdrawals).doubleValue();
  }

  public AccountMonthlyPerformanceEntity(
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
    this.startEquity = scaleDecimal(startEquity);
    this.endEquity = scaleDecimal(endEquity);
    this.valuationCurrency = "USD";
    this.depositFlow = scaleDecimal(depositFlow);
    this.withdrawalFlow = scaleDecimal(withdrawalFlow);
    this.dividends = scaleDecimal(0.0);
    this.interest = scaleDecimal(0.0);
    this.fees = scaleDecimal(0.0);
    this.taxes = scaleDecimal(0.0);
    this.realizedProfit = scaleDecimal(netCashflow);
    this.profit = scaleDecimal(profit);
    this.returnPct = scaleDecimal(returnPct);
    this.updatedAt = updatedAt;
  }

  public BigDecimal getStartEquity() {
    return startEquity;
  }

  public void setStartEquity(BigDecimal startEquity) {
    this.startEquity = scaleDecimal(startEquity);
  }

  public BigDecimal getEndEquity() {
    return endEquity;
  }

  public void setEndEquity(BigDecimal endEquity) {
    this.endEquity = scaleDecimal(endEquity);
  }

  public BigDecimal getDepositFlow() {
    return depositFlow;
  }

  public void setDepositFlow(BigDecimal depositFlow) {
    this.depositFlow = scaleDecimal(depositFlow);
  }

  public BigDecimal getWithdrawalFlow() {
    return withdrawalFlow;
  }

  public void setWithdrawalFlow(BigDecimal withdrawalFlow) {
    this.withdrawalFlow = scaleDecimal(withdrawalFlow);
  }

  public BigDecimal getDividends() {
    return dividends;
  }

  public void setDividends(BigDecimal dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public BigDecimal getInterest() {
    return interest;
  }

  public void setInterest(BigDecimal interest) {
    this.interest = scaleDecimal(interest);
  }

  public BigDecimal getFees() {
    return fees;
  }

  public void setFees(BigDecimal fees) {
    this.fees = scaleDecimal(fees);
  }

  public BigDecimal getTaxes() {
    return taxes;
  }

  public void setTaxes(BigDecimal taxes) {
    this.taxes = scaleDecimal(taxes);
  }

  public BigDecimal getRealizedProfit() {
    return realizedProfit;
  }

  public void setRealizedProfit(BigDecimal realizedProfit) {
    this.realizedProfit = scaleDecimal(realizedProfit);
  }

  public BigDecimal getProfit() {
    return profit;
  }

  public void setProfit(BigDecimal profit) {
    this.profit = scaleDecimal(profit);
  }

  public BigDecimal getReturnPct() {
    return returnPct;
  }

  public void setReturnPct(BigDecimal returnPct) {
    this.returnPct = scaleDecimal(returnPct);
  }

  private static BigDecimal scaleDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
  }

  private static BigDecimal scaleDecimal(double value) {
    return scaleDecimal(BigDecimal.valueOf(value));
  }

  private static Double asDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
