package com.example.demo.infrastructure.repository.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "accountId")
@Table(name = "account_statistics")
public class AccountStatistics {

  @Id
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "valuation_currency")
  private String valuationCurrency;

  @Column(name = "total_deposit", nullable = false)
  private Double totalDeposit;

  @Column(name = "total_withdrawal", nullable = false)
  private Double totalWithdrawal;

  @Column(name = "net_deposit", nullable = false)
  private Double netDeposit;

  @Column(name = "cash_balance", nullable = false)
  private Double cashBalance;

  @Column(name = "market_value", nullable = false)
  private Double marketValue;

  @Column(name = "equity", nullable = false)
  private Double equity;

  @Column(name = "cost_base", nullable = false)
  private Double costBase;

  @Column(name = "realized_profit", nullable = false)
  private Double realizedProfit;

  @Column(name = "unrealized_profit", nullable = false)
  private Double unrealizedProfit;

  @Column(name = "dividends", nullable = false)
  private Double dividends;

  @Column(name = "interest", nullable = false)
  private Double interest;

  @Column(name = "fees", nullable = false)
  private Double fees;

  @Column(name = "taxes", nullable = false)
  private Double taxes;

  @Column(name = "activity_count", nullable = false)
  private Integer activityCount;

  @Column(name = "first_activity_at")
  private ZonedDateTime firstActivityAt;

  @Column(name = "last_activity_at")
  private ZonedDateTime lastActivityAt;

  @Column(name = "latest_snapshot_date")
  private LocalDate latestSnapshotDate;

  @Column(name = "latest_return_pct")
  private Double latestReturnPct;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;
}

