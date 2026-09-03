package com.smartbox.investory.longterm.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Long Term Asset Read Service")
class LongTermAssetReadServiceTest {
  private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

  @Mock LongTermAssetQueryService queries;
  @Mock LongTermAssetProjectionQueryService projections;
  @Mock LongTermAssetAnnualSnapshotService annualSnapshots;
  @Mock CurrencyConversion currencyRates;
  @Mock PortfolioContextReader portfolios;
  private LongTermAssetReadService readService;

  @BeforeEach
  void setUp() {
    readService =
        new LongTermAssetReadService(
            queries, projections, annualSnapshots, currencyRates, portfolios);
    org.mockito.Mockito.lenient()
        .when(portfolios.findById(1L))
        .thenReturn(java.util.Optional.of(new PortfolioContext(1L, CurrencyType.USD)));
    org.mockito.Mockito.lenient()
        .when(
            annualSnapshots.currentAnnualSnapshot(
                org.mockito.ArgumentMatchers.anyList(), any(LocalDate.class)))
        .thenReturn(
            new com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel(
                null, null, null, null, null, null));
    org.mockito.Mockito.lenient().when(queries.list(1L, DATE)).thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(
            currencyRates.convertToBaseCurrency(
                any(BigDecimal.class), eq(CurrencyType.USD), eq(CurrencyType.PLN), eq(DATE)))
        .thenAnswer(
            invocation -> invocation.getArgument(0, BigDecimal.class).divide(new BigDecimal("4")));
  }

  @DisplayName("projection Inputs Normalize Every Monetary Field To Usd")
  @Test
  void projectionInputsNormalizeEveryMonetaryFieldToUsd() {
    LongTermAssetProjectionInput input =
        new LongTermAssetProjectionInput(
            1L,
            "Apartment",
            LongTermAssetType.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("400000"),
            List.of(
                new LongTermAssetProjectionInput.Period(
                    DATE,
                    null,
                    new BigDecimal("48000"),
                    new BigDecimal("12000"),
                    new BigDecimal("0.01"),
                    null,
                    false)),
            List.of(),
            null,
            new BigDecimal("400000"),
            null,
            new BigDecimal("0.085"),
            new BigDecimal("200000"),
            false);
    when(projections.snapshot(1L, DATE))
        .thenReturn(
            new LongTermAssetProjectionQueryService.Snapshot(
                List.of(),
                com.smartbox.investory.longterm.application.service.LongTermAssetRelatedDataLoader
                    .Data.empty(),
                List.of(input)));

    LongTermAssetProjectionModel result =
        readService.snapshot(1L, DATE).projectionInputs().getFirst();

    assertEquals(CurrencyType.USD, result.currency());
    assertEquals(new BigDecimal("100000"), result.currentValue());
    assertEquals(new BigDecimal("12000"), result.periods().getFirst().annualIncome());
    assertEquals(new BigDecimal("3000"), result.periods().getFirst().annualExpense());
    assertEquals(new BigDecimal("100000"), result.redemptionValue());
    assertEquals(new BigDecimal("50000"), result.taxBase());
    assertEquals(new BigDecimal("0.01"), result.periods().getFirst().annualReturnRate());
  }

  @DisplayName("projection Inputs Denominate In Portfolio Base Currency When Not Usd")
  @Test
  void projectionInputsDenominateInPortfolioBaseCurrencyWhenNotUsd() {
    when(portfolios.findById(2L))
        .thenReturn(java.util.Optional.of(new PortfolioContext(2L, CurrencyType.PLN)));
    org.mockito.Mockito.lenient().when(queries.list(2L, DATE)).thenReturn(List.of());
    when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class), eq(CurrencyType.PLN), eq(CurrencyType.USD), eq(DATE)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0, BigDecimal.class).multiply(new BigDecimal("4")));
    LongTermAssetProjectionInput input =
        new LongTermAssetProjectionInput(
            9L,
            "Deposit",
            LongTermAssetType.DEPOSIT,
            CurrencyType.USD,
            new BigDecimal("1000"),
            List.of(
                new LongTermAssetProjectionInput.Period(
                    DATE,
                    null,
                    new BigDecimal("50"),
                    new BigDecimal("10"),
                    new BigDecimal("0.02"),
                    null,
                    false)),
            List.of(),
            null,
            new BigDecimal("1000"),
            null,
            new BigDecimal("0.19"),
            new BigDecimal("500"),
            false);
    when(projections.snapshot(2L, DATE))
        .thenReturn(
            new LongTermAssetProjectionQueryService.Snapshot(
                List.of(),
                com.smartbox.investory.longterm.application.service.LongTermAssetRelatedDataLoader
                    .Data.empty(),
                List.of(input)));

    LongTermAssetProjectionModel result =
        readService.snapshot(2L, DATE).projectionInputs().getFirst();

    assertEquals(CurrencyType.PLN, result.currency());
    assertEquals(new BigDecimal("4000"), result.currentValue());
    assertEquals(new BigDecimal("200"), result.periods().getFirst().annualIncome());
    assertEquals(new BigDecimal("40"), result.periods().getFirst().annualExpense());
    assertEquals(new BigDecimal("4000"), result.redemptionValue());
    assertEquals(new BigDecimal("2000"), result.taxBase());
  }

  @DisplayName("projection Inputs Preserve Bond Interest Periods And Contract Metadata")
  @Test
  void projectionInputsPreserveBondInterestPeriodsAndContractMetadata() {
    LongTermAssetProjectionInput input =
        new LongTermAssetProjectionInput(
            7L,
            "Bond",
            LongTermAssetType.BOND,
            CurrencyType.USD,
            new BigDecimal("900000"),
            List.of(
                new LongTermAssetProjectionInput.Period(
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2028, 12, 31),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("0.053333333333333333"),
                    null,
                    false)),
            List.of(),
            LocalDate.of(2028, 12, 31),
            new BigDecimal("900000"),
            InterestTreatment.PAY_OUT,
            new BigDecimal("0.19"),
            null,
            false);
    when(projections.snapshot(1L, DATE))
        .thenReturn(
            new LongTermAssetProjectionQueryService.Snapshot(
                List.of(),
                com.smartbox.investory.longterm.application.service.LongTermAssetRelatedDataLoader
                    .Data.empty(),
                List.of(input)));

    var result = readService.snapshot(1L, DATE).projectionInputs().getFirst();

    assertEquals(LongTermAssetType.BOND, result.type());
    assertEquals(new BigDecimal("900000"), result.currentValue());
    assertEquals(
        new BigDecimal("0.053333333333333333"), result.periods().getFirst().annualReturnRate());
    assertEquals(LocalDate.of(2028, 12, 31), result.periods().getFirst().validTo());
    assertEquals(new BigDecimal("900000"), result.redemptionValue());
    assertEquals(
        com.smartbox.investory.longterm.api.model.InterestTreatment.PAY_OUT,
        result.interestTreatment());
  }
}
