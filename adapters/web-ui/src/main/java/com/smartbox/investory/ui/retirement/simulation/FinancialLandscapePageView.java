package com.smartbox.investory.ui.retirement.simulation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Presentation-only model for the financial landscape page. */
public record FinancialLandscapePageView(
    Long portfolioId,
    String currency,
    int year,
    BigDecimal annualIncome,
    BigDecimal annualCashIncome,
    BigDecimal annualMarketResult,
    BigDecimal annualExpenses,
    BigDecimal annualGap,
    BigDecimal liquidAssets,
    BigDecimal illiquidAssets,
    List<ExpenseGroupView> expenseGroups) {
  Map<String, Object> asAttributes(boolean compact) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("annualIncome", annualIncome);
    values.put("annualCashIncome", annualCashIncome);
    values.put("annualMarketResult", annualMarketResult);
    values.put("annualExpenses", annualExpenses);
    values.put("annualGap", annualGap);
    if (!compact) {
      values.put("liquidAssets", liquidAssets);
      values.put("illiquidAssets", illiquidAssets);
      values.put("expenseGroups", expenseGroups);
    }
    return values;
  }

  /** Visible expense breakdown for the landscape footer/summary area. */
  public record ExpenseGroupView(String name, BigDecimal monthlyAmount) {}
}
