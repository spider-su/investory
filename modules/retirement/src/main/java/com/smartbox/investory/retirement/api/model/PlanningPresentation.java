package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;

/** Retirement planning-owned labels and formatting. */
public final class PlanningPresentation {
  private PlanningPresentation() {}

  public static String wholeNumber(BigDecimal value) {
    return FinancialPresentation.wholeNumber(value);
  }

  public static String compactMoney(BigDecimal value) {
    return FinancialPresentation.compactMoney(value);
  }

  public static String percentage(BigDecimal value) {
    return FinancialPresentation.percentage(value);
  }

  public static String years(BigDecimal value) {
    return FinancialPresentation.years(value);
  }

  public static String planningMetric(PlanningMetric metric, BigDecimal value) {
    return switch (metric.presentationType()) {
      case MONEY -> FinancialPresentation.money(value);
      case PERCENTAGE -> FinancialPresentation.percentage(value);
      case NUMBER -> FinancialPresentation.decimal(value);
    };
  }

  public static String planningMetric(PlanningMetric metric, BigDecimal value, Object currency) {
    return switch (metric.presentationType()) {
      case MONEY -> FinancialPresentation.money(value, currency);
      case PERCENTAGE -> FinancialPresentation.percentage(value);
      case NUMBER -> FinancialPresentation.decimal(value);
    };
  }

  public static String fundingStrategy(SimulationFundingStrategy value) {
    return switch (value) {
      case SIMPLE_WATERFALL ->
          "Fixed funding order: income → reserve → permitted Long-Term capital → Investment";
      case RESERVE_AND_HARVEST ->
          "Reserve and harvest funding order: manual reserve → market cash → spendable fixed income → permitted emergency equity";
    };
  }
}
