package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LongTermAssetsFacadeCrudTest {

  @ParameterizedTest
  @EnumSource(LongTermAssetType.class)
  void supportsCreateReadUpdateAndSoftDeleteForEveryAssetType(LongTermAssetType type) {
    var service = mock(LongTermAssetService.class);
    var contracts = mock(RentalContractService.class);
    var stored = new AtomicReference<LongTermAssetEntity>();
    when(service.save(any(LongTermAssetEntity.class)))
        .thenAnswer(
            invocation -> {
              LongTermAssetEntity asset = invocation.getArgument(0);
              if (asset.getId() == null) asset.setId(7L);
              stored.set(asset);
              return asset;
            });
    when(service.get(1L, 7L)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));

    var facade = new LongTermAssetsFacade(service, contracts);
    var created = facade.create(command(type, null, "Created " + type));

    assertThat(created.id()).isEqualTo(7L);
    assertThat(created.portfolioId()).isEqualTo(1L);
    assertThat(created.type()).isEqualTo(type);
    assertThat(created.name()).isEqualTo("Created " + type);

    var read = facade.asset(1L, 7L);
    assertThat(read.id()).isEqualTo(7L);
    assertThat(read.type()).isEqualTo(type);
    assertThat(read.currentValue()).isEqualByComparingTo("1200");

    facade.update(command(type, 7L, "Updated " + type));
    assertThat(stored.get().getName()).isEqualTo("Updated " + type);
    assertThat(stored.get().getCurrentValue()).isEqualByComparingTo("1500");
    verify(service, times(2)).save(stored.get());

    facade.archive(1L, 7L);
    verify(service).archive(1L, 7L);
    facade.reactivate(1L, 7L);
    verify(service).reactivate(1L, 7L);
  }

  private static LongTermAssetsFacade.AssetCommand command(
      LongTermAssetType type, Long id, String name) {
    return new LongTermAssetsFacade.AssetCommand(
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
        "notes");
  }
}
