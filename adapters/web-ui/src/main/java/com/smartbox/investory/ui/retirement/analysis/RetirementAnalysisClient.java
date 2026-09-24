package com.smartbox.investory.ui.retirement.analysis;

import com.smartbox.investory.retirement.api.model.RetirementAnalysisResult;

/** UI-side client contract. Its implementation may be in-process or HTTP-backed. */
public interface RetirementAnalysisClient {
  RetirementAnalysisResult analyze(
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge);
}
