package com.smartbox.investory.longterm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("Long Term Assets Rest Controller")
class LongTermAssetsRestControllerTest {
  private static final String BASE = "/api/v1/portfolios/3/long-term-assets";
  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2030, 12, 31);

  @Mock private LongTermAssetsApi assets;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new LongTermAssetsRestController(assets),
                new LongTermInstrumentRestController(assets),
                new LongTermRealEstateRestController(assets))
            .build();
  }

  @Test
  @DisplayName("maps asset and subtype payloads completely")
  void mapsAssetSubtypePayloadsCompletely() throws Exception {
    when(assets.create(any())).thenReturn(asset(7L));
    mvc.perform(
            post(BASE)
                .contentType("application/json")
                .content(
                    """
                    {"name":"Art","type":"OTHER","currency":"USD","acquisitionDate":"2020-02-03",
                     "acquisitionValue":100,"currentValue":125,"taxBase":10,"active":true,
                     "notes":"note","rentalTaxPaidByTenant":false}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", BASE + "/7"));
    verify(assets)
        .create(
            new AssetCommand(
                3L,
                null,
                "Art",
                LongTermAssetType.OTHER,
                CurrencyType.USD,
                LocalDate.of(2020, 2, 3),
                new BigDecimal("100"),
                new BigDecimal("125"),
                new BigDecimal("10"),
                true,
                "note",
                false));

    mvc.perform(
            patch(BASE + "/7")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Art 2","notes":"updated"}
                    """))
        .andExpect(status().isOk());
    verify(assets)
        .patch(
            new AssetPatchCommand(
                3L, 7L, "Art 2", null, null, null, null, null, null, null, "updated", null));

    when(assets.saveCashReserve(any(), any())).thenReturn(asset(8L));
    mvc.perform(
            post(BASE + "/cash-reserves")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Emergency","currency":"PLN","value":50000,"annualReturnPercent":4.5,
                     "effectiveFrom":"2026-01-01","notes":"liquid"}
                    """))
        .andExpect(status().isCreated());
    verify(assets)
        .saveCashReserve(
            new CashReserveCommand(
                3L,
                null,
                "Emergency",
                CurrencyType.PLN,
                new BigDecimal("50000"),
                new BigDecimal("0.045"),
                "liquid"),
            FROM);

    mvc.perform(
            put(BASE + "/8/cash-reserve")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Emergency 2","currency":"PLN","value":51000,"annualReturnPercent":4.75,
                     "effectiveFrom":"2026-01-01","notes":"updated"}
                    """))
        .andExpect(status().isOk());
    verify(assets)
        .saveCashReserve(
            new CashReserveCommand(
                3L,
                8L,
                "Emergency 2",
                CurrencyType.PLN,
                new BigDecimal("51000"),
                new BigDecimal("0.0475"),
                "updated"),
            FROM);

    when(assets.createBond(any())).thenReturn(asset(9L));
    mvc.perform(
            post(BASE + "/bonds")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Bond","currency":"USD","value":20000,"acquisitionDate":"2026-01-01",
                     "maturityDate":"2030-12-31","interestTreatment":"PAY_OUT",
                     "annualRatePercent":6.25,"notes":"fixed"}
                    """))
        .andExpect(status().isCreated());
    verify(assets)
        .createBond(
            new BondCommand(
                3L,
                null,
                "Bond",
                CurrencyType.USD,
                new BigDecimal("20000"),
                FROM,
                TO,
                InterestTreatment.PAY_OUT,
                new BigDecimal("0.0625"),
                "fixed"));

    mvc.perform(
            put(BASE + "/9/bond")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Bond 2","currency":"USD","value":21000,"acquisitionDate":"2026-01-01",
                     "maturityDate":"2030-12-31","interestTreatment":"CAPITALIZE",
                     "annualRatePercent":6.5,"notes":"updated"}
                    """))
        .andExpect(status().isOk());
    verify(assets)
        .updateBond(
            new BondCommand(
                3L,
                9L,
                "Bond 2",
                CurrencyType.USD,
                new BigDecimal("21000"),
                FROM,
                TO,
                InterestTreatment.CAPITALIZE,
                new BigDecimal("0.065"),
                "updated"));

    when(assets.createDeposit(any())).thenReturn(asset(10L));
    mvc.perform(
            post(BASE + "/deposits")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Deposit","currency":"EUR","value":12000,"acquisitionDate":"2026-01-01",
                     "maturityDate":"2030-12-31","interestTreatment":"CAPITALIZE",
                     "annualInterestRatePercent":4,"taxRatePercent":19,"notes":"term"}
                    """))
        .andExpect(status().isCreated());
    verify(assets)
        .createDeposit(
            new DepositCommand(
                3L,
                "Deposit",
                CurrencyType.EUR,
                new BigDecimal("12000"),
                FROM,
                TO,
                InterestTreatment.CAPITALIZE,
                new BigDecimal("0.04"),
                new BigDecimal("0.19"),
                "term"));

    when(assets.saveRealEstate(anyLong(), any())).thenReturn(asset(11L));
    mvc.perform(
            post(BASE + "/real-estate")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Flat","currency":"PLN","acquisitionDate":"2020-01-02",
                     "acquisitionValue":500000,"currentValue":700000,"taxBase":2000,
                     "monthlyRent":4000,"monthlyParkingIncome":300,"monthlyAdministrationCost":700,
                     "monthlyOtherCost":100,"annualPropertyTax":500,"annualInsurance":900,
                     "effectiveFrom":"2026-01-01","expectedAnnualGrowthRatePercent":3.5,
                     "notes":"rental","rentalTaxPaidByTenant":true}
                    """))
        .andExpect(status().isCreated());
    var realEstate = ArgumentCaptor.forClass(RealEstateEntryModel.class);
    verify(assets).saveRealEstate(org.mockito.ArgumentMatchers.eq(3L), realEstate.capture());
    assertEquals(
        new RealEstateEntryModel(
            "Flat",
            CurrencyType.PLN,
            LocalDate.of(2020, 1, 2),
            new BigDecimal("500000"),
            new BigDecimal("700000"),
            new BigDecimal("2000"),
            new BigDecimal("4000"),
            new BigDecimal("300"),
            new BigDecimal("700"),
            new BigDecimal("100"),
            new BigDecimal("500"),
            new BigDecimal("900"),
            FROM,
            new BigDecimal("0.035"),
            "rental",
            true),
        realEstate.getValue());
  }

  @Test
  @DisplayName("maps rental lifecycle payloads completely")
  void mapsRentalContractLifecycleCompletely() throws Exception {
    when(assets.createRentalContract(any())).thenReturn(contract(41L));
    mvc.perform(
            post(BASE + "/7/rental-contracts")
                .contentType("application/json")
                .content(
                    """
                    {"tenantName":"Tenant","tenantEmail":"t@example.com","tenantPhone":"123",
                     "startDate":"2026-01-01","endDate":"2030-12-31","monthlyTaxBase":3500,
                     "rentalTaxPaidByTenant":true,"endCurrentContractBeforeStart":true,
                     "terms":[{"type":"RENT","amount":4000,"frequency":"MONTHLY","paidByTenant":false}]}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", BASE + "/7/rental-contracts/41"));
    verify(assets)
        .createRentalContract(
            new RentalContractCommand(
                3L,
                7L,
                "Tenant",
                "t@example.com",
                "123",
                FROM,
                TO,
                new BigDecimal("3500"),
                true,
                true,
                List.of(
                    new RentalTermCommand(
                        CashFlowType.RENT, new BigDecimal("4000"), Frequency.MONTHLY, false))));

    when(assets.updateRentalContract(any())).thenReturn(contract(41L));
    mvc.perform(
            put(BASE + "/7/rental-contracts/41")
                .contentType("application/json")
                .content(
                    """
                    {"tenantName":"Tenant 2","tenantEmail":"new@example.com","tenantPhone":"456",
                     "startDate":"2026-01-01","endDate":null,"monthlyTaxBase":3600,
                     "rentalTaxPaidByTenant":false,"usePropertyTaxPayerDefault":true,
                     "terms":[{"type":"ADMIN_FEE","amount":700,"frequency":"MONTHLY","paidByTenant":true}]}
                    """))
        .andExpect(status().isOk());
    verify(assets)
        .updateRentalContract(
            new UpdateRentalContractCommand(
                3L,
                7L,
                41L,
                "Tenant 2",
                "new@example.com",
                "456",
                FROM,
                null,
                new BigDecimal("3600"),
                false,
                true,
                List.of(
                    new RentalTermCommand(
                        CashFlowType.ADMIN_FEE, new BigDecimal("700"), Frequency.MONTHLY, true))));

    mvc.perform(delete(BASE + "/7/rental-contracts/41")).andExpect(status().isNoContent());
    verify(assets).deleteRentalContract(3L, 7L, 41L);

    when(assets.endRentalContract(3L, 7L, 41L, TO)).thenReturn(contract(41L));
    mvc.perform(
            post(BASE + "/7/rental-contracts/41/end")
                .contentType("application/json")
                .content("{\"date\":\"2030-12-31\"}"))
        .andExpect(status().isOk());
    verify(assets).endRentalContract(3L, 7L, 41L, TO);

    mvc.perform(
            post(BASE + "/7/rental-contracts/41/terminate")
                .contentType("application/json")
                .content("{\"date\":\"2030-12-31\"}"))
        .andExpect(status().isNoContent());
    verify(assets).terminateRentalContract(3L, 7L, 41L, TO);
  }

  @Test
  @DisplayName("maps detail mutations completely")
  void mapsDetailMutationsCompletely() throws Exception {
    mvc.perform(
            patch(BASE + "/7/tax-base").contentType("application/json").content("{\"value\":2500}"))
        .andExpect(status().isOk());
    verify(assets).saveTaxBase(3L, 7L, new BigDecimal("2500"));

    mvc.perform(
            patch(BASE + "/7/rental-tax-ownership")
                .contentType("application/json")
                .content("{\"paidByTenant\":true}"))
        .andExpect(status().isOk());
    verify(assets).saveRentalTaxOwnership(3L, 7L, true);

    mvc.perform(post(BASE + "/7/archive")).andExpect(status().isOk());
    mvc.perform(post(BASE + "/7/reactivate")).andExpect(status().isOk());
    verify(assets).archive(3L, 7L);
    verify(assets).reactivate(3L, 7L);

    mvc.perform(
            put(BASE + "/7/property-growth")
                .contentType("application/json")
                .content("{\"growthRatePercent\":3.75,\"from\":\"2026-01-01\"}"))
        .andExpect(status().isNoContent());
    verify(assets).savePropertyGrowth(3L, 7L, new BigDecimal("0.0375"), FROM);

    mvc.perform(
            put(BASE + "/7/bond-details")
                .contentType("application/json")
                .content(
                    """
                    {"maturityDate":"2030-12-31","taxRatePercent":19,
                     "interestTreatment":"PAY_OUT","redemptionValue":22000}
                    """))
        .andExpect(status().isNoContent());
    verify(assets)
        .saveBondDetails(
            3L,
            7L,
            new BondDetailsCommand(
                TO, new BigDecimal("0.19"), InterestTreatment.PAY_OUT, new BigDecimal("22000")));

    mvc.perform(
            put(BASE + "/7/deposit-details")
                .contentType("application/json")
                .content(
                    """
                    {"maturityDate":"2030-12-31","annualInterestRatePercent":4,
                     "taxRatePercent":19,"interestTreatment":"CAPITALIZE"}
                    """))
        .andExpect(status().isNoContent());
    verify(assets)
        .saveDepositDetails(
            3L,
            7L,
            new DepositDetailsCommand(
                TO, new BigDecimal("0.04"), new BigDecimal("0.19"), InterestTreatment.CAPITALIZE));

    String valuation =
        "{\"validFrom\":\"2026-01-01\",\"validTo\":\"2030-12-31\",\"growthRatePercent\":4.25}";
    when(assets.addValuation(any(), any(), any()))
        .thenReturn(new ValuationView(52L, FROM, TO, new BigDecimal("0.0425")));
    mvc.perform(post(BASE + "/7/valuations").contentType("application/json").content(valuation))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", BASE + "/7/valuations/52"))
        .andExpect(jsonPath("$.id").value(52));
    mvc.perform(put(BASE + "/7/valuations/51").contentType("application/json").content(valuation))
        .andExpect(status().isNoContent());
    mvc.perform(delete(BASE + "/7/valuations/51")).andExpect(status().isNoContent());
    ValuationCommand valuationCommand = new ValuationCommand(FROM, TO, new BigDecimal("0.0425"));
    verify(assets).addValuation(3L, 7L, valuationCommand);
    verify(assets).updateValuation(3L, 7L, 51L, valuationCommand);
    verify(assets).deleteValuation(3L, 7L, 51L);
  }

  @Test
  @DisplayName("maps policies and rejects invalid payloads")
  void mapsTaxPoliciesAndRejectsInvalidPayloads() throws Exception {
    String policy = "{\"validFrom\":\"2026-01-01\",\"validTo\":\"2030-12-31\",\"ratePercent\":8.5}";
    when(assets.saveRentalTaxPolicy(any(), any()))
        .thenReturn(new RentalTaxView(62L, FROM, TO, new BigDecimal("0.085")));
    mvc.perform(post(BASE + "/rental-tax-policies").contentType("application/json").content(policy))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", BASE + "/rental-tax-policies/62"))
        .andExpect(jsonPath("$.id").value(62));
    mvc.perform(
            put(BASE + "/rental-tax-policies/61").contentType("application/json").content(policy))
        .andExpect(status().isNoContent());
    mvc.perform(delete(BASE + "/rental-tax-policies/61")).andExpect(status().isNoContent());
    RentalTaxCommand command = new RentalTaxCommand(FROM, TO, new BigDecimal("0.085"));
    verify(assets).saveRentalTaxPolicy(3L, command);
    verify(assets).updateRentalTaxPolicy(3L, 61L, command);
    verify(assets).deleteRentalTaxPolicy(3L, 61L);

    mvc.perform(
            post(BASE + "/bonds")
                .contentType("application/json")
                .content(
                    """
                    {"name":"","currency":"USD","value":-1,"acquisitionDate":"2031-01-01",
                     "maturityDate":"2030-12-31","interestTreatment":"PAY_OUT",
                     "annualRatePercent":101}
                    """))
        .andExpect(status().isBadRequest());
    verify(assets, never()).createBond(any());
  }

  @Test
  @DisplayName("uses path identity for reads")
  void usesPathIdentityForReads() throws Exception {
    when(assets.asset(3L, 7L)).thenReturn(asset(7L));
    mvc.perform(get(BASE + "/7")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
    verify(assets).asset(3L, 7L);
  }

  @Test
  @DisplayName("does not expose tenant identity from detail response")
  void doesNotExposeTenantIdentityFromDetailResponse() throws Exception {
    when(assets.details(3L, 7L, FROM))
        .thenReturn(
            new DetailView(asset(7L), null, null, null, List.of(), null, List.of(contract(41L))));

    mvc.perform(get(BASE + "/7/details").param("date", FROM.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contracts[0].tenantName").doesNotExist())
        .andExpect(jsonPath("$.contracts[0].tenantEmail").doesNotExist())
        .andExpect(jsonPath("$.contracts[0].tenantPhone").doesNotExist());
  }

  @Test
  @DisplayName("maps subresource reads and subtype updates")
  void mapsSubresourceReadsAndSubtypeUpdates() throws Exception {
    when(assets.rentalContract(3L, 7L, 41L)).thenReturn(contract(41L));
    when(assets.valuation(3L, 7L, 51L))
        .thenReturn(new ValuationView(51L, FROM, TO, new BigDecimal("0.03")));
    when(assets.rentalTaxPolicy(3L, 62L))
        .thenReturn(new RentalTaxView(62L, FROM, TO, new BigDecimal("0.085")));

    mvc.perform(get(BASE + "/7/rental-contracts/41"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(41));
    mvc.perform(get(BASE + "/7/valuations/51"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(51));
    mvc.perform(get(BASE + "/rental-tax-policies/62"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(62));

    when(assets.updateRealEstate(
            org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(7L), any()))
        .thenReturn(asset(7L));
    mvc.perform(
            put(BASE + "/7/real-estate")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Flat","currency":"PLN","acquisitionDate":"2020-01-02",
                     "acquisitionValue":500000,"currentValue":700000,"taxBase":2000,
                     "monthlyRent":4000,"effectiveFrom":"2026-01-01",
                     "expectedAnnualGrowthRatePercent":3.5,"rentalTaxPaidByTenant":false}
                    """))
        .andExpect(status().isOk());
    verify(assets)
        .updateRealEstate(
            org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(7L), any());
  }

  private static AssetView asset(Long id) {
    return new AssetView(
        id,
        3L,
        "Asset",
        LongTermAssetType.OTHER,
        CurrencyType.USD,
        FROM,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        true,
        null,
        false);
  }

  private static RentalContractView contract(Long id) {
    return new RentalContractView(
        id, "Tenant", null, null, FROM, TO, null, TO, null, false, null, List.of());
  }
}
