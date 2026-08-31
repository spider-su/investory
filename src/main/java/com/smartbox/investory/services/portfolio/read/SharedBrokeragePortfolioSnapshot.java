package com.smartbox.investory.services.portfolio.read;

import com.smartbox.investory.infrastructure.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Current shared brokerage aggregation for application/profile consumers.
 *
 * <p>This is intentionally not portfolio-scoped: current brokerage reporting aggregates the shared
 * market dataset. Multi-portfolio brokerage scoping is a separate change.
 */
public record SharedBrokeragePortfolioSnapshot(
    CurrencyType baseCurrency,
    BigDecimal balance,
    BigDecimal cash,
    BigDecimal dividends,
    BigDecimal interest,
    List<BrokeragePositionSnapshot> openPositions) {
  public SharedBrokeragePortfolioSnapshot {
    openPositions = openPositions == null ? List.of() : List.copyOf(openPositions);
  }
}
