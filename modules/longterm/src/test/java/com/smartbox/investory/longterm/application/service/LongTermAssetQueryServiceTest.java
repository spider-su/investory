package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.deposit.LongTermAssetDepositDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyRepository;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodRepository;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongTermAssetQueryServiceTest {
  private LongTermAssetRepository assets;
  private LongTermAssetRelatedDataLoader relatedData;
  private LongTermAssetQueryService service;

  @BeforeEach
  void setUp() {
    assets = mock(LongTermAssetRepository.class);
    relatedData = mock(LongTermAssetRelatedDataLoader.class);
    service =
        new LongTermAssetQueryService(
            assets,
            mock(LongTermAssetValuationPeriodRepository.class),
            mock(RentalTaxPolicyRepository.class),
            mock(PortfolioContextReader.class),
            mock(CurrencyConversion.class),
            mock(LongTermAssetRentalContractRepository.class),
            relatedData);
  }

  @Test
  void emptyPortfolioReturnsNoRowsWithoutLoadingRelatedData() {
    LocalDate date = LocalDate.of(2026, 1, 1);
    when(assets.findAllByPortfolioIdAndActiveTrueOrderByName(1L)).thenReturn(List.of());

    assertThat(service.list(1L, date)).isEmpty();
    verifyNoInteractions(relatedData);
  }

  @Test
  void rejectsMissingDateAtTheQueryBoundary() {
    assertThatThrownBy(() -> service.list(1L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("date");
  }

  @Test
  void mapsDepositEconomicsAndUsesHistoryFloorForRelatedData() {
    var deposit = asset(5L, LongTermAssetType.DEPOSIT);
    var details = new LongTermAssetDepositDetailsEntity();
    details.setAssetId(5L);
    details.setAnnualInterestRate(new BigDecimal("0.05"));
    details.setTaxRate(new BigDecimal("0.19"));
    details.setMaturityDate(LocalDate.of(2028, 1, 1));
    when(assets.findAllByPortfolioIdAndActiveTrueOrderByName(1L)).thenReturn(List.of(deposit));
    when(relatedData.load(1L, List.of(5L), LocalDate.of(2025, 1, 1)))
        .thenReturn(
            new LongTermAssetRelatedDataLoader.Data(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(5L, details),
                new BigDecimal("0.085")));

    var result = service.list(1L, LocalDate.of(2024, 1, 1)).getFirst();

    assertThat(result.currentAnnualRate()).isEqualByComparingTo("0.05");
    assertThat(result.annualEconomics().grossAnnualIncome()).isEqualByComparingTo("50");
    assertThat(result.annualEconomics().annualTax()).isEqualByComparingTo("9.5");
    assertThat(result.maturityDate()).isEqualTo(LocalDate.of(2028, 1, 1));
  }

  private static LongTermAssetEntity asset(Long id, LongTermAssetType type) {
    var asset = new LongTermAssetEntity();
    asset.setId(id);
    asset.setPortfolioId(1L);
    asset.setName(type.name());
    asset.setType(type);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("1000"));
    asset.setAcquisitionValue(new BigDecimal("900"));
    asset.setActive(true);
    return asset;
  }
}
