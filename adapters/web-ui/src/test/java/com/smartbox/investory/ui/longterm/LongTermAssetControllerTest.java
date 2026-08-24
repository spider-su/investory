package com.smartbox.investory.ui.longterm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
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
        InterestTreatmentModel.CAPITALIZE,
        new BigDecimal("6"),
        null);
    var command = ArgumentCaptor.forClass(LongTermAssetsApi.BondCommand.class);
    verify(assets).createBond(command.capture());
    assertEquals(new BigDecimal("150000"), command.getValue().value());
    assertEquals(new BigDecimal("6"), command.getValue().annualRatePercent());
  }

  @Test
  void propertyGrowthFormIsSentAsPercentInput() {
    controller.savePropertyGrowth(7L, 1L, BigDecimal.ONE, LocalDate.of(2026, 1, 1));
    verify(assets).savePropertyGrowth(1L, 7L, BigDecimal.ONE, LocalDate.of(2026, 1, 1));
  }

  @Test
  void bondUpdateSendsYieldToTheBondCommand() {
    controller.updateBond(
        7L,
        1L,
        "Bond",
        CurrencyType.PLN,
        new BigDecimal("151000"),
        LocalDate.of(2025, 3, 1),
        LocalDate.of(2028, 2, 28),
        InterestTreatmentModel.PAY_OUT,
        new BigDecimal("5.75"),
        "updated");

    var command = ArgumentCaptor.forClass(LongTermAssetsApi.BondCommand.class);
    verify(assets).updateBond(command.capture());
    assertEquals(new BigDecimal("5.75"), command.getValue().annualRatePercent());
  }

  @Test
  void taxBaseIsSentAsUpdateCommand() {
    LongTermAssetController.AssetForm form = new LongTermAssetController.AssetForm();
    form.setType(LongTermAssetTypeModel.REAL_ESTATE);
    form.setCurrentValue(new BigDecimal("780000"));
    controller.update(7L, form, 1L, new BigDecimal("1800"));
    var command = ArgumentCaptor.forClass(LongTermAssetsApi.AssetCommand.class);
    verify(assets).update(command.capture());
    assertEquals(new BigDecimal("1800"), command.getValue().taxBase());
  }

  @Test
  void rentalCreateMapsTenantRolloverTaxPayersAndOnlyEnteredTerms() {
    var form = new LongTermAssetController.RentalContractForm();
    form.setTenantName("Tenant");
    form.setTenantEmail("tenant@example.com");
    form.setTenantPhone("+48 123");
    form.setStartDate(LocalDate.of(2027, 1, 1));
    form.setRentalTaxOwnership("TENANT");
    form.setEndCurrentContractBeforeStart(true);
    form.setRent(new BigDecimal("3300"));
    form.setAnnualPropertyTax(new BigDecimal("700"));
    form.setPropertyTaxPaidByTenant(true);

    controller.addRentalContract(7L, 1L, form, new RedirectAttributesModelMap());

    var command = ArgumentCaptor.forClass(LongTermAssetsApi.RentalContractCommand.class);
    verify(assets).createRentalContract(command.capture());
    assertEquals("Tenant", command.getValue().tenantName());
    assertEquals("tenant@example.com", command.getValue().tenantEmail());
    assertEquals("+48 123", command.getValue().tenantPhone());
    assertEquals(Boolean.TRUE, command.getValue().rentalTaxPaidByTenant());
    assertEquals(true, command.getValue().endCurrentContractBeforeStart());
    assertEquals(
        java.util.List.of(
            com.smartbox.investory.longterm.api.model.CashFlowTypeModel.RENT,
            com.smartbox.investory.longterm.api.model.CashFlowTypeModel.PROPERTY_TAX),
        command.getValue().terms().stream()
            .map(LongTermAssetsApi.RentalTermCommand::type)
            .toList());
    assertEquals(true, command.getValue().terms().get(1).paidByTenant());
  }

  @Test
  void rentalUpdatePreservesContractIdentityAndDeleteUsesOwnedPath() {
    var form = new LongTermAssetController.RentalContractForm();
    form.setStartDate(LocalDate.of(2027, 1, 1));
    form.setRentalTaxOwnership("INHERIT");

    controller.updateRentalContract(7L, 44L, 1L, form, new RedirectAttributesModelMap());
    controller.deleteRentalContract(7L, 44L, 1L, new RedirectAttributesModelMap());

    var command = ArgumentCaptor.forClass(LongTermAssetsApi.UpdateRentalContractCommand.class);
    verify(assets).updateRentalContract(command.capture());
    assertEquals(44L, command.getValue().contractId());
    assertEquals(null, command.getValue().rentalTaxPaidByTenant());
    verify(assets).deleteRentalContract(1L, 7L, 44L);
  }

  private static LongTermAssetsApi.AssetView assetView(Long id) {
    return new LongTermAssetsApi.AssetView(
        id,
        1L,
        "Bond",
        LongTermAssetTypeModel.BOND,
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
