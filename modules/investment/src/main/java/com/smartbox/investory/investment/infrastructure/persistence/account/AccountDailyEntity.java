package com.smartbox.investory.investment.infrastructure.persistence.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
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

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "account_daily")
public class AccountDailyEntity {

  @Id
  @SequenceGenerator(
      name = "account_daily_id_seq",
      sequenceName = "account_daily_id_seq",
      allocationSize = 50)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_daily_id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "snapshot_date", nullable = false)
  private LocalDate date;

  @Column(name = "valuation_currency", nullable = false)
  private String valuationCurrency;

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
  @Column(name = "unrealized_profit", nullable = false, precision = 20, scale = 8)
  private BigDecimal unrealizedProfit;

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
  @Column(name = "deposits", nullable = false, precision = 20, scale = 8)
  private BigDecimal deposits;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "withdrawals", nullable = false, precision = 20, scale = 8)
  private BigDecimal withdrawals;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "daily_profit_amount", nullable = false, precision = 20, scale = 8)
  private BigDecimal dailyProfitAmount;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "daily_return_pct", precision = 20, scale = 8)
  private BigDecimal dailyReturn;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "portfolio_weight", precision = 20, scale = 8)
  private BigDecimal portfolioWeight;

  @Column(name = "created_at", nullable = false)
  private ZonedDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;

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

  public BigDecimal getUnrealizedProfit() {
    return unrealizedProfit;
  }

  public void setUnrealizedProfit(BigDecimal unrealizedProfit) {
    this.unrealizedProfit = scaleDecimal(unrealizedProfit);
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

  public BigDecimal getDeposits() {
    return deposits;
  }

  public void setDeposits(BigDecimal deposits) {
    this.deposits = scaleDecimal(deposits);
  }

  public BigDecimal getWithdrawals() {
    return withdrawals;
  }

  public void setWithdrawals(BigDecimal withdrawals) {
    this.withdrawals = scaleDecimal(withdrawals);
  }

  public BigDecimal getDailyProfitAmount() {
    return dailyProfitAmount;
  }

  public void setDailyProfitAmount(BigDecimal dailyProfitAmount) {
    this.dailyProfitAmount = scaleDecimal(dailyProfitAmount);
  }

  public BigDecimal getDailyReturn() {
    return dailyReturn;
  }

  public void setDailyReturn(BigDecimal dailyReturn) {
    this.dailyReturn = scaleDecimal(dailyReturn);
  }

  public BigDecimal getPortfolioWeight() {
    return portfolioWeight;
  }

  public void setPortfolioWeight(BigDecimal portfolioWeight) {
    this.portfolioWeight = scaleDecimal(portfolioWeight);
  }

  private static Double asDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  private static BigDecimal scaleDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
  }
}
