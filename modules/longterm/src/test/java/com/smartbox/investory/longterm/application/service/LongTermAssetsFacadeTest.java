package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Long Term Assets Facade")
class LongTermAssetsFacadeTest {
  private static LongTermAssetsFacade facade(
      LongTermAssetQueryService queries,
      LongTermAssetCommandService commands,
      RentalContractService contracts) {
    return new LongTermAssetsFacade(
        queries, mock(LongTermAssetAnnualSnapshotService.class), commands, contracts);
  }

  @DisplayName("details Uses One Query Snapshot")
  @Test
  void detailsUsesOneQuerySnapshot() {
    var queries = mock(LongTermAssetQueryService.class);
    var commands = mock(LongTermAssetCommandService.class);
    var contracts = mock(RentalContractService.class);
    var facade = facade(queries, commands, contracts);
    LocalDate date = LocalDate.of(2026, 1, 1);
    var asset = asset(LongTermAssetType.BOND);
    when(queries.detail(1L, 7L, date))
        .thenReturn(
            new LongTermAssetQueryService.AssetDetailData(
                asset, summary(asset), null, null, List.of(), null, List.of()));

    assertThat(facade.details(1L, 7L, date).contracts()).isEmpty();

    verify(queries).detail(1L, 7L, date);
    verifyNoInteractions(contracts);
  }

  @DisplayName("bond Update Changes Current Value Without Overwriting Acquisition Value")
  @Test
  void bondUpdateChangesCurrentValueWithoutOverwritingAcquisitionValue() {
    var queries = mock(LongTermAssetQueryService.class);
    var commands = mock(LongTermAssetCommandService.class);
    var contracts = mock(RentalContractService.class);
    var facade = facade(queries, commands, contracts);
    var bond = asset(LongTermAssetType.BOND);
    bond.setAcquisitionValue(new BigDecimal("100"));
    bond.setCurrentValue(new BigDecimal("120"));
    when(queries.get(1L, 7L)).thenReturn(Optional.of(bond));

    facade.updateBond(
        new com.smartbox.investory.longterm.api.model.BondCommand(
            1L,
            7L,
            "Bond",
            CurrencyType.PLN,
            new BigDecimal("150"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2030, 1, 1),
            InterestTreatment.PAY_OUT,
            new BigDecimal("0.05"),
            "notes"));

    assertThat(bond.getAcquisitionValue()).isEqualByComparingTo("100");
    assertThat(bond.getCurrentValue()).isEqualByComparingTo("150");
    verify(commands).save(bond);
  }

  @Test
  void bondUpdateRejectsNonBondAsset() {
    var queries = mock(LongTermAssetQueryService.class);
    var commands = mock(LongTermAssetCommandService.class);
    var facade = facade(queries, commands, mock(RentalContractService.class));
    var other = asset(LongTermAssetType.OTHER);
    when(queries.get(1L, 7L)).thenReturn(Optional.of(other));

    assertThatThrownBy(
            () ->
                facade.updateBond(
                    new com.smartbox.investory.longterm.api.model.BondCommand(
                        1L,
                        7L,
                        "Other",
                        CurrencyType.PLN,
                        new BigDecimal("150"),
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2030, 1, 1),
                        InterestTreatment.PAY_OUT,
                        new BigDecimal("0.05"),
                        "notes")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AssetEntity is not a bond");
    verifyNoInteractions(commands);
  }

  @DisplayName("real Estate Details Map Tenant Effective End Status And Newest First Contracts")
  @Test
  void realEstateDetailsMapTenantEffectiveEndStatusAndNewestFirstContracts() {
    var queries = mock(LongTermAssetQueryService.class);
    var commands = mock(LongTermAssetCommandService.class);
    var rentalContracts = mock(RentalContractService.class);
    var facade = facade(queries, commands, rentalContracts);
    var asset = asset(LongTermAssetType.REAL_ESTATE);
    var newest = contract(12L, LocalDate.of(2026, 7, 1), null, "Newest tenant");
    var older = contract(11L, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 6, 30), "Older tenant");
    when(queries.detail(1L, 7L, LocalDate.of(2026, 8, 1)))
        .thenReturn(
            new LongTermAssetQueryService.AssetDetailData(
                asset,
                summary(asset),
                null,
                null,
                List.of(),
                BigDecimal.ZERO,
                List.of(newest, older)));

    var contracts = facade.details(1L, 7L, LocalDate.of(2026, 8, 1)).contracts();

    assertThat(contracts).extracting(RentalContractView::id).containsExactly(12L, 11L);
    assertThat(contracts.getFirst().tenantName()).isEqualTo("Newest tenant");
    assertThat(contracts.getFirst().effectiveEndDate()).isNull();
    assertThat(contracts.getFirst().status().name()).isEqualTo("CURRENT");
    assertThat(contracts.getLast().effectiveEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    assertThat(contracts.getLast().status().name()).isEqualTo("ENDED");
  }

  private static LongTermAssetEntity asset(LongTermAssetType type) {
    var asset = new LongTermAssetEntity();
    asset.setId(7L);
    asset.setPortfolioId(1L);
    asset.setName(type.name());
    asset.setType(type);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("1000"));
    asset.setAcquisitionValue(new BigDecimal("1000"));
    asset.setActive(true);
    return asset;
  }

  private static LongTermAssetRentalContractEntity contract(
      Long id, LocalDate start, LocalDate end, String tenant) {
    var contract = new LongTermAssetRentalContractEntity();
    contract.setId(id);
    contract.setAssetId(7L);
    contract.setStartDate(start);
    contract.setEndDate(end);
    contract.setTenantName(tenant);
    return contract;
  }

  private static LongTermAssetSummary summary(LongTermAssetEntity asset) {
    BigDecimal zero = BigDecimal.ZERO;
    return new LongTermAssetSummary(
        asset.getId(),
        asset.getName(),
        asset.getType(),
        asset.getCurrency(),
        asset.getCurrentValue(),
        null,
        zero,
        new AnnualEconomics(zero, zero, zero, zero, zero, zero, zero, zero),
        null,
        null,
        null);
  }
}
