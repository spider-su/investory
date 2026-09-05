package com.smartbox.investory.investment.api.portfolio;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Current brokerage aggregation for application consumers.
 *
 * <p>{@link BrokeragePortfolioReader#currentSnapshot(Long)} returns the portfolio-scoped market
 * dataset. {@code balance} is total brokerage equity, including {@code cash}; open positions carry
 * the invested part used for allocation reconciliation.
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
