package com.smartbox.investory.ui.longterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.AnnualEconomicsView;
import com.smartbox.investory.longterm.api.model.AssetSummaryView;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RealEstateCommand;
import com.smartbox.investory.longterm.api.model.RealEstateView;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import com.smartbox.investory.longterm.api.model.RentalContractView;
import com.smartbox.investory.longterm.api.model.RentalTermView;
import com.smartbox.investory.longterm.api.model.ResourceNotFoundException;
import com.smartbox.investory.longterm.api.model.UpdateRentalContractCommand;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ui.ConcurrentModel;

class LongTermRealEstateControllerTest {
  private static final Long PORTFOLIO_ID = 7L;
  private static final Long ASSET_ID = 9402L;
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 6);

  @Test
  void detailRoutePassesCompleteAssetSummaryAndRentalReadModel() {
    LongTermAssetsApi assets = mock(LongTermAssetsApi.class);
    LongTermRealEstateController controller = controller(assets);
    RealEstateView asset = realEstate();
    AssetSummaryView summary = summary();
    RentalContractView contract = rentalContract();
    when(assets.realEstate(PORTFOLIO_ID, ASSET_ID)).thenReturn(asset);
    when(assets.realEstateSummary(PORTFOLIO_ID, ASSET_ID, TODAY)).thenReturn(summary);
    when(assets.rentalContracts(PORTFOLIO_ID, ASSET_ID, TODAY)).thenReturn(List.of(contract));
    var model = new ConcurrentModel();

    String template = controller.realEstateDetail(PORTFOLIO_ID, ASSET_ID, model);

    assertThat(template).isEqualTo("real-estate-detail");
    assertThat(model.getAttribute("portfolioId")).isEqualTo(PORTFOLIO_ID);
    assertThat(model.getAttribute("asset")).isSameAs(asset);
    assertThat(model.getAttribute("summary")).isSameAs(summary);
    @SuppressWarnings("unchecked")
    List<RentalContractView> contracts = (List<RentalContractView>) model.getAttribute("contracts");
    assertThat(contracts).containsExactly(contract);
    @SuppressWarnings("unchecked")
    var contractForms =
        (java.util.Map<Long, RentalContractForm>) model.getAttribute("contractForms");
    assertThat(contractForms).containsKey(contract.id());
    assertThat(contractForms.get(contract.id()).getTenantName()).isEqualTo(contract.tenantName());
    assertThat(model.getAttribute("today")).isEqualTo(TODAY);
    assertThat(model.getAttribute("suggestedNextContractStart")).isEqualTo(TODAY);
    verify(assets).realEstate(PORTFOLIO_ID, ASSET_ID);
    verify(assets).realEstateSummary(PORTFOLIO_ID, ASSET_ID, TODAY);
    verify(assets).rentalContracts(PORTFOLIO_ID, ASSET_ID, TODAY);
  }

  @Test
  void createRouteUsesCanonicalUrl() {
    LongTermRealEstateController controller = controller(mock(LongTermAssetsApi.class));
    var model = new ConcurrentModel();

    assertThat(controller.realEstateForm(PORTFOLIO_ID, model)).isEqualTo("real-estate-form");
    assertThat(model.getAttribute("portfolioId")).isEqualTo(PORTFOLIO_ID);
  }

  @Test
  void updateRoutePreservesEveryRealEstateField() {
    LongTermAssetsApi assets = mock(LongTermAssetsApi.class);
    LongTermRealEstateController controller = controller(assets);
    RealEstateForm form =
        new RealEstateForm(
            ASSET_ID,
            "Riverside flat",
            CurrencyType.EUR,
            new BigDecimal("312500.00"),
            new BigDecimal("8700.00"),
            LocalDate.of(2020, 4, 15),
            "KW1A/00012345/6",
            "Keep the tenant notice period.");
    RealEstateView saved = realEstate();
    when(assets.updateRealEstate(any())).thenReturn(saved);

    String result = controller.saveRealEstate(PORTFOLIO_ID, form, mockRedirectAttributes());

    assertThat(result).isEqualTo("redirect:/portfolios/7/long-term-assets/9402/real-estate");
    ArgumentCaptor<RealEstateCommand> command = ArgumentCaptor.forClass(RealEstateCommand.class);
    verify(assets).updateRealEstate(command.capture());
    assertThat(command.getValue())
        .isEqualTo(
            new RealEstateCommand(
                PORTFOLIO_ID,
                ASSET_ID,
                form.name(),
                form.currency(),
                form.value(),
                form.taxBase(),
                form.acquisitionDate(),
                form.landRegisterNumber(),
                form.notes()));
  }

  @Test
  void rentalReadbackPreservesAllSupportedTermsAndPaidByTenantFlags() {
    RentalContractForm form = RentalContractForm.from(rentalContract());

    var command = form.updateCommand(PORTFOLIO_ID, ASSET_ID, 77L);

    assertThat(command)
        .isEqualTo(
            new UpdateRentalContractCommand(
                PORTFOLIO_ID,
                ASSET_ID,
                77L,
                "Ada Tenant",
                "ada@example.test",
                "+48123456789",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                List.of(
                    term(CashFlowType.RENT, "4200", Frequency.MONTHLY, false),
                    term(CashFlowType.PARKING_RENT, "300", Frequency.MONTHLY, false),
                    term(CashFlowType.ADMIN_FEE, "650", Frequency.MONTHLY, true),
                    term(CashFlowType.UTILITIES, "900", Frequency.MONTHLY, true),
                    term(CashFlowType.OTHER_INCOME, "125", Frequency.MONTHLY, false),
                    term(CashFlowType.OTHER_EXPENSE, "80", Frequency.MONTHLY, false),
                    term(CashFlowType.PROPERTY_TAX, "2400", Frequency.ANNUAL, true),
                    term(CashFlowType.INSURANCE, "1200", Frequency.ANNUAL, false))));
  }

  @Test
  void detailRoutePropagatesMissingTargetedSummary() {
    LongTermAssetsApi assets = mock(LongTermAssetsApi.class);
    when(assets.realEstate(PORTFOLIO_ID, ASSET_ID)).thenReturn(realEstate());
    when(assets.realEstateSummary(PORTFOLIO_ID, ASSET_ID, TODAY))
        .thenThrow(new ResourceNotFoundException("Real estate not found"));

    assertThatThrownBy(
            () ->
                controller(assets).realEstateDetail(PORTFOLIO_ID, ASSET_ID, new ConcurrentModel()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Real estate not found");
  }

  private static LongTermRealEstateController controller(LongTermAssetsApi assets) {
    return new LongTermRealEstateController(
        assets, Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC));
  }

  private static org.springframework.web.servlet.mvc.support.RedirectAttributes
      mockRedirectAttributes() {
    return mock(org.springframework.web.servlet.mvc.support.RedirectAttributes.class);
  }

  private static RealEstateView realEstate() {
    return new RealEstateView(
        ASSET_ID,
        PORTFOLIO_ID,
        "Riverside flat",
        CurrencyType.EUR,
        LocalDate.of(2020, 4, 15),
        new BigDecimal("312500.00"),
        new BigDecimal("8700.00"),
        "KW1A/00012345/6",
        true,
        "Keep the tenant notice period.");
  }

  private static AssetSummaryView summary() {
    return new AssetSummaryView(
        ASSET_ID,
        "Riverside flat",
        LongTermAssetType.REAL_ESTATE,
        CurrencyType.EUR,
        new BigDecimal("312500.00"),
        null,
        null,
        new AnnualEconomicsView(
            new BigDecimal("62100"),
            new BigDecimal("2400"),
            new BigDecimal("4800"),
            new BigDecimal("59700"),
            new BigDecimal("54900"),
            new BigDecimal("4575"),
            new BigDecimal("19.87"),
            new BigDecimal("19.10"),
            new BigDecimal("17.57")),
        LocalDate.of(2027, 1, 1));
  }

  private static RentalContractView rentalContract() {
    return new RentalContractView(
        77L,
        "Ada Tenant",
        "ada@example.test",
        "+48123456789",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2027, 1, 1),
        null,
        LocalDate.of(2027, 1, 1),
        RentalContractStatusModel.CURRENT,
        List.of(
            termView(CashFlowType.RENT, "4200", Frequency.MONTHLY, false),
            termView(CashFlowType.PARKING_RENT, "300", Frequency.MONTHLY, false),
            termView(CashFlowType.ADMIN_FEE, "650", Frequency.MONTHLY, true),
            termView(CashFlowType.UTILITIES, "900", Frequency.MONTHLY, true),
            termView(CashFlowType.OTHER_INCOME, "125", Frequency.MONTHLY, false),
            termView(CashFlowType.OTHER_EXPENSE, "80", Frequency.MONTHLY, false),
            termView(CashFlowType.PROPERTY_TAX, "2400", Frequency.ANNUAL, true),
            termView(CashFlowType.INSURANCE, "1200", Frequency.ANNUAL, false)));
  }

  private static RentalTermView termView(
      CashFlowType type, String amount, Frequency frequency, boolean paidByTenant) {
    return new RentalTermView(type, new BigDecimal(amount), frequency, paidByTenant);
  }

  private static com.smartbox.investory.longterm.api.model.RentalTermCommand term(
      CashFlowType type, String amount, Frequency frequency, boolean paidByTenant) {
    return new com.smartbox.investory.longterm.api.model.RentalTermCommand(
        type, new BigDecimal(amount), frequency, paidByTenant);
  }
}
