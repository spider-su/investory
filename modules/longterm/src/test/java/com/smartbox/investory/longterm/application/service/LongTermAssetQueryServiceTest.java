package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
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
  private LongTermAssetLifecycleService lifecycle;
  private LongTermAssetQueryService service;

  @BeforeEach
  void setUp() {
    assets = mock(LongTermAssetRepository.class);
    relatedData = mock(LongTermAssetRelatedDataLoader.class);
    lifecycle = mock(LongTermAssetLifecycleService.class);
    when(lifecycle.activeAt(anyList(), any(LocalDate.class)))
        .thenAnswer(
            invocation -> {
              List<LongTermAssetEntity> rows = invocation.getArgument(0);
              LocalDate date = invocation.getArgument(1);
              return rows.stream().filter(asset -> lifecycle.activeOn(asset, date)).toList();
            });
    service =
        new LongTermAssetQueryService(
            mock(PortfolioContextReader.class),
            mock(CurrencyConversion.class),
            new LongTermAssetPopulationLoader(
                assets,
                relatedData,
                lifecycle,
                mock(LongTermAssetValuationPeriodRepository.class),
                mock(RentalTaxPolicyRepository.class),
                mock(LongTermAssetRentalContractRepository.class)),
            new LongTermAssetEconomicsCalculator(),
            new LongTermAssetPageAssembler(mock(CurrencyConversion.class)));
  }

  @Test
  void emptyPortfolioReturnsNoRowsWithoutLoadingRelatedData() {
    LocalDate date = LocalDate.of(2026, 1, 1);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of());

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
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(deposit));
    when(lifecycle.activeOn(deposit, LocalDate.of(2025, 1, 1))).thenReturn(true);
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

  @Test
  void historicalListUsesLifecyclePopulationInsteadOfCurrentActiveFlag() {
    LocalDate date = LocalDate.of(2026, 1, 1);
    var activeNow = asset(1L, LongTermAssetType.OTHER);
    var archivedToday = asset(2L, LongTermAssetType.OTHER);
    var archivedBefore = asset(3L, LongTermAssetType.OTHER);
    var acquiredAfter = asset(4L, LongTermAssetType.OTHER);
    var reactivated = asset(5L, LongTermAssetType.OTHER);
    var rows = List.of(activeNow, archivedToday, archivedBefore, acquiredAfter, reactivated);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(rows);
    when(lifecycle.activeOn(activeNow, date)).thenReturn(true);
    when(lifecycle.activeOn(archivedToday, date)).thenReturn(true);
    when(lifecycle.activeOn(archivedBefore, date)).thenReturn(false);
    when(lifecycle.activeOn(acquiredAfter, date)).thenReturn(false);
    when(lifecycle.activeOn(reactivated, date)).thenReturn(true);
    when(relatedData.load(1L, List.of(1L, 2L, 5L), date))
        .thenReturn(LongTermAssetRelatedDataLoader.Data.empty());

    assertThat(service.list(1L, date))
        .extracting(LongTermAssetSummary::id)
        .containsExactly(1L, 2L, 5L);
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
