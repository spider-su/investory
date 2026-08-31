package com.smartbox.investory.investment.api.portfolio;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Current brokerage aggregation for application consumers.
 *
 * <p>{@link BrokeragePortfolioReader#currentSharedSnapshot()} returns the complete shared market
 * dataset. {@link BrokeragePortfolioReader#currentSnapshot(Long)} returns the same economic shape
 * scoped to one portfolio.
 */
public record SharedBrokeragePortfolioSnapshot(
    CurrencyType baseCurrency,
    BigDecimal balance,
    BigDecimal cash,
    BigDecimal dividends,
    BigDecimal interest,
    List<BrokeragePositionSnapshot> openPositions) {
  public SharedBrokeragePortfolioSnapshot {
    openPositions =
        com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(openPositions);
  }
}
