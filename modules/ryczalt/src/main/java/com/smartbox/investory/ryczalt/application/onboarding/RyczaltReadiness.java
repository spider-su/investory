package com.smartbox.investory.ryczalt.application.onboarding;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

public record RyczaltReadiness(
    boolean onboardingComplete,
    boolean companyConfigured,
    boolean accountingConfigured,
    boolean zusConfigured,
    Ksef ksef,
    Period period,
    List<Calculation> calculations,
    List<Item> items) {
  public RyczaltReadiness {
    items = List.copyOf(items);
  }

  public record Item(String code, String status, String action) {}

  public record Ksef(String configuration, String sync) {}

  public record Period(YearMonth month, String state, boolean confirmedNoActivity) {}

  public record Calculation(String type, String status, List<String> issueCodes) {}

  public record NoActivityConfirmation(
      YearMonth month, String type, Instant confirmedAt, String confirmedBy, String dataVersion) {}

  /** Compatibility accessor for the earlier onboarding-only response. */
  public String ksefStatus() {
    return ksef.configuration();
  }

  /** Compatibility accessor for the earlier onboarding-only response. */
  public YearMonth periodMonth() {
    return period.month();
  }

  /** Compatibility accessor for the earlier onboarding-only response. */
  public String periodReadiness() {
    return period.state();
  }
}
