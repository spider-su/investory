package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Authoritative portfolio/month reporting row. */
@Entity
@Immutable
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "app_v_portfolio_monthly_summary")
public class PortfolioMonthlySummaryEntity {
  @Id
  @Column(name = "portfolio_month_key")
  private String portfolioMonthKey;

  @Column(name = "portfolio_id")
  private Long portfolioId;

  @Column(name = "month")
  private LocalDate month;

  @Column(name = "opening_equity")
  private BigDecimal openingEquity;

  @Column(name = "closing_equity")
  private BigDecimal closingEquity;

  @Column(name = "deposits")
  private BigDecimal deposits;

  @Column(name = "withdrawals")
  private BigDecimal withdrawals;

  @Column(name = "net_external_flow")
  private BigDecimal netExternalFlow;

  @Column(name = "total_profit")
  private BigDecimal totalProfit;

  @Column(name = "market_fx")
  private BigDecimal marketFx;

  @Column(name = "realized")
  private BigDecimal realized;

  @Column(name = "dividends")
  private BigDecimal dividends;

  @Column(name = "interest")
  private BigDecimal interest;

  @Column(name = "fees")
  private BigDecimal fees;

  @Column(name = "taxes")
  private BigDecimal taxes;

  @Column(name = "active_account_count")
  private Long activeAccountCount;
}
