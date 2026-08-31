package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LongTermAssetsFacadeTest {
  @Test
  void detailsLoadsContractsOnlyForRealEstate() {
    var service = mock(LongTermAssetService.class);
    var contracts = mock(RentalContractService.class);
    var facade = new LongTermAssetsFacade(service, contracts);
    for (LongTermAssetType type : LongTermAssetType.values()) {
      var asset = asset(type);
      when(service.get(1L, 7L)).thenReturn(Optional.of(asset));
      when(service.valuationPeriods(1L, 7L)).thenReturn(List.of());
      when(service.bondRatePeriods(1L, 7L)).thenReturn(List.of());
      when(service.bondDetails(1L, 7L)).thenReturn(Optional.empty());
      when(service.depositDetails(1L, 7L)).thenReturn(Optional.empty());
      when(service.summary(any(), any())).thenReturn(null);
      when(service.expectedPropertyGrowth(1L, 7L, LocalDate.of(2026, 1, 1)))
          .thenReturn(BigDecimal.ZERO);
      when(contracts.list(1L, 7L)).thenReturn(List.of());

      assertThat(facade.details(1L, 7L, LocalDate.of(2026, 1, 1)).contracts()).isEmpty();
      if (type == LongTermAssetType.REAL_ESTATE) verify(contracts).list(1L, 7L);
      else verify(contracts, never()).list(1L, 7L);
      clearInvocations(contracts);
    }
  }

  @Test
  void bondUpdateChangesCurrentValueWithoutOverwritingAcquisitionValue() {
    var service = mock(LongTermAssetService.class);
    var contracts = mock(RentalContractService.class);
    var facade = new LongTermAssetsFacade(service, contracts);
    var bond = asset(LongTermAssetType.BOND);
    bond.setAcquisitionValue(new BigDecimal("100"));
    bond.setCurrentValue(new BigDecimal("120"));
    when(service.get(1L, 7L)).thenReturn(Optional.of(bond));

    facade.updateBond(
        new LongTermAssetsFacade.BondCommand(
            1L, 7L, "Bond", CurrencyType.PLN, new BigDecimal("150"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2030, 1, 1),
            InterestTreatment.PAY_OUT, new BigDecimal("5"), "notes"));

    assertThat(bond.getAcquisitionValue()).isEqualByComparingTo("100");
    assertThat(bond.getCurrentValue()).isEqualByComparingTo("150");
    verify(service).save(bond);
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
}
