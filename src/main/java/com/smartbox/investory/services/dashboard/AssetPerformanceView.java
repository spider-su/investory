package com.smartbox.investory.services.dashboard;

import java.time.ZonedDateTime;

public record AssetPerformanceView(
    Double closedProfit,
    Double unrealizedProfit,
    Double totalProfit,
    Double dividends,
    Double withholdingTax,
    Double costBasis,
    Double marketValue,
    ZonedDateTime updatedAt) {}
