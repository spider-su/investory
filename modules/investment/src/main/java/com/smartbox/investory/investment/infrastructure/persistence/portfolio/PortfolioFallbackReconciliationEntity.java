package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Read-only mapping of the per-portfolio fallback reconciliation view. */
@Entity
@Immutable
@Getter
@NoArgsConstructor
@Table(name = "recon_v_portfolio_service_fallback", schema = "investory")
public class PortfolioFallbackReconciliationEntity {
  @Id
  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(name = "fallback_reconciliation_status")
  private String fallbackReconciliationStatus;

  @Column(name = "realized_profit_difference")
  private BigDecimal realizedProfitDifference;

  @Column(name = "unrealized_profit_difference")
  private BigDecimal unrealizedProfitDifference;

  @Column(name = "dividends_difference")
  private BigDecimal dividendsDifference;

  @Column(name = "interest_difference")
  private BigDecimal interestDifference;
}
