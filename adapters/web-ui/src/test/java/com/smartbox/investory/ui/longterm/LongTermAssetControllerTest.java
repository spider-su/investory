package com.smartbox.investory.ui.longterm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class LongTermAssetControllerTest {
  @Mock LongTermAssetsApi assets;
  private LongTermAssetController controller;

  @BeforeEach
  void setUp() {
    controller = new LongTermAssetController(assets, Clock.systemUTC());
  }

  @Test
  void bondFormIsSentAsApplicationCommand() {
    when(assets.createBond(any())).thenReturn(assetView(7L));
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
    var command = ArgumentCaptor.forClass(LongTermAssetsApi.BondCommand.class);
    verify(assets).createBond(command.capture());
    assertEquals(new BigDecimal("150000"), command.getValue().value());
  }

  @Test
  void propertyGrowthFormIsSentAsPercentInput() {
    controller.savePropertyGrowth(7L, 1L, BigDecimal.ONE, LocalDate.of(2026, 1, 1));
    verify(assets).savePropertyGrowth(1L, 7L, BigDecimal.ONE, LocalDate.of(2026, 1, 1));
  }

  @Test
  void cashFlowFormPreservesBothEffectiveDates() {
    controller.addCashFlow(
        11L,
        1L,
        CashFlowType.RENT,
        new BigDecimal("2900"),
        Frequency.MONTHLY,
        LocalDate.of(2025, 1, 1),
        LocalDate.of(2026, 12, 31));
    var command = ArgumentCaptor.forClass(LongTermAssetsApi.CashFlowCommand.class);
    verify(assets).addCashFlow(command.capture(), any());
    assertEquals(LocalDate.of(2025, 1, 1), command.getValue().validFrom());
    assertEquals(LocalDate.of(2026, 12, 31), command.getValue().validTo());
  }

  @Test
  void taxBaseIsSentAsUpdateCommand() {
    LongTermAssetController.AssetForm form = new LongTermAssetController.AssetForm();
    form.setType(LongTermAssetType.REAL_ESTATE);
    form.setCurrentValue(new BigDecimal("780000"));
    controller.update(
        7L, form, 1L, null, null, new BigDecimal("1800"), new RedirectAttributesModelMap());
    var command = ArgumentCaptor.forClass(LongTermAssetsApi.AssetCommand.class);
    verify(assets).update(command.capture());
    assertEquals(new BigDecimal("1800"), command.getValue().taxBase());
  }

  @Test
  void rentalPeriodOverlapIsReturnedAsFormError() {
    LongTermAssetController.AssetForm form = new LongTermAssetController.AssetForm();
    form.setType(LongTermAssetType.REAL_ESTATE);
    form.setCurrentValue(new BigDecimal("780000"));
    doThrow(new IllegalArgumentException("Overlapping cash-flow period"))
        .when(assets)
        .saveRentalPeriod(
            eq(1L), eq(7L), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2027, 1, 1)), any());
    var redirectAttributes = new RedirectAttributesModelMap();

    assertEquals(
        "redirect:/long-term-assets/7?portfolioId=1",
        controller.update(
            7L,
            form,
            1L,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2027, 1, 1),
            null,
            redirectAttributes));

    assertEquals(
        "Rental period overlaps an existing cash-flow period.",
        redirectAttributes.getFlashAttributes().get("rentalPeriodError"));
  }

  private static LongTermAssetsApi.AssetView assetView(Long id) {
    return new LongTermAssetsApi.AssetView(
        id,
        1L,
        "Bond",
        LongTermAssetType.BOND,
        CurrencyType.PLN,
        null,
        BigDecimal.ONE,
        BigDecimal.ONE,
        null,
        true,
        null,
        false);
  }
}
