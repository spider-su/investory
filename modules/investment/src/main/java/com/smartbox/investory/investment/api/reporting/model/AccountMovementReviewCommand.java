package com.smartbox.investory.investment.api.reporting.model;

import java.time.LocalDate;

public record AccountMovementReviewCommand(
    String issueCode,
    Long accountId,
    LocalDate eventDate,
    String eventFingerprint,
    AccountMovementReviewResolution resolution,
    String rationale,
    String evidenceReference,
    String reviewedBy) {}
