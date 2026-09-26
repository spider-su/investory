package com.smartbox.investory.ryczalt.application.onboarding;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CompanyLookupResult(
    String nip,
    String companyName,
    String regon,
    String vatStatus,
    String registeredAddress,
    String source,
    Instant retrievedAt,
    String legalForm,
    String businessStatus,
    LocalDate businessStartDate,
    List<String> sources,
    List<String> warnings,
    String lookupStatus) {
  public CompanyLookupResult(
      String nip,
      String companyName,
      String regon,
      String vatStatus,
      String registeredAddress,
      String source,
      Instant retrievedAt) {
    this(
        nip,
        companyName,
        regon,
        vatStatus,
        registeredAddress,
        source,
        retrievedAt,
        null,
        null,
        null,
        List.of(source),
        List.of(),
        "COMPLETE");
  }
}
