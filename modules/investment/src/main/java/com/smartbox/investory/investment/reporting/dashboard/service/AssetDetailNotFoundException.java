package com.smartbox.investory.investment.reporting.dashboard.service;

public class AssetDetailNotFoundException extends RuntimeException {

  public AssetDetailNotFoundException(String symbol) {
    super("Asset not found: " + symbol);
  }
}
