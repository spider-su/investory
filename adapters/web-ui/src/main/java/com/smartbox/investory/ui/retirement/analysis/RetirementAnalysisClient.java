package com.smartbox.investory.ui.retirement.analysis;

import com.smartbox.investory.shared.currency.CurrencyType;

/** UI-side client contract. Its implementation may be in-process or HTTP-backed. */
public interface RetirementAnalysisClient {
  RetirementAnalysisView analyze(
      Long portfolioId,
      Long planId,
      Integer defaultCurrentAge,
      Integer defaultEndAge,
      CurrencyType displayCurrency);
}
