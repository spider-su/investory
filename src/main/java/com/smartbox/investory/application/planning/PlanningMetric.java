package com.smartbox.investory.application.planning;

import java.util.Locale;

/**
 * Compact set of auditable planning values. Amounts are canonical planning/base currency unless a
 * ratio.
 */
public enum PlanningMetric {
  NET_WORTH,
  MARKET_ASSETS,
  SAFE_RESERVE,
  SAFE_RESERVE_TARGET,
  MANUAL_LIQUID_RESERVE,
  FIXED_INCOME,
  EQUITY,
  REAL_ESTATE,
  BOND_VALUE,
  BOND_INCOME,
  CASH_RESERVE_VALUE,
  RENTAL_INCOME,
  PASSIVE_INCOME,
  CORE_SPENDING,
  DISCRETIONARY_SPENDING,
  PORTFOLIO_FUNDING,
  EQUITY_RETURN,
  EQUITY_HARVEST,
  EMERGENCY_EQUITY_WITHDRAWAL,
  MARKET_RETURN,
  MARKET_INCOME,
  MARKET_WITHDRAWAL;

  /** Planning-only flows may be supplied by the user; portfolio facts never may. */
  public boolean isManualEditable() {
    return this == CORE_SPENDING || this == DISCRETIONARY_SPENDING;
  }

  public boolean isRequiredForClose() {
    return this == CORE_SPENDING || this == DISCRETIONARY_SPENDING;
  }

  public String label() {
    if (this == CORE_SPENDING) return "Annual living costs";
    if (this == DISCRETIONARY_SPENDING) return "Annual extras";
    if (this == MARKET_ASSETS) return "Market assets · year-end";
    if (this == MARKET_WITHDRAWAL) return "Net portfolio withdrawal";
    if (this == REAL_ESTATE) return "Real estate · year-end";
    if (this == RENTAL_INCOME) return "Rental income";
    if (this == BOND_VALUE) return "Bonds · year-end";
    if (this == BOND_INCOME) return "Bond income";
    if (this == CASH_RESERVE_VALUE) return "Cash reserve · year-end";
    String text = name().toLowerCase(Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  public PlanningMetricPresentationType presentationType() {
    return switch (this) {
      case EQUITY_RETURN, MARKET_RETURN -> PlanningMetricPresentationType.PERCENTAGE;
      default -> PlanningMetricPresentationType.MONEY;
    };
  }

  public boolean isRatio() {
    return presentationType() == PlanningMetricPresentationType.PERCENTAGE;
  }
}
