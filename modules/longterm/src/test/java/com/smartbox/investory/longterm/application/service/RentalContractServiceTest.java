package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RentalContractServiceTest {
  @Test
  void rejectsContractsForNonRealEstateAssets() {
    var assets = mock(LongTermAssetRepository.class);
    var contracts = mock(LongTermAssetRentalContractRepository.class);
    var asset = new LongTermAssetEntity();
    asset.setId(7L);
    asset.setPortfolioId(1L);
    asset.setType(LongTermAssetType.BOND);
    when(assets.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(asset));

    var service = new RentalContractService(assets, contracts);

    assertThatThrownBy(
            () ->
                service.create(
                    1L, 7L, java.time.LocalDate.of(2026, 1, 1), null, java.util.List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("real-estate");
  }
}
