package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import java.time.LocalDate;

public record PortfolioDataQualityIssueRow(
    String issueType,
    String accountId,
    String assetId,
    String issueCode,
    Integer priceAgeDays,
    LocalDate selectedPriceDate,
    String priceCurrency,
    String priceQuality,
    String priceOrigin,
    String reconstructionStatus,
    Long selectedPriceHistoryId) {}
