package com.smartbox.investory.ryczalt.application;

import java.time.LocalDate;

/** Profile-scoped taxpayer configuration required by native Ryczalt exports. */
public record RyczaltProfile(
    long profileId,
    boolean hasUop,
    String nip,
    String fullName,
    String taxOfficeCode,
    String email,
    String vatPaymentAccount,
    String ryczaltPaymentAccount,
    String zusPaymentAccount,
    String firstName,
    String surname,
    LocalDate dateOfBirth) {}
