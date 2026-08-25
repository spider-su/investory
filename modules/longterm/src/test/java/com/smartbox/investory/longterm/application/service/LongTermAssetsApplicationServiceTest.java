package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongTermAssetsApplicationServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void mapsRentalContractManagementFieldsToPublicBoundary() {
    var facade = mock(LongTermAssetsFacade.class);
    var application = new LongTermAssetsApplicationService(facade, CLOCK);
    LocalDate date = LocalDate.of(2026, 8, 24);
    var contract =
        new LongTermAssetsFacade.ContractView(
            44L,
            "Tenant",
            "tenant@example.com",
            "+48 123",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2026, 8, 20),
            LocalDate.of(2026, 8, 20),
            RentalContractStatusModel.TERMINATED,
            Boolean.TRUE,
            List.of(
                new LongTermAssetsFacade.TermView(
                    CashFlowType.UTILITIES, new BigDecimal("450"), Frequency.MONTHLY, true)));
    when(facade.details(1L, 7L, date))
        .thenReturn(
            new LongTermAssetsFacade.DetailView(
                asset(),
                summary(),
                null,
                null,
                List.of(),
                BigDecimal.ZERO,
                List.of(contract)));

    var result = application.details(1L, 7L, date).contracts().getFirst();

    assertThat(result.id()).isEqualTo(44L);
    assertThat(result.tenantName()).isEqualTo("Tenant");
    assertThat(result.tenantEmail()).isEqualTo("tenant@example.com");
    assertThat(result.tenantPhone()).isEqualTo("+48 123");
    assertThat(result.effectiveEndDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    assertThat(result.status()).isEqualTo(RentalContractStatusModel.TERMINATED);
    assertThat(result.rentalTaxPaidByTenant()).isTrue();
    assertThat(result.terms().getFirst().type()).isEqualTo(CashFlowTypeModel.UTILITIES);
    assertThat(result.terms().getFirst().frequency()).isEqualTo(FrequencyModel.MONTHLY);
    assertThat(result.terms().getFirst().paidByTenant()).isTrue();
  }

  @Test
  void mapsMutationStatusWithInjectedApplicationClock() {
    var facade = mock(LongTermAssetsFacade.class);
    var application = new LongTermAssetsApplicationService(facade, CLOCK);
    var created = new LongTermAssetRentalContractEntity();
    created.setId(45L);
    created.setAssetId(7L);
    created.setStartDate(LocalDate.of(2026, 8, 25));
    when(facade.createRentalContract(any())).thenReturn(created);

    var result =
        application.createRentalContract(
            new LongTermAssetsApi.RentalContractCommand(
                1L, 7L, null, null, null, LocalDate.of(2026, 8, 25), null, null, false, List.of()));

    assertThat(result.status()).isEqualTo(RentalContractStatusModel.UPCOMING);
  }

  private static LongTermAssetsFacade.AssetView asset() {
    return new LongTermAssetsFacade.AssetView(
        7L,
        1L,
        "Rental",
        LongTermAssetType.REAL_ESTATE,
        CurrencyType.PLN,
        null,
        BigDecimal.ZERO,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        true,
        null,
        false);
  }

  private static LongTermAssetSummary summary() {
    BigDecimal zero = BigDecimal.ZERO;
    return new LongTermAssetSummary(
        7L,
        "Rental",
        LongTermAssetType.REAL_ESTATE,
        CurrencyType.PLN,
        BigDecimal.ONE,
        null,
        null,
        new AnnualEconomics(zero, zero, zero, zero, zero, zero, zero, zero),
        null,
        null,
        null);
  }
}
