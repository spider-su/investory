package com.smartbox.investory.marketradar.domain;

import java.time.LocalDate;

public record RadarValidationObservation(
    String symbol,
    LocalDate signalDate,
    RadarState state,
    int horizonDays,
    double symbolReturn,
    Double benchmarkReturn,
    Double excessReturn) {}
