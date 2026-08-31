package com.smartbox.investory.investment.api.portfolio;

/** Stable brokerage asset classification used by profile composition. */
public record BrokerageAssetClassification(String symbol, BrokerageAssetType assetType) {}
