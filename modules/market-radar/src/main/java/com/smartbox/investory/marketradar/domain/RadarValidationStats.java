package com.smartbox.investory.marketradar.domain;

public record RadarValidationStats(
    RadarState state,
    int horizonDays,
    long observations,
    Double averageReturn,
    Double averageExcessReturn,
    Double positiveRate,
    Double outperformRate) {}
