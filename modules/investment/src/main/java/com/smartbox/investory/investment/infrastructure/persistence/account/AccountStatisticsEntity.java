package com.smartbox.investory.investment.infrastructure.persistence.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
@EqualsAndHashCode(of = "accountId")
@Table(name = "app_v_account_statistics")
public class AccountStatisticsEntity {

  @Id
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "valuation_currency")
  private String valuationCurrency;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_deposit", nullable = false, precision = 20, scale = 8)
  private BigDecimal totalDeposit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_withdrawal", nullable = false, precision = 20, scale = 8)
  private BigDecimal totalWithdrawal;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "net_deposit", nullable = false, precision = 20, scale = 8)
  private BigDecimal netDeposit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "account_net_deposit", nullable = false, precision = 20, scale = 8)
  private BigDecimal accountNetDeposit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "cash_balance", nullable = false, precision = 20, scale = 8)
  private BigDecimal cashBalance;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "market_value", nullable = false, precision = 20, scale = 8)
  private BigDecimal marketValue;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "equity", nullable = false, precision = 20, scale = 8)
  private BigDecimal equity;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "cost_base", nullable = false, precision = 20, scale = 8)
  private BigDecimal costBase;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "realized_profit", nullable = false, precision = 20, scale = 8)
  private BigDecimal realizedProfit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "unrealized_profit", nullable = false, precision = 20, scale = 8)
  private BigDecimal unrealizedProfit;

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

  @Column(name = "activity_count", nullable = false)
  private Integer activityCount;

  @Column(name = "first_activity_at")
  private ZonedDateTime firstActivityAt;

  @Column(name = "last_activity_at")
  private ZonedDateTime lastActivityAt;

  @Column(name = "latest_snapshot_date")
  private LocalDate latestSnapshotDate;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "latest_return_pct", precision = 20, scale = 8)
  private BigDecimal latestReturnPct;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;

  public BigDecimal getTotalDeposit() {
    return totalDeposit;
  }

  public void setTotalDeposit(BigDecimal totalDeposit) {
    this.totalDeposit = scaleDecimal(totalDeposit);
  }

  public BigDecimal getTotalWithdrawal() {
    return totalWithdrawal;
  }

  public void setTotalWithdrawal(BigDecimal totalWithdrawal) {
    this.totalWithdrawal = scaleDecimal(totalWithdrawal);
  }

  public BigDecimal getNetDeposit() {
    return netDeposit;
  }

  public void setNetDeposit(BigDecimal netDeposit) {
    this.netDeposit = scaleDecimal(netDeposit);
  }

  public BigDecimal getAccountNetDeposit() {
    return accountNetDeposit;
  }

  public void setAccountNetDeposit(BigDecimal accountNetDeposit) {
    this.accountNetDeposit = scaleDecimal(accountNetDeposit);
  }

  public BigDecimal getCashBalance() {
    return cashBalance;
  }

  public void setCashBalance(BigDecimal cashBalance) {
    this.cashBalance = scaleDecimal(cashBalance);
  }

  public BigDecimal getMarketValue() {
    return marketValue;
  }

  public void setMarketValue(BigDecimal marketValue) {
    this.marketValue = scaleDecimal(marketValue);
  }

  public BigDecimal getEquity() {
    return equity;
  }

  public void setEquity(BigDecimal equity) {
    this.equity = scaleDecimal(equity);
  }

  public BigDecimal getCostBase() {
    return costBase;
  }

  public void setCostBase(BigDecimal costBase) {
    this.costBase = scaleDecimal(costBase);
  }

  public BigDecimal getRealizedProfit() {
    return realizedProfit;
  }

  public void setRealizedProfit(BigDecimal realizedProfit) {
    this.realizedProfit = scaleDecimal(realizedProfit);
  }

  public BigDecimal getUnrealizedProfit() {
    return unrealizedProfit;
  }

  public void setUnrealizedProfit(BigDecimal unrealizedProfit) {
    this.unrealizedProfit = scaleDecimal(unrealizedProfit);
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

  public BigDecimal getLatestReturnPct() {
    return latestReturnPct;
  }

  public void setLatestReturnPct(BigDecimal latestReturnPct) {
    this.latestReturnPct = scaleDecimal(latestReturnPct);
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
