package com.smartbox.investory.investment.api.asset.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetPricePointView(
    LocalDate date,
    BigDecimal closePrice,
    String currency,
    String source,
    Integer qualityScore,
    String qualityClass,
    String priceOrigin) {}
