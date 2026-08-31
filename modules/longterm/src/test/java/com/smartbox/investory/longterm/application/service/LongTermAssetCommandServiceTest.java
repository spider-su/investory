package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondDetailsRepository;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodRepository;
import com.smartbox.investory.longterm.infrastructure.deposit.LongTermAssetDepositDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.deposit.LongTermAssetDepositDetailsRepository;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongTermAssetCommandServiceTest {
  private LongTermAssetRepository assets;
  private LongTermAssetValuationPeriodRepository valuations;
  private LongTermAssetBondRatePeriodRepository bondRates;
  private LongTermAssetBondDetailsRepository bonds;
  private LongTermAssetDepositDetailsRepository deposits;
  private LongTermAssetLifecycleService lifecycle;
  private LongTermAssetPeriodService periods;
  private LongTermAssetCommandService service;

  @BeforeEach
  void setUp() {
    assets = mock(LongTermAssetRepository.class);
    valuations = mock(LongTermAssetValuationPeriodRepository.class);
    bondRates = mock(LongTermAssetBondRatePeriodRepository.class);
    bonds = mock(LongTermAssetBondDetailsRepository.class);
    deposits = mock(LongTermAssetDepositDetailsRepository.class);
    lifecycle = mock(LongTermAssetLifecycleService.class);
    periods = mock(LongTermAssetPeriodService.class);
    service =
        new LongTermAssetCommandService(
            assets,
            valuations,
            bondRates,
            bonds,
            deposits,
            lifecycle,
            mock(LongTermAssetQueryService.class),
            periods,
            Clock.fixed(Instant.parse("2026-02-03T00:00:00Z"), ZoneOffset.UTC));
    when(assets.save(any(LongTermAssetEntity.class)))
        .thenAnswer(
            call -> {
              LongTermAssetEntity asset = call.getArgument(0);
              if (asset.getId() == null) asset.setId(5L);
              return asset;
            });
  }

  @Test
  void saveRejectsIncompleteAndNegativeAssets() {
    var asset = new LongTermAssetEntity();
    assertThatThrownBy(() -> service.save(asset)).hasMessage("Portfolio is required");
    asset.setPortfolioId(1L);
    asset.setName(" ");
    assertThatThrownBy(() -> service.save(asset)).hasMessage("Name is required");
    asset.setName("Asset");
    asset.setType(LongTermAssetType.OTHER);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("-1"));
    assertThatThrownBy(() -> service.save(asset)).hasMessage("Current value must be non-negative");
  }

  @Test
  void savePreservesSubtypeAndRejectsOrphanedDetails() {
    var asset = validAsset(5L, LongTermAssetType.OTHER);
    when(assets.findByIdAndPortfolioId(5L, 1L)).thenReturn(Optional.of(asset));
    when(assets.findTypeByIdAndPortfolioId(5L, 1L)).thenReturn(Optional.of(LongTermAssetType.BOND));
    assertThatThrownBy(() -> service.save(asset)).hasMessageContaining("type cannot be changed");

    when(assets.findTypeByIdAndPortfolioId(5L, 1L))
        .thenReturn(Optional.of(LongTermAssetType.OTHER));
    when(bonds.existsById(5L)).thenReturn(true);
    assertThatThrownBy(() -> service.save(asset)).hasMessageContaining("Bond details");
  }

  @Test
  void saveInitializesLifecycleForValidAsset() {
    var asset = validAsset(null, LongTermAssetType.OTHER);
    assertThat(service.save(asset).getId()).isEqualTo(5L);
    verify(lifecycle).ensureInitialPeriod(asset);
  }

  @Test
  void cashReserveCreationUsesCanonicalRateAndEffectiveDate() {
    LongTermAssetEntity result =
        service.saveCashReserve(
            1L,
            null,
            "Reserve",
            CurrencyType.PLN,
            new BigDecimal("12000"),
            new BigDecimal("0.04"),
            "liquid",
            LocalDate.of(2026, 1, 1));

    assertThat(result.getType()).isEqualTo(LongTermAssetType.CASH_RESERVE);
    assertThat(result.getAcquisitionValue()).isEqualByComparingTo("12000");
    verify(periods)
        .replaceCurrentValuationGrowth(5L, LocalDate.of(2026, 1, 1), new BigDecimal("0.04"));
  }

  @Test
  void cashReserveUpdateRejectsTypeAndCurrencyChanges() {
    var property = validAsset(5L, LongTermAssetType.REAL_ESTATE);
    when(assets.findByIdAndPortfolioId(5L, 1L)).thenReturn(Optional.of(property));
    assertThatThrownBy(
            () ->
                service.saveCashReserve(
                    1L, 5L, "R", CurrencyType.PLN, BigDecimal.ONE, null, null, LocalDate.now()))
        .hasMessageContaining("type cannot be changed");

    var reserve = validAsset(5L, LongTermAssetType.CASH_RESERVE);
    when(assets.findByIdAndPortfolioId(5L, 1L)).thenReturn(Optional.of(reserve));
    assertThatThrownBy(
            () ->
                service.saveCashReserve(
                    1L, 5L, "R", CurrencyType.USD, BigDecimal.ONE, null, null, LocalDate.now()))
        .hasMessageContaining("currency cannot be changed");
  }

  @Test
  void bondDetailsRequireBondMaturityAndCanonicalTaxRate() {
    var bond = validAsset(5L, LongTermAssetType.BOND);
    bond.setAcquisitionDate(LocalDate.of(2026, 1, 1));
    when(assets.findByIdAndPortfolioId(5L, 1L)).thenReturn(Optional.of(bond));
    var details = new LongTermAssetBondDetailsEntity();
    details.setMaturityDate(LocalDate.of(2025, 12, 31));
    assertThatThrownBy(() -> service.saveBondDetails(1L, 5L, details))
        .hasMessageContaining("maturity");

    details.setMaturityDate(LocalDate.of(2030, 1, 1));
    when(bonds.save(details)).thenReturn(details);
    assertThat(service.saveBondDetails(1L, 5L, details).getTaxRate()).isEqualByComparingTo("0.19");
  }

  @Test
  void simpleBondDefaultsRedemptionAndReplacesRatePeriod() {
    var bond = validAsset(5L, LongTermAssetType.BOND);
    bond.setAcquisitionDate(null);
    when(assets.findByIdAndPortfolioId(5L, 1L)).thenReturn(Optional.of(bond));
    when(bonds.findById(5L)).thenReturn(Optional.empty());
    when(bonds.save(any())).thenAnswer(call -> call.getArgument(0));

    service.saveSimpleBond(
        1L, 5L, new BigDecimal("0.06"), LocalDate.of(2030, 1, 1), InterestTreatment.CAPITALIZE);

    verify(periods)
        .replaceBondRate(
            5L, LocalDate.of(2026, 2, 3), LocalDate.of(2030, 1, 1), new BigDecimal("0.06"));
  }

  @Test
  void depositsRequireSubtypeMaturityAndValidRate() {
    var deposit = validAsset(5L, LongTermAssetType.DEPOSIT);
    deposit.setAcquisitionDate(LocalDate.of(2026, 1, 1));
    when(assets.findByIdAndPortfolioId(5L, 1L)).thenReturn(Optional.of(deposit));
    var details = new LongTermAssetDepositDetailsEntity();
    assertThatThrownBy(() -> service.saveDepositDetails(1L, 5L, details))
        .hasMessage("Deposit maturity is required");

    details.setMaturityDate(LocalDate.of(2030, 1, 1));
    details.setAnnualInterestRate(new BigDecimal("1.01"));
    assertThatThrownBy(() -> service.saveDepositDetails(1L, 5L, details))
        .hasMessageContaining("Deposit interest rate");
  }

  @Test
  void ownedMutationsRejectForeignAssetsAndDelegateLifecycle() {
    when(assets.findByIdAndPortfolioId(8L, 1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.updateTaxBase(1L, 8L, BigDecimal.ONE))
        .isInstanceOf(AssetNotFoundException.class);
    service.archive(1L, 8L);
    service.reactivate(1L, 8L);
    verify(lifecycle).archive(1L, 8L);
    verify(lifecycle).reactivate(1L, 8L);
  }

  private static LongTermAssetEntity validAsset(Long id, LongTermAssetType type) {
    var asset = new LongTermAssetEntity();
    asset.setId(id);
    asset.setPortfolioId(1L);
    asset.setName("Asset");
    asset.setType(type);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("100"));
    asset.setAcquisitionValue(new BigDecimal("90"));
    asset.setActive(true);
    return asset;
  }
}
