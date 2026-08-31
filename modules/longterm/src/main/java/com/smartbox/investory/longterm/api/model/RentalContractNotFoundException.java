package com.smartbox.investory.longterm.api.model;

/** Public Long-Term API model. */
public class RentalContractNotFoundException extends ResourceNotFoundException {
  public RentalContractNotFoundException(Long assetId, Long contractId) {
    super("Rental contract %s was not found for asset %s".formatted(contractId, assetId));
  }
}
