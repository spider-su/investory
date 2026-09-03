package com.smartbox.investory.profile.application;

import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Maps Long-Term projection facts into the profile planning model. */
final class ProfilePlanningCalculator {
  private final ProfileAllocationCalculator allocations;

  ProfilePlanningCalculator(ProfileAllocationCalculator allocations) {
    this.allocations = allocations;
  }

  ProfileAssetProjection state(List<LongTermAssetProjectionModel> inputs, LocalDate date) {
    return new ProfileAssetProjection(
        inputs.stream().map(this::asset).toList(),
        BigDecimal.ZERO,
        date.getYear(),
        ProjectionSource.PROJECTED);
  }

  private ProjectedLongTermAsset asset(LongTermAssetProjectionModel input) {
    var bucket = allocations.classify(input.type());
    return new ProjectedLongTermAsset(
        input.id(),
        input.name(),
        input.type(),
        bucket,
        input.currency(),
        input.currentValue(),
        allocations.liquidity(bucket),
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
        input.maturityDate(),
        input.redemptionValue(),
        input.interestTreatment(),
        input.taxRate(),
        input.taxBase(),
        input.rentalTaxPaidByTenant());
  }
}
