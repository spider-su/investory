package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.longterm.application.service.LongTermAssetAnnualSnapshotService;
import com.smartbox.investory.longterm.application.service.LongTermAssetProjectionQueryService;
import com.smartbox.investory.longterm.application.service.LongTermAssetQueryService;
import com.smartbox.investory.longterm.application.service.LongTermAssetReadService;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
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
  private LongTermAssetReadService readService;

  @BeforeEach
  void setUp() {
    readService =
        new LongTermAssetReadService(queries, projections, annualSnapshots, currencyRates);
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
    when(projections.projectionInputs(1L, DATE)).thenReturn(List.of(input));

    LongTermAssetProjectionModel result =
        readService.snapshot(1L, DATE).projectionInputs().getFirst();

    assertEquals(CurrencyType.USD, result.currency());
    assertEquals(new BigDecimal("100000"), result.currentValue());
    assertEquals(new BigDecimal("12000"), result.periods().getFirst().annualIncome());
    assertEquals(new BigDecimal("3000"), result.periods().getFirst().annualExpense());
    assertEquals(new BigDecimal("100000"), result.redemptionValue());
    assertEquals(new BigDecimal("50000"), result.taxBase());
    assertEquals(new BigDecimal("0.01"), result.periods().getFirst().annualReturnRate());
    verify(queries).list(1L, DATE);
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
    when(projections.projectionInputs(1L, DATE)).thenReturn(List.of(input));

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
