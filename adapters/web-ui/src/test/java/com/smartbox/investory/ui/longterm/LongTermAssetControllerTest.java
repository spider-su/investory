package com.smartbox.investory.ui.longterm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class LongTermAssetControllerTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

  @Mock LongTermAssetsApi assets;
  private LongTermAssetController controller;

  @BeforeEach
  void setUp() {
    controller = new LongTermAssetController(assets, CLOCK);
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
    controller.savePropertyGrowth(
        7L,
        1L,
        BigDecimal.ONE,
        LocalDate.of(2026, 1, 1),
        new RedirectAttributesModelMap());
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
        "updated",
        new RedirectAttributesModelMap());

    var command = ArgumentCaptor.forClass(LongTermAssetsApi.BondCommand.class);
    verify(assets).updateBond(command.capture());
    assertEquals(new BigDecimal("5.75"), command.getValue().annualRatePercent());
  }

  @Test
  void taxBaseIsSentAsUpdateCommand() {
    LongTermAssetController.AssetForm form = new LongTermAssetController.AssetForm();
    form.setType(LongTermAssetTypeModel.REAL_ESTATE);
    form.setCurrentValue(new BigDecimal("780000"));
    controller.update(7L, form, 1L, new BigDecimal("1800"), new RedirectAttributesModelMap());
    var command = ArgumentCaptor.forClass(LongTermAssetsApi.AssetCommand.class);
    verify(assets).update(command.capture());
    assertEquals(new BigDecimal("1800"), command.getValue().taxBase());
  }

  @Test
  void illegalTypeUpdateRedirectsWithFeedback() {
    LongTermAssetController.AssetForm form = new LongTermAssetController.AssetForm();
    form.setType(LongTermAssetTypeModel.DEPOSIT);
    var feedback = new RedirectAttributesModelMap();
    doThrow(new IllegalArgumentException("Asset type cannot be changed after creation"))
        .when(assets)
        .update(any());

    String result = controller.update(7L, form, 1L, null, feedback);

    assertEquals("redirect:/long-term-assets/7?portfolioId=1", result);
    assertEquals("Asset type cannot be changed.", feedback.getFlashAttributes().get("error"));
  }

  @Test
  void effectiveDatedCorrectionsAndDeletesReachApplicationApi() {
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 12, 31);
    var feedback = new RedirectAttributesModelMap();
    controller.updateValuationPeriod(7L, 41L, 1L, from, to, new BigDecimal("3.5"), feedback);
    controller.deleteValuationPeriod(7L, 41L, 1L, feedback);
    var bondRate = new LongTermAssetController.BondRateForm();
    bondRate.setValidFrom(from);
    bondRate.setValidTo(to);
    bondRate.setAnnualInterestRate(new BigDecimal("0.06"));
    controller.updateBondRatePeriod(7L, 42L, 1L, bondRate, feedback);
    controller.deleteBondRatePeriod(7L, 42L, 1L, feedback);
    var tax = new LongTermAssetController.RentalTaxForm();
    tax.setValidFrom(from);
    tax.setValidTo(to);
    controller.updateRentalTaxPolicy(43L, 1L, tax, new BigDecimal("8.5"), null, feedback);
    controller.deleteRentalTaxPolicy(43L, 1L, feedback);

    verify(assets)
        .updateValuation(
            1L, 7L, 41L, new LongTermAssetsApi.ValuationCommand(from, to, new BigDecimal("3.5")));
    verify(assets).deleteValuation(1L, 7L, 41L);
    verify(assets)
        .updateBondRate(
            1L, 7L, 42L, new LongTermAssetsApi.BondRateCommand(from, to, new BigDecimal("0.06")));
    verify(assets).deleteBondRate(1L, 7L, 42L);
    verify(assets)
        .updateRentalTaxPolicy(
            1L, 43L, new LongTermAssetsApi.RentalTaxCommand(from, to, new BigDecimal("8.5"), null));
    verify(assets).deleteRentalTaxPolicy(1L, 43L);
  }

  @Test
  void periodValidationFailureRedirectsWithFeedbackInsteadOfEscapingAsServerError() {
    var feedback = new RedirectAttributesModelMap();
    doThrow(new IllegalArgumentException("Overlapping valuation period"))
        .when(assets)
        .addValuation(any(), any(), any());

    String result =
        controller.addValuationPeriod(
            7L,
            1L,
            LocalDate.of(2026, 1, 1),
            null,
            new BigDecimal("3.5"),
            feedback);

    assertEquals("redirect:/long-term-assets/7?portfolioId=1", result);
    assertEquals("Overlapping valuation period", feedback.getFlashAttributes().get("error"));
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

    controller.addRentalContract(
        7L, 1L, form, binding(form, "rentalContract"), new RedirectAttributesModelMap());

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

    controller.updateRentalContract(
        7L, 44L, 1L, form, binding(form, "contractEditForm"), new RedirectAttributesModelMap());
    controller.deleteRentalContract(7L, 44L, 1L, new RedirectAttributesModelMap());

    var command = ArgumentCaptor.forClass(LongTermAssetsApi.UpdateRentalContractCommand.class);
    verify(assets).updateRentalContract(command.capture());
    assertEquals(44L, command.getValue().contractId());
    assertEquals(null, command.getValue().rentalTaxPaidByTenant());
    verify(assets).deleteRentalContract(1L, 7L, 44L);
  }

  @Test
  void futureActualTerminationIsRejectedAtControllerBoundary() {
    var feedback = new RedirectAttributesModelMap();

    controller.terminateRentalContract(7L, 44L, 1L, LocalDate.of(2026, 8, 25), feedback);

    assertEquals(
        "Actual termination date cannot be later than today.",
        feedback.getFlashAttributes().get("error"));
    verify(assets, never()).terminateRentalContract(any(), any(), any(), any());
  }

  @Test
  void malformedCreateBindingRedirectsWithValuesErrorsAndOpenForm() throws Exception {
    var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    var result =
        mockMvc
            .perform(
                post("/long-term-assets/7/rental-contracts")
                    .param("portfolioId", "1")
                    .param("tenantName", "Tenant retained")
                    .param("startDate", "not-a-date")
                    .param("rent", "not-an-amount")
                    .param("rentFrequency", "NOT_A_FREQUENCY"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/long-term-assets/7?portfolioId=1#rental-contracts"))
            .andReturn();

    var flash = result.getFlashMap();
    assertEquals(true, flash.get("showAddContract"));
    assertEquals(
        "Tenant retained",
        ((LongTermAssetController.RentalContractForm) flash.get("rentalContract")).getTenantName());
    assertEquals(
        "not-a-date", ((java.util.Map<?, ?>) flash.get("rentalRejectedValues")).get("startDate"));
    assertEquals(
        "not-an-amount", ((java.util.Map<?, ?>) flash.get("rentalRejectedValues")).get("rent"));
    var errors = (java.util.List<?>) flash.get("rentalBindingErrors");
    assertFalse(errors.isEmpty());
    assertTrue(errors.contains("Invalid rent: not-an-amount."));
    verify(assets, never()).createRentalContract(any());
  }

  @Test
  void malformedUpdateBindingReopensEditedContractWithoutCallingService() {
    var form = new LongTermAssetController.RentalContractForm();
    form.setTenantName("Tenant retained");
    var binding = binding(form, "contractEditForm");
    binding.rejectValue("endDate", "typeMismatch", "bad-date");
    var feedback = new RedirectAttributesModelMap();

    controller.updateRentalContract(7L, 44L, 1L, form, binding, feedback);

    assertEquals(44L, feedback.getFlashAttributes().get("editContractId"));
    assertEquals(form, feedback.getFlashAttributes().get("contractEditForm"));
    assertFalse(
        ((java.util.List<?>) feedback.getFlashAttributes().get("rentalBindingErrors")).isEmpty());
    verify(assets, never()).updateRentalContract(any());
  }

  private static BindingResult binding(Object form, String name) {
    return new BeanPropertyBindingResult(form, name);
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
