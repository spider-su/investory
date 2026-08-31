package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;

@DisplayName("Long Term Assets Facade Crud")
class LongTermAssetsFacadeCrudTest {
  private static LongTermAssetsFacade facade(
      LongTermAssetQueryService queries,
      LongTermAssetCommandService commands,
      RentalContractService contracts) {
    return new LongTermAssetsFacade(
        queries, mock(LongTermAssetAnnualSnapshotService.class), commands, contracts);
  }

  @org.junit.jupiter.api.Test
  void bondCrudPersistsYieldAsDecimalRatePeriod() {
    var queries = mock(LongTermAssetQueryService.class);
    var commands = mock(LongTermAssetCommandService.class);
    var contracts = mock(RentalContractService.class);
    var stored = new LongTermAssetEntity();
    stored.setId(8L);
    stored.setPortfolioId(1L);
    stored.setType(LongTermAssetType.BOND);
    stored.setName("Bond");
    stored.setCurrency(CurrencyType.PLN);
    stored.setCurrentValue(new BigDecimal("150000"));
    when(commands.save(any(LongTermAssetEntity.class))).thenReturn(stored);

    var facade = facade(queries, commands, contracts);
    facade.createBond(
        new com.smartbox.investory.longterm.api.model.BondCommand(
            1L,
            null,
            "Bond",
            CurrencyType.PLN,
            new BigDecimal("150000"),
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2028, 1, 1),
            com.smartbox.investory.longterm.api.model.InterestTreatment.CAPITALIZE,
            new BigDecimal("0.0625"),
            null));

    verify(commands)
        .saveSimpleBond(
            1L,
            8L,
            new BigDecimal("0.0625"),
            LocalDate.of(2028, 1, 1),
            com.smartbox.investory.longterm.api.model.InterestTreatment.CAPITALIZE);
  }

  @org.junit.jupiter.api.Test
  void genericWorkflowSupportsOnlyOtherAssets() {
    LongTermAssetType type = LongTermAssetType.OTHER;
    var queries = mock(LongTermAssetQueryService.class);
    var commands = mock(LongTermAssetCommandService.class);
    var contracts = mock(RentalContractService.class);
    var stored = new AtomicReference<LongTermAssetEntity>();
    when(commands.save(any(LongTermAssetEntity.class)))
        .thenAnswer(
            invocation -> {
              LongTermAssetEntity asset = invocation.getArgument(0);
              if (asset.getId() == null) asset.setId(7L);
              stored.set(asset);
              return asset;
            });
    when(queries.get(1L, 7L)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));

    var facade = facade(queries, commands, contracts);
    var created = facade.create(command(type, null, "Created " + type));

    assertThat(created.id()).isEqualTo(7L);
    assertThat(created.portfolioId()).isEqualTo(1L);
    assertThat(created.type()).isEqualTo(LongTermAssetType.OTHER);
    assertThat(created.name()).isEqualTo("Created " + type);

    var read = facade.asset(1L, 7L);
    assertThat(read.id()).isEqualTo(7L);
    assertThat(read.type()).isEqualTo(type);
    assertThat(read.currentValue()).isEqualByComparingTo("1200");

    facade.update(command(type, 7L, "Updated " + type));
    assertThat(stored.get().getName()).isEqualTo("Updated " + type);
    assertThat(stored.get().getCurrentValue()).isEqualByComparingTo("1500");
    verify(commands, times(2)).save(stored.get());

    facade.archive(1L, 7L);
    verify(commands).archive(1L, 7L);
    facade.reactivate(1L, 7L);
    verify(commands).reactivate(1L, 7L);
  }

  @org.junit.jupiter.api.Test
  void genericWorkflowRejectsSpecializedAssets() {
    var facade =
        facade(
            mock(LongTermAssetQueryService.class),
            mock(LongTermAssetCommandService.class),
            mock(RentalContractService.class));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> facade.create(command(LongTermAssetType.BOND, null, "Bond")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("subtype workflow");
  }

  private static com.smartbox.investory.longterm.api.model.AssetCommand command(
      LongTermAssetType type, Long id, String name) {
    return new com.smartbox.investory.longterm.api.model.AssetCommand(
        1L,
        id,
        name,
        type,
        CurrencyType.PLN,
        LocalDate.of(2025, 1, 1),
        new BigDecimal("1000"),
        new BigDecimal(id == null ? "1200" : "1500"),
        null,
        true,
        "notes",
        false);
  }
}
