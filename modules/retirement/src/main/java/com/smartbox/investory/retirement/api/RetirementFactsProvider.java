package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.RetirementFacts;

/** Public source-facts boundary used by the retirement application layer. */
public interface RetirementFactsProvider {
  RetirementFacts load(Long portfolioId);
}
