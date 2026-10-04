package com.smartbox.investory.investment.api.reporting.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AccountMovementReview(
    String issueCode,
    Long accountId,
    String accountName,
    LocalDate eventDate,
    LocalDate previousDate,
    Integer gapDays,
    BigDecimal previousValue,
    BigDecimal currentValue,
    BigDecimal changePct,
    BigDecimal ratio,
    String explanation,
    String eventFingerprint,
    boolean active,
    boolean priorDecisionMatchesCurrent,
    AccountMovementReviewResolution previousResolution,
    String previousRationale,
    String previousEvidenceReference,
    String previousReviewedBy,
    Instant previousReviewedAt) {}
