package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PortfolioDataQualitySnapshotRow(
    String state,
    long reconciledAccounts,
    long totalAccounts,
    long pricedOpenPositions,
    long totalOpenPositions,
    long missingPriceOpenPositions,
    long stalePriceOpenPositions,
    long reconstructedPriceOpenPositions,
    long missingFxOpenPositions,
    long staleFxOpenPositions,
    long missingCostBasisPositions,
    long unreconciledPositions,
    OffsetDateTime latestImportAt,
    OffsetDateTime latestCompletedImportAt,
    LocalDate latestPriceDate,
    LocalDate latestFxDate,
    OffsetDateTime latestAccountRefreshAt) {}
