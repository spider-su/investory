package com.smartbox.investory.marketradar.domain;

import java.time.LocalDate;

public record ThemeSnapshot(
    String theme,
    String proxySymbol,
    LocalDate date,
    ThemeState state,
    int memberCount,
    double breadthAboveSma50,
    double breadthOutperformingBenchmark,
    double proxyReturn20d,
    double benchmarkReturn20d,
    double relativeStrength20d) {}
