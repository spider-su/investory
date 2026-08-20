package com.smartbox.investory.infrastructure.repository.portfolio;

import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
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
@IdClass(PortfolioMonthlyPerformanceId.class)
@EqualsAndHashCode(of = {"portfolioId", "month"})
@Table(name = "app_v_portfolio_monthly")
public class PortfolioMonthlyPerformance {

  @Id
  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "base_currency", nullable = false)
  private CurrencyType baseCurrency;

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
  @Column(name = "total_profit", nullable = false, precision = 20, scale = 8)
  private BigDecimal profit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "compounded_monthly_return", precision = 20, scale = 8)
  private BigDecimal returnPct;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;

  public Double getNetCashflow() {
    BigDecimal deposits = depositFlow == null ? BigDecimal.ZERO : depositFlow;
    BigDecimal withdrawals = withdrawalFlow == null ? BigDecimal.ZERO : withdrawalFlow;
    return deposits.subtract(withdrawals).doubleValue();
  }

  public PortfolioMonthlyPerformance(
      Long portfolioId,
      LocalDate month,
      LocalDate firstDate,
      LocalDate endDate,
      double startEquity,
      double endEquity,
      CurrencyType baseCurrency,
      double depositFlow,
      double withdrawalFlow,
      double dividends,
      double interest,
      double fees,
      double taxes,
      double realizedProfit,
      double profit,
      Double returnPct,
      ZonedDateTime updatedAt) {
    this.portfolioId = portfolioId;
    this.month = month;
    this.firstDate = firstDate;
    this.endDate = endDate;
    this.startEquity = scaleDecimal(startEquity);
    this.endEquity = scaleDecimal(endEquity);
    this.baseCurrency = baseCurrency;
    this.depositFlow = scaleDecimal(depositFlow);
    this.withdrawalFlow = scaleDecimal(withdrawalFlow);
    this.dividends = scaleDecimal(dividends);
    this.interest = scaleDecimal(interest);
    this.fees = scaleDecimal(fees);
    this.taxes = scaleDecimal(taxes);
    this.realizedProfit = scaleDecimal(realizedProfit);
    this.profit = scaleDecimal(profit);
    this.returnPct = scaleDecimal(returnPct);
    this.updatedAt = updatedAt;
  }

  public Double getStartEquity() {
    return asDouble(startEquity);
  }

  public void setStartEquity(BigDecimal startEquity) {
    this.startEquity = scaleDecimal(startEquity);
  }

  public void setStartEquity(Double startEquity) {
    this.startEquity = scaleDecimal(startEquity);
  }

  public void setStartEquity(double startEquity) {
    this.startEquity = scaleDecimal(startEquity);
  }

  public Double getEndEquity() {
    return asDouble(endEquity);
  }

  public void setEndEquity(BigDecimal endEquity) {
    this.endEquity = scaleDecimal(endEquity);
  }

  public void setEndEquity(Double endEquity) {
    this.endEquity = scaleDecimal(endEquity);
  }

  public void setEndEquity(double endEquity) {
    this.endEquity = scaleDecimal(endEquity);
  }

  public Double getDepositFlow() {
    return asDouble(depositFlow);
  }

  public void setDepositFlow(BigDecimal depositFlow) {
    this.depositFlow = scaleDecimal(depositFlow);
  }

  public void setDepositFlow(Double depositFlow) {
    this.depositFlow = scaleDecimal(depositFlow);
  }

  public void setDepositFlow(double depositFlow) {
    this.depositFlow = scaleDecimal(depositFlow);
  }

  public Double getWithdrawalFlow() {
    return asDouble(withdrawalFlow);
  }

  public void setWithdrawalFlow(BigDecimal withdrawalFlow) {
    this.withdrawalFlow = scaleDecimal(withdrawalFlow);
  }

  public void setWithdrawalFlow(Double withdrawalFlow) {
    this.withdrawalFlow = scaleDecimal(withdrawalFlow);
  }

  public void setWithdrawalFlow(double withdrawalFlow) {
    this.withdrawalFlow = scaleDecimal(withdrawalFlow);
  }

  public Double getDividends() {
    return asDouble(dividends);
  }

  public void setDividends(BigDecimal dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public void setDividends(Double dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public void setDividends(double dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public Double getInterest() {
    return asDouble(interest);
  }

  public void setInterest(BigDecimal interest) {
    this.interest = scaleDecimal(interest);
  }

  public void setInterest(Double interest) {
    this.interest = scaleDecimal(interest);
  }

  public void setInterest(double interest) {
    this.interest = scaleDecimal(interest);
  }

  public Double getFees() {
    return asDouble(fees);
  }

  public void setFees(BigDecimal fees) {
    this.fees = scaleDecimal(fees);
  }

  public void setFees(Double fees) {
    this.fees = scaleDecimal(fees);
  }

  public void setFees(double fees) {
    this.fees = scaleDecimal(fees);
  }

  public Double getTaxes() {
    return asDouble(taxes);
  }

  public void setTaxes(BigDecimal taxes) {
    this.taxes = scaleDecimal(taxes);
  }

  public void setTaxes(Double taxes) {
    this.taxes = scaleDecimal(taxes);
  }

  public void setTaxes(double taxes) {
    this.taxes = scaleDecimal(taxes);
  }

  public Double getRealizedProfit() {
    return asDouble(realizedProfit);
  }

  public void setRealizedProfit(BigDecimal realizedProfit) {
    this.realizedProfit = scaleDecimal(realizedProfit);
  }

  public void setRealizedProfit(Double realizedProfit) {
    this.realizedProfit = scaleDecimal(realizedProfit);
  }

  public void setRealizedProfit(double realizedProfit) {
    this.realizedProfit = scaleDecimal(realizedProfit);
  }

  public Double getProfit() {
    return asDouble(profit);
  }

  public void setProfit(BigDecimal profit) {
    this.profit = scaleDecimal(profit);
  }

  public void setProfit(Double profit) {
    this.profit = scaleDecimal(profit);
  }

  public void setProfit(double profit) {
    this.profit = scaleDecimal(profit);
  }

  public Double getReturnPct() {
    return asDouble(returnPct);
  }

  public BigDecimal getEndEquityDecimal() {
    return endEquity;
  }

  public BigDecimal getStartEquityDecimal() {
    return startEquity;
  }

  public BigDecimal getDepositFlowDecimal() {
    return depositFlow;
  }

  public BigDecimal getWithdrawalFlowDecimal() {
    return withdrawalFlow;
  }

  public BigDecimal getDividendsDecimal() {
    return dividends;
  }

  public BigDecimal getInterestDecimal() {
    return interest;
  }

  public BigDecimal getFeesDecimal() {
    return fees;
  }

  public BigDecimal getTaxesDecimal() {
    return taxes;
  }

  public BigDecimal getRealizedProfitDecimal() {
    return realizedProfit;
  }

  public BigDecimal getProfitDecimal() {
    return profit;
  }

  public BigDecimal getReturnPctDecimal() {
    return returnPct;
  }

  public void setReturnPct(BigDecimal returnPct) {
    this.returnPct = scaleDecimal(returnPct);
  }

  public void setReturnPct(Double returnPct) {
    this.returnPct = scaleDecimal(returnPct);
  }

  public void setReturnPct(double returnPct) {
    this.returnPct = scaleDecimal(returnPct);
  }

  private static BigDecimal scaleDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
  }

  private static BigDecimal scaleDecimal(Double value) {
    return value == null ? null : scaleDecimal(BigDecimal.valueOf(value));
  }

  private static BigDecimal scaleDecimal(double value) {
    return scaleDecimal(BigDecimal.valueOf(value));
  }

  private static Double asDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
