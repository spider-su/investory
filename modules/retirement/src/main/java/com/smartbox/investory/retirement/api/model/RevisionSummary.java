package com.smartbox.investory.retirement.api.model;

import java.time.Instant;

public record RevisionSummary(Long id, int revisionNumber, Instant createdAt) {}
