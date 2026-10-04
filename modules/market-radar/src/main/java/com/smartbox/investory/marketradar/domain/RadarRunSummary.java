package com.smartbox.investory.marketradar.domain;

import java.time.Instant;
import java.util.UUID;

public record RadarRunSummary(
    UUID id,
    Instant startedAt,
    Instant completedAt,
    RadarRunStatus status,
    int universeSize,
    int attempted,
    int stored,
    int noData,
    int failed,
    int interesting,
    int normal,
    int emerging,
    int trending,
    int hot,
    int extended,
    int cooling,
    int outcomesEvaluated,
    long durationMs,
    String error) {}
