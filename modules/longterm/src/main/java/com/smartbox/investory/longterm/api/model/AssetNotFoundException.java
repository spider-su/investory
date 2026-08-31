package com.smartbox.investory.longterm.api.model;

/** Public Long-Term API model. */
public class AssetNotFoundException extends ResourceNotFoundException {
  public AssetNotFoundException(Long portfolioId, Long assetId) {
    super("Long-term asset %s was not found in portfolio %s".formatted(assetId, portfolioId));
  }
}
