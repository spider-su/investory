package com.example.demo.services.dashboard;

import java.time.LocalDate;

public record AssetPricePointView(
    LocalDate date,
    double closePrice,
    String currency,
    String source,
    Integer qualityScore,
    String qualityClass,
    String priceOrigin) {}
