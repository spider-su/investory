package com.smartbox.investory.ryczalt.application.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyLookupAggregatorTest {
  @Test
  void combinesProvidersWithoutReplacingKnownFieldsWithEmptyValues() {
    var gus =
        (CompanyLookupProvider)
            nip ->
                new CompanyLookupResult(
                    nip,
                    "Acme JDG",
                    "123",
                    null,
                    "Street 1",
                    "GUS_BIR",
                    Instant.now(),
                    "JDG",
                    "ACTIVE",
                    null,
                    List.of("GUS_BIR"),
                    List.of(),
                    "PARTIAL");
    var vat =
        (CompanyLookupProvider)
            nip ->
                new CompanyLookupResult(
                    nip,
                    null,
                    null,
                    "Czynny",
                    null,
                    "VAT_WHITE_LIST",
                    Instant.now(),
                    null,
                    null,
                    null,
                    List.of("VAT_WHITE_LIST"),
                    List.of(),
                    "PARTIAL");

    var result = new CompanyLookupAggregator(List.of(gus, vat)).lookup("5261040518");

    assertEquals("Acme JDG", result.companyName());
    assertEquals("Czynny", result.vatStatus());
    assertEquals(List.of("GUS_BIR", "VAT_WHITE_LIST"), result.sources());
    assertTrue(result.warnings().isEmpty());
  }

  @Test
  void keepsPartialResultWhenOneProviderFails() {
    var gus =
        (CompanyLookupProvider)
            nip -> {
              throw new IllegalStateException("provider_timeout");
            };
    var vat =
        (CompanyLookupProvider)
            nip ->
                new CompanyLookupResult(
                    nip, "Acme", null, "Czynny", null, "VAT_WHITE_LIST", Instant.now());

    var result = new CompanyLookupAggregator(List.of(gus, vat)).lookup("5261040518");

    assertEquals("Acme", result.companyName());
    assertEquals("PARTIAL", result.lookupStatus());
    assertTrue(result.warnings().contains("provider_timeout"));
  }
}
