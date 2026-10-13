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
    BigDecimal annualLongTermIncome,
    BigDecimal annualExpenses,
    BigDecimal annualSurplus,
    BigDecimal annualGap,
    BigDecimal liquidAssets,
    BigDecimal illiquidAssets,
    BigDecimal netWorth,
    BigDecimal reserve,
    List<ExpenseGroupView> expenseGroups) {
  Map<String, Object> asAttributes() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("currency", currency);
    values.put("year", year);
    values.put("annualIncome", annualIncome);
    values.put("annualCashIncome", annualCashIncome);
    values.put("annualMarketResult", annualMarketResult);
    values.put("annualLongTermIncome", annualLongTermIncome);
    values.put("annualExpenses", annualExpenses);
    values.put("annualSurplus", annualSurplus);
    values.put("annualGap", annualGap);
    values.put("liquidAssets", liquidAssets);
    values.put("illiquidAssets", illiquidAssets);
    values.put("netWorth", netWorth);
    values.put("reserve", reserve);
    values.put("expenseGroups", expenseGroups);
    return values;
  }

  /** Visible expense breakdown for the landscape footer/summary area. */
  public record ExpenseGroupView(String name, BigDecimal monthlyAmount) {}
}
