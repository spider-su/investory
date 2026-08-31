package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Long Term Assets Application Service")
class LongTermAssetsApplicationServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

  @DisplayName("maps Rental Contract Management Fields To Public Boundary")
  @Test
  void mapsRentalContractManagementFieldsToPublicBoundary() {
    var queries = mock(LongTermAssetQueryService.class);
    var detail = mock(LongTermAssetQueryService.AssetDetailData.class);
    var sourceAsset =
        mock(com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity.class);
    var sourceContract = mock(LongTermAssetRentalContractEntity.class);
    var sourceTerm =
        mock(
            com.smartbox.investory.longterm.infrastructure.rental
                .LongTermAssetRentalContractTermEntity.class);
    var sourceSummary =
        mock(com.smartbox.investory.longterm.application.model.LongTermAssetSummary.class);
    when(sourceSummary.annualEconomics()).thenReturn(mock(AnnualEconomics.class));
    when(sourceTerm.getType()).thenReturn(CashFlowType.UTILITIES);
    when(sourceTerm.getAmount()).thenReturn(new BigDecimal("450"));
    when(sourceTerm.getFrequency()).thenReturn(Frequency.MONTHLY);
    when(sourceTerm.isPaidByTenant()).thenReturn(true);
    when(sourceContract.getTerms()).thenReturn(List.of(sourceTerm));
    when(sourceContract.getId()).thenReturn(44L);
    when(sourceContract.getAssetId()).thenReturn(7L);
    when(sourceContract.getTenantName()).thenReturn("Tenant");
    when(sourceContract.getTenantEmail()).thenReturn("tenant@example.com");
    when(sourceContract.getTenantPhone()).thenReturn("+48 123");
    when(sourceContract.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
    when(sourceContract.getEndDate()).thenReturn(LocalDate.of(2026, 12, 31));
    when(sourceContract.getTerminatedDate()).thenReturn(LocalDate.of(2026, 8, 20));
    when(sourceContract.getRentalTaxPaidByTenant()).thenReturn(Boolean.TRUE);
    when(sourceContract.getMonthlyTaxBase()).thenReturn(new BigDecimal("1200"));
    when(detail.asset()).thenReturn(sourceAsset);
    when(detail.summary()).thenReturn(sourceSummary);
    when(detail.valuationPeriods()).thenReturn(List.of());
    when(detail.expectedPropertyGrowth()).thenReturn(BigDecimal.ZERO);
    when(detail.contracts()).thenReturn(List.of(sourceContract));
    when(queries.detail(1L, 7L, LocalDate.of(2026, 8, 24))).thenReturn(detail);
    var application =
        new LongTermAssetsApplicationService(
            queries,
            mock(LongTermAssetAnnualSnapshotService.class),
            mock(LongTermAssetCommandService.class),
            mock(RentalContractService.class),
            CLOCK);
    LocalDate date = LocalDate.of(2026, 8, 24);
    var contract =
        new RentalContractView(
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
            new BigDecimal("1200"),
            List.of(
                new RentalTermView(
                    com.smartbox.investory.longterm.api.model.CashFlowType.UTILITIES,
                    new BigDecimal("450"),
                    com.smartbox.investory.longterm.api.model.Frequency.MONTHLY,
                    true)));
    var result = application.details(1L, 7L, date).contracts().getFirst();

    assertThat(result.id()).isEqualTo(44L);
    assertThat(result.tenantName()).isEqualTo("Tenant");
    assertThat(result.tenantEmail()).isEqualTo("tenant@example.com");
    assertThat(result.tenantPhone()).isEqualTo("+48 123");
    assertThat(result.effectiveEndDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    assertThat(result.status()).isEqualTo(RentalContractStatusModel.TERMINATED);
    assertThat(result.rentalTaxPaidByTenant()).isTrue();
    assertThat(result.terms().getFirst().type())
        .isEqualTo(com.smartbox.investory.longterm.api.model.CashFlowType.UTILITIES);
    assertThat(result.terms().getFirst().frequency())
        .isEqualTo(com.smartbox.investory.longterm.api.model.Frequency.MONTHLY);
    assertThat(result.terms().getFirst().paidByTenant()).isTrue();
  }

  @DisplayName("maps Mutation Status With Injected Application Clock")
  @Test
  void mapsMutationStatusWithInjectedApplicationClock() {
    var rentals = mock(RentalContractService.class);
    var application =
        new LongTermAssetsApplicationService(
            mock(LongTermAssetQueryService.class),
            mock(LongTermAssetAnnualSnapshotService.class),
            mock(LongTermAssetCommandService.class),
            rentals,
            CLOCK);
    var created = new LongTermAssetRentalContractEntity();
    created.setId(45L);
    created.setAssetId(7L);
    created.setStartDate(LocalDate.of(2026, 8, 25));
    when(rentals.create(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(created);

    var result =
        application.createRentalContract(
            new RentalContractCommand(
                1L,
                7L,
                null,
                null,
                null,
                LocalDate.of(2026, 8, 25),
                null,
                null,
                null,
                false,
                List.of()));

    assertThat(result.status()).isEqualTo(RentalContractStatusModel.UPCOMING);
  }

  private static AssetView asset() {
    return new AssetView(
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
}
