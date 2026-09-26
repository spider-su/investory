package com.smartbox.investory.profile.api.contract;

import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/** Transport contract for the complete Profile REST response. */
public record ProfileResponse(
    Long portfolioId,
    CurrencyType currency,
    BigDecimal marketPortfolioValue,
    BigDecimal longTermAssetValue,
    BigDecimal totalNetWorth,
    BigDecimal liquidAssets,
    BigDecimal illiquidAssets,
    List<ProfileAllocation> allocations,
    BigDecimal currentRentalIncome,
    BigDecimal currentBondIncome,
    ProfileAssetProjection longTermPlanningState,
    BigDecimal retirementReserve,
    BigDecimal investmentCapital,
    ProfileIncomeSummary incomeSummary,
    ProfileAllocationReconciliation allocationReconciliation) {}
