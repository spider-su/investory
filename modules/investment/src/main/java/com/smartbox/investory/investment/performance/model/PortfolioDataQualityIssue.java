package com.smartbox.investory.investment.performance.model;

import java.time.LocalDate;

public record PortfolioDataQualityIssue(
    String type,
    String accountId,
    String assetId,
    String code,
    Integer priceAgeDays,
    LocalDate priceDate,
    String priceCurrency,
    String priceQuality,
    String priceOrigin,
    String reconstructionStatus,
    Long priceHistoryId) {}
