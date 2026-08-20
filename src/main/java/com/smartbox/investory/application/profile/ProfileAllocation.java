package com.smartbox.investory.application.profile;

import com.smartbox.investory.services.PlanningPresentation;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

public record ProfileAllocation(
    EconomicBucket bucket, BigDecimal value, BigDecimal percentage, Liquidity liquidity) {
  public String bucketLabel() {
    return PlanningPresentation.bucket(bucket);
  }

  public String liquidityLabel() {
    return PlanningPresentation.liquidity(liquidity);
  }

  public String valueDisplay(CurrencyType currency) {
    return PlanningPresentation.money(value, currency);
  }

  public String wholeValueDisplay() {
    return PlanningPresentation.wholeNumber(value);
  }

  public boolean isNonZero() {
    return value != null && value.signum() != 0;
  }

  public String getBucketLabel() {
    return bucketLabel();
  }

  public String getLiquidityLabel() {
    return liquidityLabel();
  }

  public String getWholeValueDisplay() {
    return wholeValueDisplay();
  }

  public String getPercentageDisplay() {
    return percentageDisplay();
  }

  public String getAllocationCssClass() {
    return switch (bucket) {
      case EQUITY -> "iv-allocation--equity";
      case REAL_ESTATE -> "iv-allocation--real-estate";
      case FIXED_INCOME -> "iv-allocation--fixed-income";
      case LIQUID_CASH -> "iv-allocation--cash";
      case OTHER -> "iv-allocation--other";
    };
  }

  public String getLegendLabel() {
    return bucketLabel() + " · " + percentageDisplay();
  }

  public String percentageDisplay() {
    return PlanningPresentation.percentage(percentage);
  }
}
