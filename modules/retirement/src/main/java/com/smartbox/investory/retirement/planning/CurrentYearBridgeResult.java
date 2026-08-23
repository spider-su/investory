package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.BucketType;
import com.smartbox.investory.retirement.simulation.SimulationEvent;
import com.smartbox.investory.retirement.simulation.SimulationLifecyclePhase;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable year-end handoff from the live profile to the next full simulation year. */
public record CurrentYearBridgeResult(
    InvestmentProfile bridgedProfile,
    int asOfYear,
    int nextProjectedYear,
    SimulationLifecyclePhase lifecyclePhase,
    BigDecimal fractionApplied,
    BigDecimal contributionApplied,
    BigDecimal retirementSpendingApplied,
    BigDecimal requiredPortfolioFunding,
    BigDecimal passiveIncomeUsed,
    BigDecimal pensionIncomeUsed,
    BigDecimal contractualIncomeApplied,
    BigDecimal redemptionCashApplied,
    BigDecimal investmentAnnualReturn,
    List<SimulationEvent> currentYearEventsApplied,
    Map<BucketType, BucketBoundary> bucketBoundaries) {

  public record BucketBoundary(BigDecimal startValue, BigDecimal expectedEndValue) {
    public BucketBoundary {
      if (startValue == null || expectedEndValue == null) {
        throw new IllegalArgumentException("Bucket boundary requires values");
      }
    }
  }

  public CurrentYearBridgeResult {
    if (bridgedProfile == null
        || lifecyclePhase == null
        || fractionApplied == null
        || contributionApplied == null
        || retirementSpendingApplied == null
        || requiredPortfolioFunding == null
        || passiveIncomeUsed == null
        || pensionIncomeUsed == null
        || contractualIncomeApplied == null
        || redemptionCashApplied == null
        || currentYearEventsApplied == null
        || bucketBoundaries == null) {
      throw new IllegalArgumentException("Bridge result requires complete values");
    }
    currentYearEventsApplied = List.copyOf(currentYearEventsApplied);
    EnumMap<BucketType, BucketBoundary> copy = new EnumMap<>(BucketType.class);
    copy.putAll(bucketBoundaries);
    bucketBoundaries = Map.copyOf(copy);
  }

  public BigDecimal start(BucketType bucket) {
    BucketBoundary boundary = bucketBoundaries.get(bucket);
    return boundary == null ? null : boundary.startValue();
  }

  public BigDecimal expectedEnd(BucketType bucket) {
    BucketBoundary boundary = bucketBoundaries.get(bucket);
    return boundary == null ? null : boundary.expectedEndValue();
  }
}
