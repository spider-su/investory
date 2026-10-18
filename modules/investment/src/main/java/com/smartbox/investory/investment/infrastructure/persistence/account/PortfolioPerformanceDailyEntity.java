package com.smartbox.investory.investment.infrastructure.persistence.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Read-only mapping of the canonical daily portfolio performance view. */
@Entity
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(PortfolioPerformanceDailyId.class)
@Table(name = "app_v_portfolio_performance_daily", schema = "investory")
public class PortfolioPerformanceDailyEntity {
  @Id
  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Id
  @Column(name = "snapshot_date", nullable = false)
  private LocalDate date;

  @Column(name = "equity")
  private BigDecimal endValue;

  @Column(name = "deposits")
  private BigDecimal contributions;

  @Column(name = "withdrawals")
  private BigDecimal withdrawals;

  @Column(name = "initialization_adjustment")
  private BigDecimal initializationAdjustment;
}
