package com.smartbox.investory.profile.web;

import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.profile.api.model.ProfileSummary;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/** Explicit external representation of a whole-wealth profile. */
public record ProfileResponse(
    Long portfolioId,
    CurrencyType currency,
    BigDecimal marketPortfolioValue,
    BigDecimal longTermAssetValue,
    BigDecimal totalNetWorth,
    BigDecimal liquidAssets,
    BigDecimal illiquidAssets,
    List<AllocationResponse> allocations,
    BigDecimal currentRentalIncome,
    BigDecimal currentBondIncome,
    BigDecimal retirementReserve,
    BigDecimal investmentCapital,
    IncomeResponse income,
    AllocationReconciliationResponse allocationReconciliation) {

  public static ProfileResponse from(ProfileSummary profile) {
    return new ProfileResponse(
        profile.portfolioId(),
        profile.currency(),
        profile.marketPortfolioValue(),
        profile.longTermAssetValue(),
        profile.totalNetWorth(),
        profile.liquidAssets(),
        profile.illiquidAssets(),
        profile.allocations().stream().map(AllocationResponse::from).toList(),
        profile.currentRentalIncome(),
        profile.currentBondIncome(),
        profile.retirementReserve(),
        profile.investmentCapital(),
        IncomeResponse.from(profile.incomeSummary()),
        AllocationReconciliationResponse.from(profile.allocationReconciliation()));
  }

  public record AllocationResponse(
      String bucket,
      BigDecimal value,
      BigDecimal percentage,
      String liquidity,
      String assetHorizon) {
    private static AllocationResponse from(ProfileAllocation allocation) {
      return new AllocationResponse(
          allocation.bucket().name(),
          allocation.value(),
          allocation.percentage(),
          allocation.liquidity().name(),
          allocation.assetHorizon().name());
    }
  }

  public record IncomeResponse(
      BigDecimal marketIncomeYtd,
      BigDecimal marketAnnualIncome,
      BigDecimal marketNetYield,
      BigDecimal longTermAnnualIncome,
      BigDecimal longTermNetYield,
      BigDecimal combinedAnnualIncome,
      BigDecimal combinedNetYield) {
    private static IncomeResponse from(ProfileIncomeSummary income) {
      return new IncomeResponse(
          income.marketIncomeYtd(),
          income.marketAnnualIncome(),
          income.marketNetYield(),
          income.longTermAnnualIncome(),
          income.longTermNetYield(),
          income.combinedAnnualIncome(),
          income.combinedNetYield());
    }
  }

  public record AllocationReconciliationResponse(
      SourceTotalResponse shortTerm, SourceTotalResponse longTerm, boolean balanced) {
    private static AllocationReconciliationResponse from(
        ProfileAllocationReconciliation reconciliation) {
      return new AllocationReconciliationResponse(
          SourceTotalResponse.from(reconciliation.shortTerm()),
          SourceTotalResponse.from(reconciliation.longTerm()),
          reconciliation.balanced());
    }
  }

  public record SourceTotalResponse(
      BigDecimal classifiedValue,
      BigDecimal authoritativeValue,
      BigDecimal delta,
      boolean balanced) {
    private static SourceTotalResponse from(ProfileAllocationReconciliation.SourceTotal source) {
      return new SourceTotalResponse(
          source.classifiedValue(), source.authoritativeValue(), source.delta(), source.balanced());
    }
  }
}
