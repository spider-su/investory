package com.smartbox.investory.longterm.api.model;

/** Public Long-Term API model. */
public class ValuationNotFoundException extends ResourceNotFoundException {
  public ValuationNotFoundException(Long assetId, Long periodId) {
    super("Valuation period %s was not found for asset %s".formatted(periodId, assetId));
  }
}
