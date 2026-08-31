package com.smartbox.investory.longterm.api.model;

/** Public Long-Term API model. */
public class RentalTaxPolicyNotFoundException extends ResourceNotFoundException {
  public RentalTaxPolicyNotFoundException(Long portfolioId, Long policyId) {
    super("Rental tax policy %s was not found in portfolio %s".formatted(policyId, portfolioId));
  }
}
