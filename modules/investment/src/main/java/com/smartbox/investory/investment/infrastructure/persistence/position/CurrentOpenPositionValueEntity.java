package com.smartbox.investory.investment.infrastructure.persistence.position;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** Canonical current valuation, shared with dashboard reporting; prices are already normalized. */
@Entity
@Immutable
@Getter
@Table(name = "app_v_current_open_position_rows")
public class CurrentOpenPositionValueEntity {
  @Id
  @Column(name = "position_id")
  private Long positionId;

  @Column(name = "portfolio_id")
  private Long portfolioId;

  @Column(name = "market_price")
  private BigDecimal marketPrice;

  @Column(name = "market_price_currency")
  private String marketPriceCurrency;

  @Column(name = "market_price_to_base_rate")
  private BigDecimal marketPriceToBaseRate;

  @Column(name = "market_value_in_base_currency")
  private BigDecimal marketValueInBaseCurrency;
}
