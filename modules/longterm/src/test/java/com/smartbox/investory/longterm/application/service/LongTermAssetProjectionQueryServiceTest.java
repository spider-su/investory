package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodEntity;
import com.smartbox.investory.longterm.infrastructure.deposit.LongTermAssetDepositDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyRepository;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodEntity;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongTermAssetProjectionQueryServiceTest {
  private LongTermAssetRepository assets;
  private LongTermAssetLifecycleService lifecycle;
  private LongTermAssetRelatedDataLoader relatedData;
  private LongTermAssetProjectionQueryService service;

  @BeforeEach
  void setUp() {
    assets = mock(LongTermAssetRepository.class);
    lifecycle = mock(LongTermAssetLifecycleService.class);
    when(lifecycle.activeAt(anyList(), any(LocalDate.class)))
        .thenAnswer(
            invocation -> {
              List<LongTermAssetEntity> rows = invocation.getArgument(0);
              LocalDate date = invocation.getArgument(1);
              return rows.stream().filter(asset -> lifecycle.activeOn(asset, date)).toList();
            });
    relatedData = mock(LongTermAssetRelatedDataLoader.class);
    service =
        new LongTermAssetProjectionQueryService(
            new LongTermAssetPopulationLoader(
                assets,
                relatedData,
                lifecycle,
                mock(LongTermAssetValuationPeriodRepository.class),
                mock(RentalTaxPolicyRepository.class),
                mock(LongTermAssetRentalContractRepository.class)));
  }

  @Test
  void rejectsNullDateAndAvoidsRelatedQueriesForEmptyPortfolio() {
    assertThatThrownBy(() -> service.projectionInputs(1L, null))
        .isInstanceOf(IllegalArgumentException.class);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of());
    assertThat(service.projectionInputs(1L, LocalDate.of(2026, 1, 1))).isEmpty();
    verifyNoInteractions(relatedData);
  }

  @Test
  void mapsBondTermsRatesTaxAndRedemption() {
    var bond = asset(5L, LongTermAssetType.BOND);
    var details = new LongTermAssetBondDetailsEntity();
    details.setAssetId(5L);
    details.setMaturityDate(LocalDate.of(2030, 1, 1));
    details.setRedemptionValue(new BigDecimal("1200"));
    details.setInterestTreatment(InterestTreatment.CAPITALIZE);
    details.setTaxRate(null);
    var rate = new LongTermAssetBondRatePeriodEntity();
    rate.setAssetId(5L);
    rate.setValidFrom(LocalDate.of(2026, 1, 1));
    rate.setValidTo(LocalDate.of(2030, 1, 1));
    rate.setAnnualInterestRate(new BigDecimal("0.06"));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(bond));
    when(lifecycle.activeOn(bond, LocalDate.of(2026, 1, 1))).thenReturn(true);
    when(relatedData.load(1L, List.of(5L), LocalDate.of(2026, 1, 1)))
        .thenReturn(
            new LongTermAssetRelatedDataLoader.Data(
                Map.of(),
                Map.of(),
                Map.of(5L, List.of(rate)),
                Map.of(5L, details),
                Map.of(),
                new BigDecimal("0.085")));

    var result = service.projectionInputs(1L, LocalDate.of(2026, 1, 1)).getFirst();

    assertThat(result.maturityDate()).isEqualTo(LocalDate.of(2030, 1, 1));
    assertThat(result.redemptionValue()).isEqualByComparingTo("1200");
    assertThat(result.taxRate()).isEqualByComparingTo("0.19");
    assertThat(result.periods())
        .singleElement()
        .satisfies(p -> assertThat(p.annualReturnRate()).isEqualByComparingTo("0.06"));
  }

  @Test
  void mapsDepositRateFromAcquisitionAndRealEstateTaxBase() {
    var deposit = asset(5L, LongTermAssetType.DEPOSIT);
    deposit.setAcquisitionDate(LocalDate.of(2025, 4, 1));
    var details = new LongTermAssetDepositDetailsEntity();
    details.setAssetId(5L);
    details.setMaturityDate(LocalDate.of(2027, 4, 1));
    details.setAnnualInterestRate(new BigDecimal("0.05"));
    details.setTaxRate(new BigDecimal("0.19"));
    details.setInterestTreatment(InterestTreatment.PAY_OUT);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(deposit));
    when(lifecycle.activeOn(deposit, LocalDate.of(2026, 1, 1))).thenReturn(true);
    when(relatedData.load(1L, List.of(5L), LocalDate.of(2026, 1, 1)))
        .thenReturn(
            new LongTermAssetRelatedDataLoader.Data(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(5L, details),
                new BigDecimal("0.085")));
    var depositResult = service.projectionInputs(1L, LocalDate.of(2026, 1, 1)).getFirst();
    assertThat(depositResult.periods().getFirst().validFrom()).isEqualTo(LocalDate.of(2025, 4, 1));

    var property = asset(7L, LongTermAssetType.REAL_ESTATE);
    property.setTaxBase(new BigDecimal("300000"));
    property.setRentalTaxPaidByTenant(true);
    var growth = new LongTermAssetValuationPeriodEntity();
    growth.setAssetId(7L);
    growth.setValidFrom(LocalDate.of(2026, 1, 1));
    growth.setExpectedAnnualGrowthRate(new BigDecimal("0.03"));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(property));
    when(lifecycle.activeOn(property, LocalDate.of(2026, 1, 1))).thenReturn(true);
    when(relatedData.load(1L, List.of(7L), LocalDate.of(2026, 1, 1)))
        .thenReturn(
            new LongTermAssetRelatedDataLoader.Data(
                Map.of(),
                Map.of(7L, List.of(growth)),
                Map.of(),
                Map.of(),
                Map.of(),
                new BigDecimal("0.09")));
    var propertyResult = service.projectionInputs(1L, LocalDate.of(2026, 1, 1)).getFirst();
    assertThat(propertyResult.taxRate()).isEqualByComparingTo("0.09");
    assertThat(propertyResult.taxBase()).isEqualByComparingTo("300000");
    assertThat(propertyResult.rentalTaxPaidByTenant()).isTrue();
  }

  @Test
  void historicalProjectionInputsUseSameLifecyclePopulationAsSummaryReads() {
    LocalDate date = LocalDate.of(2026, 1, 1);
    var activeNow = asset(1L, LongTermAssetType.OTHER);
    var archivedToday = asset(2L, LongTermAssetType.OTHER);
    var archivedBefore = asset(3L, LongTermAssetType.OTHER);
    var acquiredAfter = asset(4L, LongTermAssetType.OTHER);
    var reactivated = asset(5L, LongTermAssetType.OTHER);
    when(assets.findAllByPortfolioIdOrderByName(1L))
        .thenReturn(List.of(activeNow, archivedToday, archivedBefore, acquiredAfter, reactivated));
    when(lifecycle.activeOn(activeNow, date)).thenReturn(true);
    when(lifecycle.activeOn(archivedToday, date)).thenReturn(true);
    when(lifecycle.activeOn(archivedBefore, date)).thenReturn(false);
    when(lifecycle.activeOn(acquiredAfter, date)).thenReturn(false);
    when(lifecycle.activeOn(reactivated, date)).thenReturn(true);
    when(relatedData.load(1L, List.of(1L, 2L, 5L), date))
        .thenReturn(LongTermAssetRelatedDataLoader.Data.empty());

    assertThat(service.projectionInputs(1L, date))
        .extracting(LongTermAssetProjectionInput::id)
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
