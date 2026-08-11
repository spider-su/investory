package com.example.demo.infrastructure.repository.portfolio;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
@Table(name = "app_v_portfolio_kpi_summary")
public class PortfolioKpiSummary {

  @Id
  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(name = "portfolio_name", nullable = false)
  private String portfolioName;

  @Enumerated(EnumType.STRING)
  @Column(name = "base_currency", nullable = false)
  private CurrencyType baseCurrency;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_deposits", precision = 20, scale = 8)
  private BigDecimal totalDeposits;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "net_deposits", precision = 20, scale = 8)
  private BigDecimal netDeposits;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_withdrawals", precision = 20, scale = 8)
  private BigDecimal totalWithdrawals;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_cash", precision = 20, scale = 8)
  private BigDecimal totalCash;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_market_value", precision = 20, scale = 8)
  private BigDecimal totalMarketValue;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_equity", precision = 20, scale = 8)
  private BigDecimal totalEquity;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_realized_profit", precision = 20, scale = 8)
  private BigDecimal totalRealizedProfit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_unrealized_profit", precision = 20, scale = 8)
  private BigDecimal totalUnrealizedProfit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_dividends", precision = 20, scale = 8)
  private BigDecimal totalDividends;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_interest", precision = 20, scale = 8)
  private BigDecimal totalInterest;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_fees", precision = 20, scale = 8)
  private BigDecimal totalFees;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_taxes", precision = 20, scale = 8)
  private BigDecimal totalTaxes;

  @Column(name = "activity_count")
  private Integer activityCount;

  @Column(name = "source_max_date")
  private java.time.LocalDate sourceMaxDate;

  @Column(name = "updated_at", nullable = false)
  private java.time.ZonedDateTime updatedAt;

  public PortfolioKpiSummary(
      Long portfolioId,
      String portfolioName,
      CurrencyType baseCurrency,
      double totalDeposits,
      double netDeposits,
      double totalCash,
      double totalMarketValue,
      double totalEquity,
      double totalRealizedProfit,
      double totalUnrealizedProfit,
      double totalDividends,
      double totalInterest,
      java.time.ZonedDateTime updatedAt) {
    this.portfolioId = portfolioId;
    this.portfolioName = portfolioName;
    this.baseCurrency = baseCurrency;
    this.totalDeposits = scaleDecimal(totalDeposits);
    this.totalWithdrawals = scaleDecimal(totalDeposits - netDeposits);
    this.netDeposits = scaleDecimal(netDeposits);
    this.totalCash = scaleDecimal(totalCash);
    this.totalMarketValue = scaleDecimal(totalMarketValue);
    this.totalEquity = scaleDecimal(totalEquity);
    this.totalRealizedProfit = scaleDecimal(totalRealizedProfit);
    this.totalUnrealizedProfit = scaleDecimal(totalUnrealizedProfit);
    this.totalDividends = scaleDecimal(totalDividends);
    this.totalInterest = scaleDecimal(totalInterest);
    this.totalFees = scaleDecimal(0.0);
    this.totalTaxes = scaleDecimal(0.0);
    this.activityCount = 0;
    this.updatedAt = updatedAt;
  }

  public Double getTotalDeposits() {
    return asDouble(totalDeposits);
  }

  public void setTotalDeposits(BigDecimal totalDeposits) {
    this.totalDeposits = scaleDecimal(totalDeposits);
  }

  public void setTotalDeposits(Double totalDeposits) {
    this.totalDeposits = scaleDecimal(totalDeposits);
  }

  public void setTotalDeposits(double totalDeposits) {
    this.totalDeposits = scaleDecimal(totalDeposits);
  }

  public Double getNetDeposits() {
    return asDouble(netDeposits);
  }

  public void setNetDeposits(BigDecimal netDeposits) {
    this.netDeposits = scaleDecimal(netDeposits);
  }

  public void setNetDeposits(Double netDeposits) {
    this.netDeposits = scaleDecimal(netDeposits);
  }

  public void setNetDeposits(double netDeposits) {
    this.netDeposits = scaleDecimal(netDeposits);
  }

  public Double getTotalWithdrawals() {
    return asDouble(totalWithdrawals);
  }

  public void setTotalWithdrawals(BigDecimal totalWithdrawals) {
    this.totalWithdrawals = scaleDecimal(totalWithdrawals);
  }

  public void setTotalWithdrawals(Double totalWithdrawals) {
    this.totalWithdrawals = scaleDecimal(totalWithdrawals);
  }

  public void setTotalWithdrawals(double totalWithdrawals) {
    this.totalWithdrawals = scaleDecimal(totalWithdrawals);
  }

  public Double getTotalCash() {
    return asDouble(totalCash);
  }

  public void setTotalCash(BigDecimal totalCash) {
    this.totalCash = scaleDecimal(totalCash);
  }

  public void setTotalCash(Double totalCash) {
    this.totalCash = scaleDecimal(totalCash);
  }

  public void setTotalCash(double totalCash) {
    this.totalCash = scaleDecimal(totalCash);
  }

  public Double getTotalMarketValue() {
    return asDouble(totalMarketValue);
  }

  public void setTotalMarketValue(BigDecimal totalMarketValue) {
    this.totalMarketValue = scaleDecimal(totalMarketValue);
  }

  public void setTotalMarketValue(Double totalMarketValue) {
    this.totalMarketValue = scaleDecimal(totalMarketValue);
  }

  public void setTotalMarketValue(double totalMarketValue) {
    this.totalMarketValue = scaleDecimal(totalMarketValue);
  }

  public Double getTotalEquity() {
    return asDouble(totalEquity);
  }

  public void setTotalEquity(BigDecimal totalEquity) {
    this.totalEquity = scaleDecimal(totalEquity);
  }

  public void setTotalEquity(Double totalEquity) {
    this.totalEquity = scaleDecimal(totalEquity);
  }

  public void setTotalEquity(double totalEquity) {
    this.totalEquity = scaleDecimal(totalEquity);
  }

  public Double getTotalRealizedProfit() {
    return asDouble(totalRealizedProfit);
  }

  public void setTotalRealizedProfit(BigDecimal totalRealizedProfit) {
    this.totalRealizedProfit = scaleDecimal(totalRealizedProfit);
  }

  public void setTotalRealizedProfit(Double totalRealizedProfit) {
    this.totalRealizedProfit = scaleDecimal(totalRealizedProfit);
  }

  public void setTotalRealizedProfit(double totalRealizedProfit) {
    this.totalRealizedProfit = scaleDecimal(totalRealizedProfit);
  }

  public Double getTotalUnrealizedProfit() {
    return asDouble(totalUnrealizedProfit);
  }

  public void setTotalUnrealizedProfit(BigDecimal totalUnrealizedProfit) {
    this.totalUnrealizedProfit = scaleDecimal(totalUnrealizedProfit);
  }

  public void setTotalUnrealizedProfit(Double totalUnrealizedProfit) {
    this.totalUnrealizedProfit = scaleDecimal(totalUnrealizedProfit);
  }

  public void setTotalUnrealizedProfit(double totalUnrealizedProfit) {
    this.totalUnrealizedProfit = scaleDecimal(totalUnrealizedProfit);
  }

  public Double getTotalDividends() {
    return asDouble(totalDividends);
  }

  public void setTotalDividends(BigDecimal totalDividends) {
    this.totalDividends = scaleDecimal(totalDividends);
  }

  public void setTotalDividends(Double totalDividends) {
    this.totalDividends = scaleDecimal(totalDividends);
  }

  public void setTotalDividends(double totalDividends) {
    this.totalDividends = scaleDecimal(totalDividends);
  }

  public Double getTotalInterest() {
    return asDouble(totalInterest);
  }

  public void setTotalInterest(BigDecimal totalInterest) {
    this.totalInterest = scaleDecimal(totalInterest);
  }

  public void setTotalInterest(Double totalInterest) {
    this.totalInterest = scaleDecimal(totalInterest);
  }

  public void setTotalInterest(double totalInterest) {
    this.totalInterest = scaleDecimal(totalInterest);
  }

  public Double getTotalFees() {
    return asDouble(totalFees);
  }

  public void setTotalFees(BigDecimal totalFees) {
    this.totalFees = scaleDecimal(totalFees);
  }

  public void setTotalFees(Double totalFees) {
    this.totalFees = scaleDecimal(totalFees);
  }

  public void setTotalFees(double totalFees) {
    this.totalFees = scaleDecimal(totalFees);
  }

  public Double getTotalTaxes() {
    return asDouble(totalTaxes);
  }

  public void setTotalTaxes(BigDecimal totalTaxes) {
    this.totalTaxes = scaleDecimal(totalTaxes);
  }

  public void setTotalTaxes(Double totalTaxes) {
    this.totalTaxes = scaleDecimal(totalTaxes);
  }

  public void setTotalTaxes(double totalTaxes) {
    this.totalTaxes = scaleDecimal(totalTaxes);
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
