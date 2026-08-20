package com.smartbox.investory.controllers.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.Frequency;
import com.smartbox.investory.longterm.api.InterestTreatment;
import com.smartbox.investory.longterm.application.LongTermAssetService;
import com.smartbox.investory.longterm.application.RealEstateEntry;
import com.smartbox.investory.longterm.infrastructure.LongTermAsset;
import com.smartbox.investory.longterm.infrastructure.LongTermAssetCashFlow;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LongTermAssetControllerTest {
  @Mock LongTermAssetService service;
  private LongTermAssetController controller;

  @BeforeEach
  void setUp() {
    controller = new LongTermAssetController(service, Clock.systemUTC());
  }

  @Test
  void percentInputsAlwaysConvertPercentagePointsToDecimalRates() {
    String[][] cases = {
      {"0", "0.00"},
      {"0.5", "0.005"},
      {"1", "0.01"},
      {"2", "0.02"},
      {"5", "0.05"},
      {"8.5", "0.085"},
      {"19", "0.19"},
      {"100", "1.00"}
    };
    for (String[] entry : cases)
      assertEquals(
          0,
          new BigDecimal(entry[1])
              .compareTo(LongTermAssetController.percentInputToRate(new BigDecimal(entry[0]))),
          entry[0]);
  }

  @Test
  void creatingBondUsesOneValueForAcquisitionAndCurrentValue() {
    when(service.save(any()))
        .thenAnswer(
            invocation -> {
              LongTermAsset asset = invocation.getArgument(0);
              asset.setId(7L);
              return asset;
            });

    controller.createBond(
        1L,
        "Bond",
        CurrencyType.PLN,
        new BigDecimal("150000"),
        LocalDate.of(2025, 3, 1),
        LocalDate.of(2028, 2, 28),
        InterestTreatment.CAPITALIZE,
        new BigDecimal("6"),
        null);

    var saved = verifySave();
    assertEquals(new BigDecimal("150000"), saved.getAcquisitionValue());
    assertEquals(new BigDecimal("150000"), saved.getCurrentValue());
  }

  @Test
  void creatingBondConvertsDisplayedOnePercentRateToDecimal() {
    when(service.save(any()))
        .thenAnswer(
            invocation -> {
              LongTermAsset asset = invocation.getArgument(0);
              asset.setId(7L);
              return asset;
            });
    controller.createBond(
        1L,
        "Bond",
        CurrencyType.PLN,
        new BigDecimal("150000"),
        LocalDate.of(2025, 3, 1),
        LocalDate.of(2028, 2, 28),
        InterestTreatment.CAPITALIZE,
        BigDecimal.ONE,
        null);
    verify(service)
        .saveSimpleBond(
            1L,
            7L,
            new BigDecimal("0.01"),
            LocalDate.of(2028, 2, 28),
            InterestTreatment.CAPITALIZE);
  }

  @Test
  void propertyGrowthFormConvertsDisplayedOnePercentToDecimal() {
    controller.savePropertyGrowth(7L, 1L, BigDecimal.ONE, LocalDate.of(2026, 1, 1));
    verify(service)
        .saveExpectedPropertyGrowth(1L, 7L, new BigDecimal("0.01"), LocalDate.of(2026, 1, 1));
  }

  @Test
  void realEstateCreationConvertsDisplayedGrowthPercentToDecimal() {
    LongTermAsset saved = new LongTermAsset();
    saved.setType(com.smartbox.investory.longterm.api.LongTermAssetType.REAL_ESTATE);
    saved.setCurrentValue(new BigDecimal("1000000"));
    saved.setId(7L);
    when(service.saveRealEstateEntry(anyLong(), isNull(), any(RealEstateEntry.class)))
        .thenReturn(saved);
    RealEstateEntry entry =
        new RealEstateEntry(
            "Property",
            CurrencyType.PLN,
            null,
            null,
            new BigDecimal("1000000"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 1, 1),
            null,
            null);

    controller.saveRealEstate(1L, entry, BigDecimal.ONE);

    var captor = org.mockito.ArgumentCaptor.forClass(RealEstateEntry.class);
    verify(service).saveRealEstateEntry(eq(1L), isNull(), captor.capture());
    assertEquals(new BigDecimal("0.01"), captor.getValue().expectedAnnualGrowthRate());
  }

  @Test
  void genericValuationPeriodConvertsDisplayedGrowthPercentToDecimal() {
    controller.addValuationPeriod(7L, 1L, LocalDate.of(2026, 1, 1), null, BigDecimal.ONE);

    var captor =
        org.mockito.ArgumentCaptor.forClass(
            com.smartbox.investory.longterm.infrastructure.LongTermAssetValuationPeriod.class);
    verify(service).addValuationPeriod(eq(1L), eq(7L), captor.capture());
    assertEquals(new BigDecimal("0.01"), captor.getValue().getExpectedAnnualGrowthRate());
  }

  @Test
  void editingCashFlowPassesEndDateToService() {
    controller.changeCashFlow(
        7L,
        11L,
        1L,
        new BigDecimal("2900"),
        Frequency.MONTHLY,
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 12, 31));

    verify(service)
        .changeCashFlow(
            1L,
            7L,
            11L,
            new BigDecimal("2900"),
            Frequency.MONTHLY,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31));
  }

  @Test
  void addingCashFlowPassesBothRentalPeriodDatesToService() {
    controller.addCashFlow(
        11L,
        1L,
        com.smartbox.investory.longterm.api.CashFlowType.RENT,
        new BigDecimal("2900"),
        Frequency.MONTHLY,
        LocalDate.of(2025, 1, 1),
        LocalDate.of(2026, 12, 31));

    var captor = org.mockito.ArgumentCaptor.forClass(LongTermAssetCashFlow.class);
    verify(service).addCashFlow(eq(1L), eq(11L), captor.capture());
    assertEquals(LocalDate.of(2025, 1, 1), captor.getValue().getValidFrom());
    assertEquals(LocalDate.of(2026, 12, 31), captor.getValue().getValidTo());
  }

  @Test
  void propertySaveBindsAnnualTaxBaseExplicitly() {
    LongTermAsset asset = new LongTermAsset();
    asset.setId(7L);
    asset.setType(com.smartbox.investory.longterm.api.LongTermAssetType.REAL_ESTATE);
    when(service.get(1L, 7L)).thenReturn(Optional.of(asset));

    LongTermAsset form = new LongTermAsset();
    form.setName("Apartment");
    form.setType(com.smartbox.investory.longterm.api.LongTermAssetType.REAL_ESTATE);
    form.setCurrency(CurrencyType.PLN);
    form.setCurrentValue(new BigDecimal("780000"));

    controller.update(
        7L, form, 1L, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31), new BigDecimal("1800"));

    assertEquals(new BigDecimal("1800"), asset.getTaxBase());
    verify(service).save(asset);
  }

  @Test
  void editingNormalBondValueUpdatesBothValues() {
    LongTermAsset asset = bond("100", "100");
    when(service.get(1L, 7L)).thenReturn(Optional.of(asset));

    controller.updateBond(
        7L,
        1L,
        "Bond",
        CurrencyType.PLN,
        new BigDecimal("125"),
        LocalDate.of(2025, 3, 1),
        LocalDate.of(2028, 2, 28),
        InterestTreatment.PAY_OUT,
        new BigDecimal("6"),
        null);

    assertEquals(new BigDecimal("125"), asset.getAcquisitionValue());
    assertEquals(new BigDecimal("125"), asset.getCurrentValue());
  }

  @Test
  void editingLegacyBondPreservesHistoricalInvestedValue() {
    LongTermAsset asset = bond("100", "120");
    when(service.get(1L, 7L)).thenReturn(Optional.of(asset));

    controller.updateBond(
        7L,
        1L,
        "Bond",
        CurrencyType.PLN,
        new BigDecimal("130"),
        LocalDate.of(2025, 3, 1),
        LocalDate.of(2028, 2, 28),
        InterestTreatment.PAY_OUT,
        new BigDecimal("6"),
        null);

    assertEquals(new BigDecimal("100"), asset.getAcquisitionValue());
    assertEquals(new BigDecimal("130"), asset.getCurrentValue());
  }

  private LongTermAsset verifySave() {
    var captor = org.mockito.ArgumentCaptor.forClass(LongTermAsset.class);
    verify(service).save(captor.capture());
    return captor.getValue();
  }

  private static LongTermAsset bond(String acquisitionValue, String currentValue) {
    LongTermAsset asset = new LongTermAsset();
    asset.setId(7L);
    asset.setAcquisitionValue(new BigDecimal(acquisitionValue));
    asset.setCurrentValue(new BigDecimal(currentValue));
    return asset;
  }
}
