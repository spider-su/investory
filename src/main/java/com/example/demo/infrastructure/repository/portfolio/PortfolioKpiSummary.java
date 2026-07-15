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
import org.hibernate.annotations.Immutable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
@Table(name = "mv_portfolio_kpi_summary")
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
}
