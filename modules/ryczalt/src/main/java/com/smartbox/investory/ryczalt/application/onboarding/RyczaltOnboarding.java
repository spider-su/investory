package com.smartbox.investory.ryczalt.application.onboarding;

import java.time.Instant;
import java.time.LocalDate;

public record RyczaltOnboarding(
    long profileId,
    String state,
    String nip,
    String companyName,
    String regon,
    String legalForm,
    String businessStatus,
    LocalDate businessStartDate,
    String vatStatus,
    String registeredAddress,
    String companySource,
    String lookupStatus,
    String lookupWarnings,
    Instant companyLookupRetrievedAt,
    Instant companyConfirmedAt,
    String zusRegime,
    Boolean jdgActive,
    Boolean qualifyingUop,
    Boolean voluntarySickness,
    String zusHealthMethod,
    String zusFullJdgSocial,
    String taxationMethod,
    String ryczaltRate,
    String pitFrequency,
    String vatFrequency,
    LocalDate accountingStartDate,
    String ksefState,
    Instant completedAt) {}
