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
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
@DisplayName("Long Term Asset Controller")
class LongTermAssetControllerTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

  @Mock LongTermAssetsClient assets;
  private LongTermAssetController controller;
  private LongTermBondController bonds;
  private LongTermRealEstateController realEstate;
  private LongTermRentalTaxController rentalTax;

  @BeforeEach
  void setUp() {
    controller = new LongTermAssetController(assets, CLOCK);
    bonds = new LongTermBondController(assets);
    realEstate = new LongTermRealEstateController(assets, CLOCK);
    rentalTax = new LongTermRentalTaxController(assets);
  }

  @DisplayName("bond Form Is Sent As Application Command")
  @Test
  void bondFormIsSentAsApplicationCommand() {
    when(assets.createBond(any())).thenReturn(assetView(7L));
    bonds.createBond(
        1L,
        "Bond",
        CurrencyType.PLN,
        new BigDecimal("150000"),
        LocalDate.of(2025, 3, 1),
        LocalDate.of(2028, 2, 28),
        InterestTreatment.CAPITALIZE,
        new BigDecimal("6"),
        null,
        new RedirectAttributesModelMap());
    var command = ArgumentCaptor.forClass(BondCommand.class);
    verify(assets).createBond(command.capture());
    assertEquals(new BigDecimal("150000"), command.getValue().value());
    assertEquals(new BigDecimal("0.06"), command.getValue().annualRate());
  }

  @DisplayName("property Growth Form Is Converted To Canonical Rate")
  @Test
  void propertyGrowthFormIsSentAsPercentInput() {
    realEstate.savePropertyGrowth(
        7L, 1L, BigDecimal.ONE, LocalDate.of(2026, 1, 1), new RedirectAttributesModelMap());
    verify(assets).savePropertyGrowth(1L, 7L, new BigDecimal("0.01"), LocalDate.of(2026, 1, 1));
  }

  @DisplayName("bond Update Sends Yield To The Bond Command")
  @Test
  void bondUpdateSendsYieldToTheBondCommand() {
    bonds.updateBond(
        7L,
        1L,
        "Bond",
        CurrencyType.PLN,
        new BigDecimal("151000"),
        LocalDate.of(2025, 3, 1),
        LocalDate.of(2028, 2, 28),
        InterestTreatment.PAY_OUT,
        new BigDecimal("5.75"),
        "updated",
        new RedirectAttributesModelMap());

    var command = ArgumentCaptor.forClass(BondCommand.class);
    verify(assets).updateBond(command.capture());
    assertEquals(new BigDecimal("0.0575"), command.getValue().annualRate());
  }

  @DisplayName("tax Base Is Sent As Update Command")
  @Test
  void taxBaseIsSentAsUpdateCommand() {
    LongTermAssetForm form = new LongTermAssetForm();
    form.setType(LongTermAssetType.REAL_ESTATE);
    form.setCurrentValue(new BigDecimal("780000"));
    controller.update(7L, form, 1L, new BigDecimal("1800"), new RedirectAttributesModelMap());
    var command = ArgumentCaptor.forClass(AssetCommand.class);
    verify(assets).update(command.capture());
    assertEquals(new BigDecimal("1800"), command.getValue().taxBase());
  }

  @DisplayName("illegal Type Update Redirects With Feedback")
  @Test
  void illegalTypeUpdateRedirectsWithFeedback() {
    LongTermAssetForm form = new LongTermAssetForm();
    form.setType(LongTermAssetType.DEPOSIT);
    var feedback = new RedirectAttributesModelMap();
    doThrow(new IllegalArgumentException("Asset type cannot be changed after creation"))
        .when(assets)
        .update(any());

    String result = controller.update(7L, form, 1L, null, feedback);

    assertEquals("redirect:/long-term-assets/7?portfolioId=1", result);
    assertEquals("Asset type cannot be changed.", feedback.getFlashAttributes().get("error"));
  }

  @DisplayName("real Estate And Tax Effective Dated Corrections Reach Application Api")
  @Test
  void realEstateAndTaxEffectiveDatedCorrectionsReachApplicationApi() {
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 12, 31);
    var feedback = new RedirectAttributesModelMap();
    realEstate.updateValuationPeriod(7L, 41L, 1L, from, to, new BigDecimal("3.5"), feedback);
    realEstate.deleteValuationPeriod(7L, 41L, 1L, feedback);
    var tax = new RentalTaxForm();
    tax.setValidFrom(from);
    tax.setValidTo(to);
    rentalTax.updateRentalTaxPolicy(43L, 1L, tax, new BigDecimal("8.5"), feedback);
    rentalTax.deleteRentalTaxPolicy(43L, 1L, feedback);

    verify(assets)
        .updateValuation(1L, 7L, 41L, new ValuationCommand(from, to, new BigDecimal("0.035")));
    verify(assets).deleteValuation(1L, 7L, 41L);
    verify(assets)
        .updateRentalTaxPolicy(1L, 43L, new RentalTaxCommand(from, to, new BigDecimal("0.085")));
    verify(assets).deleteRentalTaxPolicy(1L, 43L);
  }

  @DisplayName("invalid Bond Creation Redirects To Form With Feedback")
  @Test
  void invalidBondCreationRedirectsToFormWithFeedback() {
    var feedback = new RedirectAttributesModelMap();
    doThrow(new IllegalArgumentException("Bond maturity must be on or after acquisition"))
        .when(assets)
        .createBond(any());

    String result =
        bonds.createBond(
            1L,
            "Bond",
            CurrencyType.PLN,
            new BigDecimal("100"),
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 1),
            InterestTreatment.PAY_OUT,
            new BigDecimal("5"),
            null,
            feedback);

    assertEquals("redirect:/long-term-assets?portfolioId=1", result);
    assertEquals(
        "Bond maturity must be on or after acquisition",
        feedback.getFlashAttributes().get("error"));
  }

  @DisplayName(
      "period Validation Failure Redirects With Feedback Instead Of Escaping As Server Error")
  @Test
  void periodValidationFailureRedirectsWithFeedbackInsteadOfEscapingAsServerError() {
    var feedback = new RedirectAttributesModelMap();
    doThrow(new IllegalArgumentException("Overlapping valuation period"))
        .when(assets)
        .addValuation(any(), any(), any());

    String result =
        realEstate.addValuationPeriod(
            7L, 1L, LocalDate.of(2026, 1, 1), null, new BigDecimal("3.5"), feedback);

    assertEquals("redirect:/long-term-assets/7?portfolioId=1", result);
    assertEquals("Overlapping valuation period", feedback.getFlashAttributes().get("error"));
  }

  @DisplayName("rental Create Maps Tenant Rollover Tax Payers And Only Entered Terms")
  @Test
  void rentalCreateMapsTenantRolloverTaxPayersAndOnlyEnteredTerms() {
    var form = new RentalContractForm();
    form.setTenantName("Tenant");
    form.setTenantEmail("tenant@example.com");
    form.setTenantPhone("+48 123");
    form.setStartDate(LocalDate.of(2027, 1, 1));
    form.setMonthlyTaxBase(new BigDecimal("3100"));
    form.setRentalTaxOwnership("TENANT");
    form.setEndCurrentContractBeforeStart(true);
    form.setRent(new BigDecimal("3300"));
    form.setAnnualPropertyTax(new BigDecimal("700"));
    form.setPropertyTaxPaidByTenant(true);

    realEstate.addRentalContract(
        7L, 1L, form, binding(form, "rentalContract"), new RedirectAttributesModelMap());

    var command = ArgumentCaptor.forClass(RentalContractCommand.class);
    verify(assets).createRentalContract(command.capture());
    assertEquals("Tenant", command.getValue().tenantName());
    assertEquals("tenant@example.com", command.getValue().tenantEmail());
    assertEquals("+48 123", command.getValue().tenantPhone());
    assertEquals(new BigDecimal("3100"), command.getValue().monthlyTaxBase());
    assertEquals(Boolean.TRUE, command.getValue().rentalTaxPaidByTenant());
    assertEquals(true, command.getValue().endCurrentContractBeforeStart());
    assertEquals(
        java.util.List.of(
            com.smartbox.investory.longterm.api.model.CashFlowType.RENT,
            com.smartbox.investory.longterm.api.model.CashFlowType.PROPERTY_TAX),
        command.getValue().terms().stream().map(RentalTermCommand::type).toList());
    assertEquals(true, command.getValue().terms().get(1).paidByTenant());
  }

  @DisplayName("rental Update Preserves Contract Identity And Delete Uses Owned Path")
  @Test
  void rentalUpdatePreservesContractIdentityAndDeleteUsesOwnedPath() {
    var form = new RentalContractForm();
    form.setStartDate(LocalDate.of(2027, 1, 1));
    form.setMonthlyTaxBase(new BigDecimal("3200"));
    form.setRentalTaxOwnership("INHERIT");

    realEstate.updateRentalContract(
        7L, 44L, 1L, form, binding(form, "contractEditForm"), new RedirectAttributesModelMap());
    realEstate.deleteRentalContract(7L, 44L, 1L, new RedirectAttributesModelMap());

    var command = ArgumentCaptor.forClass(UpdateRentalContractCommand.class);
    verify(assets).updateRentalContract(command.capture());
    assertEquals(44L, command.getValue().contractId());
    assertEquals(new BigDecimal("3200"), command.getValue().monthlyTaxBase());
    assertEquals(null, command.getValue().rentalTaxPaidByTenant());
    assertEquals(true, command.getValue().usePropertyTaxPayerDefault());
    verify(assets).deleteRentalContract(1L, 7L, 44L);
  }

  @DisplayName("future Actual Termination Is Rejected At Controller Boundary")
  @Test
  void futureActualTerminationIsRejectedAtControllerBoundary() {
    var feedback = new RedirectAttributesModelMap();

    realEstate.terminateRentalContract(7L, 44L, 1L, LocalDate.of(2026, 8, 25), feedback);

    assertEquals(
        "Actual termination date cannot be later than today.",
        feedback.getFlashAttributes().get("error"));
    verify(assets, never()).terminateRentalContract(any(), any(), any(), any());
  }

  @DisplayName("malformed Create Binding Redirects With Values Errors And Open Form")
  @Test
  void malformedCreateBindingRedirectsWithValuesErrorsAndOpenForm() throws Exception {
    var mockMvc = MockMvcBuilders.standaloneSetup(realEstate).build();

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
        "Tenant retained", ((RentalContractForm) flash.get("rentalContract")).getTenantName());
    assertEquals(
        "not-a-date", ((java.util.Map<?, ?>) flash.get("rentalRejectedValues")).get("startDate"));
    assertEquals(
        "not-an-amount", ((java.util.Map<?, ?>) flash.get("rentalRejectedValues")).get("rent"));
    var errors = (java.util.List<?>) flash.get("rentalBindingErrors");
    assertFalse(errors.isEmpty());
    assertTrue(errors.contains("Invalid rent: not-an-amount."));
    verify(assets, never()).createRentalContract(any());
  }

  @DisplayName("malformed Update Binding Reopens Edited Contract Without Calling Service")
  @Test
  void malformedUpdateBindingReopensEditedContractWithoutCallingService() {
    var form = new RentalContractForm();
    form.setTenantName("Tenant retained");
    var binding = binding(form, "contractEditForm");
    binding.rejectValue("endDate", "typeMismatch", "bad-date");
    var feedback = new RedirectAttributesModelMap();

    realEstate.updateRentalContract(7L, 44L, 1L, form, binding, feedback);

    assertEquals(44L, feedback.getFlashAttributes().get("editContractId"));
    assertEquals(form, feedback.getFlashAttributes().get("contractEditForm"));
    assertFalse(
        ((java.util.List<?>) feedback.getFlashAttributes().get("rentalBindingErrors")).isEmpty());
    verify(assets, never()).updateRentalContract(any());
  }

  @Test
  void listBuildsHeaderAndLargestGroupFromApplicationSnapshot() {
    var economics = economics("100", "10", "2", "88", "0.1", "0.088");
    var group =
        new AssetGroupView(
            "BOND", "Bonds", CurrencyType.PLN, List.of(), new BigDecimal("1000"), economics, null);
    var aggregate = new AggregateView(CurrencyType.PLN, new BigDecimal("1000"), economics);
    when(assets.page(1L, LocalDate.of(2026, 8, 24)))
        .thenReturn(new PageSnapshot(List.of(), List.of(group), aggregate));

    var model = new ConcurrentModel();
    assertEquals("long-term-assets", controller.list(1L, false, model));

    assertEquals("Bonds", model.getAttribute("longTermLargestClass"));
    assertEquals("100.0%", model.getAttribute("longTermLargestClassShare"));
    assertEquals(List.of(), model.getAttribute("archivedAssets"));
  }

  @Test
  void detailSelectsTypeTemplateAndSuggestsNextRentalStart() {
    var contract =
        new RentalContractView(
            4L,
            "Tenant",
            null,
            null,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            null,
            LocalDate.of(2026, 12, 31),
            null,
            null,
            null,
            List.of());
    var asset =
        new AssetView(
            7L,
            1L,
            "Flat",
            LongTermAssetType.REAL_ESTATE,
            CurrencyType.PLN,
            null,
            BigDecimal.ONE,
            BigDecimal.ONE,
            null,
            true,
            null,
            false);
    when(assets.details(1L, 7L, LocalDate.of(2026, 8, 24)))
        .thenReturn(new DetailView(asset, null, null, null, List.of(), null, List.of(contract)));

    var model = new ConcurrentModel();
    assertEquals("real-estate-detail", controller.detail(7L, 1L, model));

    assertEquals(LocalDate.of(2027, 1, 1), model.getAttribute("suggestedNextContractStart"));
    assertTrue(((java.util.Map<?, ?>) model.getAttribute("contractForms")).containsKey(4L));
  }

  private static BindingResult binding(Object form, String name) {
    return new BeanPropertyBindingResult(form, name);
  }

  private static AssetView assetView(Long id) {
    return new AssetView(
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

  private static AnnualEconomicsView economics(
      String gross, String expenses, String tax, String net, String grossYield, String netYield) {
    return new AnnualEconomicsView(
        new BigDecimal(gross),
        new BigDecimal(expenses),
        new BigDecimal(tax),
        new BigDecimal(net),
        new BigDecimal(net),
        new BigDecimal(net).divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP),
        new BigDecimal(grossYield),
        new BigDecimal(netYield),
        new BigDecimal(netYield));
  }
}
