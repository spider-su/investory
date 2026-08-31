package com.smartbox.investory.investment.api.asset.model;

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
