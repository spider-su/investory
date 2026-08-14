package com.smartbox.investory.services.dashboard;

public class AssetDetailNotFoundException extends RuntimeException {

  public AssetDetailNotFoundException(String symbol) {
    super("Asset not found: " + symbol);
  }
}
