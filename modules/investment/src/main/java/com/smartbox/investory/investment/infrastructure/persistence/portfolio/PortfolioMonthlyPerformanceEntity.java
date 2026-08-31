package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

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
public class PortfolioMonthlyPerformanceEntity {

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

  public PortfolioMonthlyPerformanceEntity(
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
