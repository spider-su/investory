package com.example.demo.infrastructure.repository.portfolio;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "portfolio_kpi_summary")
public class PortfolioKpiSummary {

  @Id
  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(name = "portfolio_name", nullable = false)
  private String portfolioName;

  @Enumerated(EnumType.STRING)
  @Column(name = "base_currency", nullable = false)
  private CurrencyType baseCurrency;

  @Column(name = "total_deposits")
  private Double totalDeposits;

  @Column(name = "net_deposits")
  private Double netDeposits;

  @Column(name = "total_withdrawals")
  private Double totalWithdrawals;

  @Column(name = "total_cash")
  private Double totalCash;

  @Column(name = "total_market_value")
  private Double totalMarketValue;

  @Column(name = "total_equity")
  private Double totalEquity;

  @Column(name = "total_realized_profit")
  private Double totalRealizedProfit;

  @Column(name = "total_unrealized_profit")
  private Double totalUnrealizedProfit;

  @Column(name = "total_dividends")
  private Double totalDividends;

  @Column(name = "total_interest")
  private Double totalInterest;

  @Column(name = "total_fees")
  private Double totalFees;

  @Column(name = "total_taxes")
  private Double totalTaxes;

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
    this.totalDeposits = totalDeposits;
    this.totalWithdrawals = totalDeposits - netDeposits;
    this.netDeposits = netDeposits;
    this.totalCash = totalCash;
    this.totalMarketValue = totalMarketValue;
    this.totalEquity = totalEquity;
    this.totalRealizedProfit = totalRealizedProfit;
    this.totalUnrealizedProfit = totalUnrealizedProfit;
    this.totalDividends = totalDividends;
    this.totalInterest = totalInterest;
    this.totalFees = 0.0;
    this.totalTaxes = 0.0;
    this.activityCount = 0;
    this.updatedAt = updatedAt;
  }
}
