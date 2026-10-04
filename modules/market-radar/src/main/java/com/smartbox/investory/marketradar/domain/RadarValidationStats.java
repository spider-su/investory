package com.smartbox.investory.marketradar.domain;

public record RadarValidationStats(
    RadarState state,
    int horizonDays,
    long observations,
    Double averageReturn,
    Double medianReturn,
    Double averageExcessReturn,
    Double medianExcessReturn,
    Double positiveRate,
    Double outperformRate) {}
