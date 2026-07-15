package com.example.demo.infrastructure.repository.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
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
@EqualsAndHashCode(of = "id")
@Table(name = "account_daily")
public class AccountDaily {

  @Id
  @SequenceGenerator(
      name = "account_daily_id_seq",
      sequenceName = "account_daily_id_seq",
      allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_daily_id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "cash_balance", nullable = false)
  private Double cashBalance;

  @Column(name = "market_value", nullable = false)
  private Double marketValue;

  @Column(name = "equity", nullable = false)
  private Double equity;

  @Column(name = "unrealized_profit", nullable = false)
  private Double unrealizedProfit;

  @Column(name = "cost_base", nullable = false)
  private Double costBase;

  @Column(name = "realized_profit", nullable = false)
  private Double realizedProfit;

  @Column(name = "dividends", nullable = false)
  private Double dividends;

  @Column(name = "interest", nullable = false)
  private Double interest;

  @Column(name = "fees", nullable = false)
  private Double fees;

  @Column(name = "taxes", nullable = false)
  private Double taxes;

  @Column(name = "deposits", nullable = false)
  private Double deposits;

  @Column(name = "withdrawals", nullable = false)
  private Double withdrawals;

  @Column(name = "daily_return")
  private Double dailyReturn;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;
}
