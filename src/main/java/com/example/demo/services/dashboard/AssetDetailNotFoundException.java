package com.example.demo.services.dashboard;

public class AssetDetailNotFoundException extends RuntimeException {

  public AssetDetailNotFoundException(String symbol) {
    super("Asset not found: " + symbol);
  }
}
