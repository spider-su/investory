package com.smartbox.investory.investment.infrastructure.persistence.account;

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

@Entity
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "app_v_account_monthly_attribution")
public class AccountMonthlyAttributionEntity {
  @Id
  @Column(name = "account_month_key")
  private String accountMonthKey;

  @Column(name = "portfolio_id")
  private Long portfolioId;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "month")
  private LocalDate month;

  @Column(name = "opening_equity")
  private BigDecimal openingEquity;

  @Column(name = "closing_equity")
  private BigDecimal closingEquity;

  @Column(name = "net_cashflow")
  private BigDecimal netCashflow;

  @Column(name = "profit")
  private BigDecimal profit;

  @Column(name = "contribution_pct")
  private BigDecimal contributionPct;
}
