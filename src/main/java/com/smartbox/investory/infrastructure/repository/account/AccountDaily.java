package com.smartbox.investory.infrastructure.repository.account;

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
public class AccountDaily {

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

  public Double getCashBalance() {
    return asDouble(cashBalance);
  }

  public BigDecimal getCashBalanceValue() {
    return cashBalance;
  }

  public void setCashBalance(BigDecimal cashBalance) {
    this.cashBalance = scaleDecimal(cashBalance);
  }

  public void setCashBalance(Double cashBalance) {
    this.cashBalance = scaleDecimal(cashBalance);
  }

  public void setCashBalance(double cashBalance) {
    setCashBalance(Double.valueOf(cashBalance));
  }

  public Double getMarketValue() {
    return asDouble(marketValue);
  }

  public BigDecimal getMarketValueValue() {
    return marketValue;
  }

  public void setMarketValue(BigDecimal marketValue) {
    this.marketValue = scaleDecimal(marketValue);
  }

  public void setMarketValue(Double marketValue) {
    this.marketValue = scaleDecimal(marketValue);
  }

  public void setMarketValue(double marketValue) {
    setMarketValue(Double.valueOf(marketValue));
  }

  public Double getEquity() {
    return asDouble(equity);
  }

  public BigDecimal getEquityValue() {
    return equity;
  }

  public void setEquity(BigDecimal equity) {
    this.equity = scaleDecimal(equity);
  }

  public void setEquity(Double equity) {
    this.equity = scaleDecimal(equity);
  }

  public void setEquity(double equity) {
    setEquity(Double.valueOf(equity));
  }

  public Double getUnrealizedProfit() {
    return asDouble(unrealizedProfit);
  }

  public BigDecimal getUnrealizedProfitValue() {
    return unrealizedProfit;
  }

  public void setUnrealizedProfit(BigDecimal unrealizedProfit) {
    this.unrealizedProfit = scaleDecimal(unrealizedProfit);
  }

  public void setUnrealizedProfit(Double unrealizedProfit) {
    this.unrealizedProfit = scaleDecimal(unrealizedProfit);
  }

  public void setUnrealizedProfit(double unrealizedProfit) {
    setUnrealizedProfit(Double.valueOf(unrealizedProfit));
  }

  public Double getCostBase() {
    return asDouble(costBase);
  }

  public BigDecimal getCostBaseValue() {
    return costBase;
  }

  public void setCostBase(BigDecimal costBase) {
    this.costBase = scaleDecimal(costBase);
  }

  public void setCostBase(Double costBase) {
    this.costBase = scaleDecimal(costBase);
  }

  public void setCostBase(double costBase) {
    setCostBase(Double.valueOf(costBase));
  }

  public Double getRealizedProfit() {
    return asDouble(realizedProfit);
  }

  public BigDecimal getRealizedProfitValue() {
    return realizedProfit;
  }

  public void setRealizedProfit(BigDecimal realizedProfit) {
    this.realizedProfit = scaleDecimal(realizedProfit);
  }

  public void setRealizedProfit(Double realizedProfit) {
    this.realizedProfit = scaleDecimal(realizedProfit);
  }

  public void setRealizedProfit(double realizedProfit) {
    setRealizedProfit(Double.valueOf(realizedProfit));
  }

  public Double getDividends() {
    return asDouble(dividends);
  }

  public BigDecimal getDividendsValue() {
    return dividends;
  }

  public void setDividends(BigDecimal dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public void setDividends(Double dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public void setDividends(double dividends) {
    setDividends(Double.valueOf(dividends));
  }

  public Double getInterest() {
    return asDouble(interest);
  }

  public BigDecimal getInterestValue() {
    return interest;
  }

  public void setInterest(BigDecimal interest) {
    this.interest = scaleDecimal(interest);
  }

  public void setInterest(Double interest) {
    this.interest = scaleDecimal(interest);
  }

  public void setInterest(double interest) {
    setInterest(Double.valueOf(interest));
  }

  public Double getFees() {
    return asDouble(fees);
  }

  public BigDecimal getFeesValue() {
    return fees;
  }

  public void setFees(BigDecimal fees) {
    this.fees = scaleDecimal(fees);
  }

  public void setFees(Double fees) {
    this.fees = scaleDecimal(fees);
  }

  public void setFees(double fees) {
    setFees(Double.valueOf(fees));
  }

  public Double getTaxes() {
    return asDouble(taxes);
  }

  public BigDecimal getTaxesValue() {
    return taxes;
  }

  public void setTaxes(BigDecimal taxes) {
    this.taxes = scaleDecimal(taxes);
  }

  public void setTaxes(Double taxes) {
    this.taxes = scaleDecimal(taxes);
  }

  public void setTaxes(double taxes) {
    setTaxes(Double.valueOf(taxes));
  }

  public Double getDeposits() {
    return asDouble(deposits);
  }

  public BigDecimal getDepositsValue() {
    return deposits;
  }

  public void setDeposits(BigDecimal deposits) {
    this.deposits = scaleDecimal(deposits);
  }

  public void setDeposits(Double deposits) {
    this.deposits = scaleDecimal(deposits);
  }

  public void setDeposits(double deposits) {
    setDeposits(Double.valueOf(deposits));
  }

  public Double getWithdrawals() {
    return asDouble(withdrawals);
  }

  public BigDecimal getWithdrawalsValue() {
    return withdrawals;
  }

  public void setWithdrawals(BigDecimal withdrawals) {
    this.withdrawals = scaleDecimal(withdrawals);
  }

  public void setWithdrawals(Double withdrawals) {
    this.withdrawals = scaleDecimal(withdrawals);
  }

  public void setWithdrawals(double withdrawals) {
    setWithdrawals(Double.valueOf(withdrawals));
  }

  public Double getDailyProfitAmount() {
    return asDouble(dailyProfitAmount);
  }

  public BigDecimal getDailyProfitAmountValue() {
    return dailyProfitAmount;
  }

  public void setDailyProfitAmount(BigDecimal dailyProfitAmount) {
    this.dailyProfitAmount = scaleDecimal(dailyProfitAmount);
  }

  public void setDailyProfitAmount(Double dailyProfitAmount) {
    this.dailyProfitAmount = scaleDecimal(dailyProfitAmount);
  }

  public void setDailyProfitAmount(double dailyProfitAmount) {
    setDailyProfitAmount(Double.valueOf(dailyProfitAmount));
  }

  public Double getDailyReturn() {
    return asDouble(dailyReturn);
  }

  public BigDecimal getDailyReturnValue() {
    return dailyReturn;
  }

  public void setDailyReturn(BigDecimal dailyReturn) {
    this.dailyReturn = scaleDecimal(dailyReturn);
  }

  public void setDailyReturn(Double dailyReturn) {
    this.dailyReturn = scaleDecimal(dailyReturn);
  }

  public void setDailyReturn(double dailyReturn) {
    setDailyReturn(Double.valueOf(dailyReturn));
  }

  public Double getPortfolioWeight() {
    return asDouble(portfolioWeight);
  }

  public BigDecimal getPortfolioWeightValue() {
    return portfolioWeight;
  }

  public void setPortfolioWeight(BigDecimal portfolioWeight) {
    this.portfolioWeight = scaleDecimal(portfolioWeight);
  }

  public void setPortfolioWeight(Double portfolioWeight) {
    this.portfolioWeight = scaleDecimal(portfolioWeight);
  }

  public void setPortfolioWeight(double portfolioWeight) {
    setPortfolioWeight(Double.valueOf(portfolioWeight));
  }

  private static Double asDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  private static BigDecimal scaleDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
  }

  private static BigDecimal scaleDecimal(Double value) {
    return value == null ? null : scaleDecimal(BigDecimal.valueOf(value));
  }
}
