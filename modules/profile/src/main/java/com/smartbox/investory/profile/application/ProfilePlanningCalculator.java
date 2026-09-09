package com.smartbox.investory.profile.application;

import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.api.model.RentalContractProjectionModel;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Maps Long-Term projection facts into the profile planning model. */
final class ProfilePlanningCalculator {
  private final ProfileAllocationCalculator allocations;
  private final ProfileCurrencyNormalizer currencyNormalizer;

  ProfilePlanningCalculator(
      ProfileAllocationCalculator allocations, ProfileCurrencyNormalizer currencyNormalizer) {
    this.allocations = allocations;
    this.currencyNormalizer = currencyNormalizer;
  }

  ProfileAssetProjection state(
      List<LongTermAssetProjectionModel> inputs, CurrencyType baseCurrency, LocalDate date) {
    // Rental growth belongs to the Retirement scenario, not to persisted Long-Term facts.
    return new ProfileAssetProjection(
        inputs.stream().map(input -> asset(input, baseCurrency, date)).toList(),
        BigDecimal.ZERO,
        date.getYear(),
        ProjectionSource.PROJECTED);
  }

  private ProjectedLongTermAsset asset(
      LongTermAssetProjectionModel input, CurrencyType baseCurrency, LocalDate date) {
    var bucket = allocations.classify(input.category());
    return new ProjectedLongTermAsset(
        input.id(),
        input.name(),
        bucket,
        baseCurrency,
        currencyNormalizer.toBase(input.currentValue(), input.currency(), baseCurrency, date),
        allocations.liquidity(input.category(), input.fundingAvailable()),
        input.periods().stream()
            .map(
                period ->
                    new ProjectedLongTermAsset.Period(
                        period.validFrom(),
                        period.validTo(),
                        currencyNormalizer.toBase(
                            period.annualIncome(), input.currency(), baseCurrency, date),
                        currencyNormalizer.toBase(
                            period.annualExpense(), input.currency(), baseCurrency, date),
                        period.annualReturnRate(),
                        period.cashFlowType(),
                        period.paidByTenant()))
            .toList(),
        input.rentalContracts().stream()
            .map(contract -> rentalContract(contract, input.currency(), baseCurrency, date))
            .toList(),
        input.maturityDate());
  }

  private RentalContractProjectionModel rentalContract(
      RentalContractProjectionModel contract,
      CurrencyType sourceCurrency,
      CurrencyType baseCurrency,
      LocalDate date) {
    return new RentalContractProjectionModel(
        contract.id(),
        contract.startDate(),
        contract.endDate(),
        contract.terminatedDate(),
        contract.terms().stream()
            .map(
                term ->
                    new RentalContractModel.Term(
                        term.type(),
                        currencyNormalizer.toBase(
                            term.amount(), sourceCurrency, baseCurrency, date),
                        term.frequency(),
                        term.paidByTenant()))
            .toList());
  }
}
