package com.smartbox.investory.retirement.profile;

import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;

/** Profile-owned labels and formatting. */
final class ProfilePresentation {
  private ProfilePresentation() {}

  static String bucket(EconomicBucket bucket) {
    return switch (bucket) {
      case LIQUID_CASH -> "Cash";
      case FIXED_INCOME -> "Fixed income";
      case EQUITY -> "Equity";
      case REAL_ESTATE -> "Real estate";
      case OTHER -> "Other";
    };
  }

  static String liquidity(Liquidity value) {
    return value == Liquidity.ILLIQUID ? "Illiquid" : "Liquid";
  }

  static String money(BigDecimal value, Object currency) {
    return FinancialPresentation.money(value, currency);
  }

  static String wholeNumber(BigDecimal value) {
    return FinancialPresentation.wholeNumber(value);
  }

  static String percentage(BigDecimal value) {
    return FinancialPresentation.percentage(value);
  }
}
