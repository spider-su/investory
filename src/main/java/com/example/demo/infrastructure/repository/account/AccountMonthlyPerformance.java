package com.example.demo.infrastructure.repository.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
@EqualsAndHashCode(of = "id")
@Table(name = "account_monthly_performance")
public class AccountMonthlyPerformance {

  @Id
  @Column(name = "id", nullable = false)
  private String id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "month", nullable = false)
  private LocalDate month;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "start_equity", nullable = false)
  private Double startEquity;

  @Column(name = "end_equity", nullable = false)
  private Double endEquity;

  @Column(name = "deposit_flow", nullable = false)
  private Double depositFlow;

  @Column(name = "withdrawal_flow", nullable = false)
  private Double withdrawalFlow;

  @Column(name = "net_cashflow", nullable = false)
  private Double netCashflow;

  @Column(name = "profit", nullable = false)
  private Double profit;

  @Column(name = "return_pct", nullable = false)
  private Double returnPct;
}
