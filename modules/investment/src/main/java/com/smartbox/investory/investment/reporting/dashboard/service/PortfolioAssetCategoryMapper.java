package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.investment.infrastructure.persistence.AssetEntity;
import java.util.Locale;

/** Deterministic mapping from the existing asset type field to dashboard categories. */
final class PortfolioAssetCategoryMapper {
  private PortfolioAssetCategoryMapper() {}

  static String category(AssetEntity asset) {
    if (asset == null || asset.getAssetType() == null) return "Other";
    String type = asset.getAssetType().trim().toUpperCase(Locale.ROOT);
    return switch (type) {
      case "CASH", "CASH_RESERVE" -> "Cash";
      case "BOND", "BONDS", "FIXED_INCOME", "FIXED INCOME" -> "Fixed income";
      case "REIT", "REAL_ESTATE", "REAL ESTATE" -> "REIT / real estate";
      case "METAL", "METALS", "COMMODITY", "COMMODITIES" -> "Commodity / metal";
      case "ETF" -> "ETF";
      case "EQUITY", "STOCK", "STOCKS", "SHARE", "SHARES" -> "Equity";
      default -> fallback(type);
    };
  }

  private static String fallback(String type) {
    if (type.contains("CASH")) return "Cash";
    if (type.contains("BOND") || type.contains("FIXED")) return "Fixed income";
    if (type.contains("REIT") || type.contains("REAL")) return "REIT / real estate";
    if (type.contains("METAL") || type.contains("COMMOD")) return "Commodity / metal";
    if (type.contains("ETF")) return "ETF";
    if (type.contains("EQUITY") || type.contains("STOCK") || type.contains("SHARE"))
      return "Equity";
    return "Other";
  }
}
