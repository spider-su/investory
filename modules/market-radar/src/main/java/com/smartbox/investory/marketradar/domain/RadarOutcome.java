package com.smartbox.investory.marketradar.domain;

import java.time.LocalDate;

public record RadarOutcome(
    String symbol,
    LocalDate signalDate,
    int horizonDays,
    LocalDate evaluatedOn,
    double symbolReturn,
    String benchmark,
    Double benchmarkReturn,
    Double excessReturn) {}
