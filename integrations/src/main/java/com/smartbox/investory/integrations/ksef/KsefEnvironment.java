package com.smartbox.investory.integrations.ksef;

public enum KsefEnvironment {
  TEST("https://api-test.ksef.mf.gov.pl/v2"),
  DEMO("https://api-demo.ksef.mf.gov.pl/v2"),
  PRODUCTION("https://api.ksef.mf.gov.pl/v2");

  private final String baseUrl;

  KsefEnvironment(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String baseUrl() {
    return baseUrl;
  }

  public static KsefEnvironment parse(String value) {
    if (value == null || value.isBlank()) return TEST;
    return KsefEnvironment.valueOf(value.trim().toUpperCase());
  }
}
