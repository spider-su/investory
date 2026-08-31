package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodRepository;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyEntity;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyRepository;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodEntity;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongTermAssetPeriodServiceTest {
  private LongTermAssetRepository assets;
  private LongTermAssetValuationPeriodRepository valuations;
  private LongTermAssetBondRatePeriodRepository bondRates;
  private RentalTaxPolicyRepository taxPolicies;
  private LongTermAssetPeriodService service;

  @BeforeEach
  void setUp() {
    assets = mock(LongTermAssetRepository.class);
    valuations = mock(LongTermAssetValuationPeriodRepository.class);
    bondRates = mock(LongTermAssetBondRatePeriodRepository.class);
    taxPolicies = mock(RentalTaxPolicyRepository.class);
    service = new LongTermAssetPeriodService(assets, valuations, bondRates, taxPolicies);
  }

  @Test
  void taxPoliciesRejectOverlapAndForeignUpdates() {
    RentalTaxPolicyEntity existing = policy(7L, 1L, "0.085", "2026-01-01", null);
    when(taxPolicies.findAllByPortfolioIdOrderByValidFrom(1L)).thenReturn(List.of(existing));

    assertThatThrownBy(
            () -> service.saveRentalTaxPolicy(1L, policy(null, null, "0.12", "2026-02-01", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Overlapping");

    RentalTaxPolicyEntity foreign = policy(9L, 2L, "0.085", "2026-01-01", null);
    when(taxPolicies.findById(9L)).thenReturn(Optional.of(foreign));
    assertThatThrownBy(() -> service.saveRentalTaxPolicy(1L, foreign))
        .isInstanceOf(RentalTaxPolicyNotFoundException.class);
  }

  @Test
  void taxPolicySaveSetsOwnerAndAcceptsAdjacentRanges() {
    RentalTaxPolicyEntity existing = policy(7L, 1L, "0.085", "2026-01-01", "2026-06-30");
    RentalTaxPolicyEntity next = policy(null, null, "0.12", "2026-07-01", null);
    when(taxPolicies.findAllByPortfolioIdOrderByValidFrom(1L)).thenReturn(List.of(existing));
    when(taxPolicies.save(next)).thenReturn(next);

    assertThat(service.saveRentalTaxPolicy(1L, next)).isSameAs(next);
    assertThat(next.getPortfolioId()).isEqualTo(1L);
  }

  @Test
  void replacingGrowthClosesPastPeriodDeletesFutureAndCreatesOpenPeriod() {
    var past = valuation(1L, "2025-01-01", null, "0.02");
    var future = valuation(2L, "2027-01-01", null, "0.03");
    when(valuations.findAllByAssetIdOrderByValidFrom(5L))
        .thenReturn(List.of(past, future))
        .thenReturn(List.of(past));

    service.replaceValuationGrowth(5L, LocalDate.of(2026, 1, 1), new BigDecimal("0.04"));

    assertThat(past.getValidTo()).isEqualTo(LocalDate.of(2025, 12, 31));
    verify(valuations).delete(future);
    verify(valuations).save(past);
    verify(valuations, times(2)).save(any(LongTermAssetValuationPeriodEntity.class));
  }

  @Test
  void nullGrowthOnlyClosesExistingPeriods() {
    var current = valuation(1L, "2025-01-01", null, "0.02");
    when(valuations.findAllByAssetIdOrderByValidFrom(5L)).thenReturn(List.of(current));

    service.replaceValuationGrowth(5L, LocalDate.of(2026, 1, 1), null);

    assertThat(current.getValidTo()).isEqualTo(LocalDate.of(2025, 12, 31));
    verify(valuations).save(current);
  }

  @Test
  void valuationPeriodRequiresOwnedRealEstateAndNoOverlap() {
    LongTermAssetEntity bond = asset(5L, LongTermAssetType.BOND);
    when(assets.findByIdAndPortfolioId(5L, 1L)).thenReturn(Optional.of(bond));
    assertThatThrownBy(
            () -> service.addValuationPeriod(1L, 5L, valuation(null, "2026-01-01", null, "0.03")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("real estate");

    LongTermAssetEntity property = asset(5L, LongTermAssetType.REAL_ESTATE);
    when(assets.findByIdAndPortfolioId(5L, 1L)).thenReturn(Optional.of(property));
    when(valuations.findAllByAssetIdOrderByValidFrom(5L))
        .thenReturn(List.of(valuation(3L, "2026-01-01", null, "0.02")));
    assertThatThrownBy(
            () -> service.addValuationPeriod(1L, 5L, valuation(null, "2026-06-01", null, "0.03")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Overlapping");
  }

  @Test
  void valuationUpdateAndDeleteEnforceAssetOwnership() {
    when(assets.findByIdAndPortfolioId(5L, 1L))
        .thenReturn(Optional.of(asset(5L, LongTermAssetType.REAL_ESTATE)));
    when(valuations.findById(8L))
        .thenReturn(Optional.of(valuationForAsset(8L, 99L, "2026-01-01", null, "0.02")));

    assertThatThrownBy(
            () ->
                service.updateValuationPeriod(
                    1L, 5L, 8L, valuation(null, "2026-01-01", null, "0.03")))
        .isInstanceOf(ValuationNotFoundException.class);
    verify(valuations, never()).save(any());
  }

  @Test
  void bondRateReplacementDeletesOldRowsAndValidatesRangeAndRate() {
    var old =
        new com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodEntity();
    when(bondRates.findAllByAssetIdOrderByValidFrom(5L)).thenReturn(List.of(old));

    service.replaceBondRate(
        5L, LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1), new BigDecimal("0.06"));
    verify(bondRates).deleteAll(List.of(old));
    verify(bondRates).flush();
    verify(bondRates).save(any());

    assertThatThrownBy(
            () ->
                service.replaceBondRate(
                    5L, LocalDate.of(2030, 1, 2), LocalDate.of(2030, 1, 1), new BigDecimal("0.06")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static LongTermAssetEntity asset(Long id, LongTermAssetType type) {
    var asset = new LongTermAssetEntity();
    asset.setId(id);
    asset.setPortfolioId(1L);
    asset.setType(type);
    return asset;
  }

  private static RentalTaxPolicyEntity policy(
      Long id, Long portfolioId, String rate, String from, String to) {
    var policy = new RentalTaxPolicyEntity();
    policy.setId(id);
    policy.setPortfolioId(portfolioId);
    policy.setRate(new BigDecimal(rate));
    policy.setValidFrom(LocalDate.parse(from));
    policy.setValidTo(to == null ? null : LocalDate.parse(to));
    return policy;
  }

  private static LongTermAssetValuationPeriodEntity valuation(
      Long id, String from, String to, String growth) {
    return valuationForAsset(id, 5L, from, to, growth);
  }

  private static LongTermAssetValuationPeriodEntity valuationForAsset(
      Long id, Long assetId, String from, String to, String growth) {
    var period = new LongTermAssetValuationPeriodEntity();
    period.setId(id);
    period.setAssetId(assetId);
    period.setValidFrom(LocalDate.parse(from));
    period.setValidTo(to == null ? null : LocalDate.parse(to));
    period.setExpectedAnnualGrowthRate(new BigDecimal(growth));
    return period;
  }
}
