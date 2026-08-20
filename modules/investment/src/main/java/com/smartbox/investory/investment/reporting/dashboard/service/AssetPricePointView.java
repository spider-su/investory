package com.smartbox.investory.investment.reporting.dashboard.service;

import java.time.LocalDate;

public record AssetPricePointView(
    LocalDate date,
    double closePrice,
    String currency,
    String source,
    Integer qualityScore,
    String qualityClass,
    String priceOrigin) {}
