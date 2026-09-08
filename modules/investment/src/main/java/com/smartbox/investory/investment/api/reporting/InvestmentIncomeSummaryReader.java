package com.smartbox.investory.investment.api.reporting;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/** Investment-owned income-source read model for downstream presentation/composition. */
public interface InvestmentIncomeSummaryReader {
  InvestmentIncomeSummary load(Long portfolioId);

  record InvestmentIncomeSummary(
      boolean available,
      CurrencyType currency,
      BigDecimal incomeBase,
      BigDecimal projectedAnnualIncome,
      BigDecimal annualizedYield,
      BigDecimal investmentResultYtd,
      BigDecimal expectedIncomeYtd,
      BigDecimal expectationProgress) {}
}
