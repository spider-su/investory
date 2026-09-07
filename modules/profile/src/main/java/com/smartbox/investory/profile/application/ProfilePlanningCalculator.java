package com.smartbox.investory.profile.application;

import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Maps Long-Term projection facts into the profile planning model. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ProfilePlanningCalculator {
  private final ProfileAllocationCalculator allocations;

  ProfileAssetProjection state(List<LongTermAssetProjectionModel> inputs, LocalDate date) {
    return new ProfileAssetProjection(
        inputs.stream().map(input -> asset(input, date)).toList(),
        BigDecimal.ZERO,
        date.getYear(),
        ProjectionSource.PROJECTED);
  }

  private ProjectedLongTermAsset asset(LongTermAssetProjectionModel input, LocalDate date) {
    var bucket = allocations.classify(input.category());
    return new ProjectedLongTermAsset(
        input.id(),
        input.name(),
        bucket,
        input.currency(),
        input.currentValue(),
        allocations.liquidity(input.category(), input.fundingAvailable()),
        input.periods().stream()
            .map(
                period ->
                    new ProjectedLongTermAsset.Period(
                        period.validFrom(),
                        period.validTo(),
                        period.annualIncome(),
                        period.annualExpense(),
                        period.annualReturnRate(),
                        period.cashFlowType(),
                        period.paidByTenant()))
            .toList(),
        input.rentalContracts(),
        input.maturityDate());
  }
}
