package com.example.demo.infrastructure.repository.portfolio;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(PortfolioMonthlyPerformanceId.class)
@EqualsAndHashCode(of = {"portfolioId", "month"})
@Table(name = "portfolio_monthly_mv")
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

  @Column(name = "opening_equity", nullable = false)
  private Double startEquity;

  @Column(name = "closing_equity", nullable = false)
  private Double endEquity;

  @Enumerated(EnumType.STRING)
  @Column(name = "base_currency", nullable = false)
  private CurrencyType baseCurrency;

  @Column(name = "deposits", nullable = false)
  private Double depositFlow;

  @Column(name = "withdrawals", nullable = false)
  private Double withdrawalFlow;

  @Column(name = "dividends", nullable = false)
  private Double dividends;

  @Column(name = "interest", nullable = false)
  private Double interest;

  @Column(name = "fees", nullable = false)
  private Double fees;

  @Column(name = "taxes", nullable = false)
  private Double taxes;

  @Column(name = "realized_profit", nullable = false)
  private Double realizedProfit;

  @Column(name = "total_profit", nullable = false)
  private Double profit;

  @Column(name = "compounded_monthly_return")
  private Double returnPct;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;

  public Double getNetCashflow() {
    return (depositFlow == null ? 0.0 : depositFlow) - (withdrawalFlow == null ? 0.0 : withdrawalFlow);
  }
}
