package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.longterm.application.service.LongTermAssetReadService;
import com.smartbox.investory.longterm.application.service.LongTermAssetService;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LongTermAssetReadServiceTest {
  private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

  @Mock LongTermAssetService longTermAssets;
  @Mock CurrencyConversion currencyRates;
  private LongTermAssetReadService readService;

  @BeforeEach
  void setUp() {
    readService = new LongTermAssetReadService(longTermAssets, currencyRates);
    org.mockito.Mockito.lenient().when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class), eq(CurrencyType.USD), eq(CurrencyType.PLN), eq(DATE)))
        .thenAnswer(
            invocation -> invocation.getArgument(0, BigDecimal.class).divide(new BigDecimal("4")));
  }

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
                    new BigDecimal("0.01"))),
            null,
            new BigDecimal("400000"),
            null,
            new BigDecimal("0.085"),
            new BigDecimal("200000"));
    when(longTermAssets.projectionInputs(1L, DATE)).thenReturn(List.of(input));

    LongTermAssetProjectionModel result = readService.projectionInputs(1L, DATE).getFirst();

    assertEquals(CurrencyType.USD, result.currency());
    assertEquals(new BigDecimal("100000"), result.currentValue());
    assertEquals(new BigDecimal("12000"), result.periods().getFirst().annualIncome());
    assertEquals(new BigDecimal("3000"), result.periods().getFirst().annualExpense());
    assertEquals(new BigDecimal("100000"), result.redemptionValue());
    assertEquals(new BigDecimal("50000"), result.taxBase());
    assertEquals(new BigDecimal("0.01"), result.periods().getFirst().annualReturnRate());
  }

  @Test
  void projectionInputsPreserveBondInterestPeriodsAndContractMetadata() {
    LongTermAssetProjectionInput input = new LongTermAssetProjectionInput(
        7L, "Bond", LongTermAssetType.BOND, CurrencyType.USD, new BigDecimal("900000"),
        List.of(new LongTermAssetProjectionInput.Period(
            LocalDate.of(2025, 1, 1), LocalDate.of(2028, 12, 31),
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.053333333333333333"))),
        LocalDate.of(2028, 12, 31), new BigDecimal("900000"), InterestTreatment.PAY_OUT,
        new BigDecimal("0.19"));
    when(longTermAssets.projectionInputs(1L, DATE)).thenReturn(List.of(input));

    var result = readService.projectionInputs(1L, DATE).getFirst();

    assertEquals(LongTermAssetTypeModel.BOND, result.type());
    assertEquals(new BigDecimal("900000"), result.currentValue());
    assertEquals(new BigDecimal("0.053333333333333333"), result.periods().getFirst().annualReturnRate());
    assertEquals(LocalDate.of(2028, 12, 31), result.periods().getFirst().validTo());
    assertEquals(new BigDecimal("900000"), result.redemptionValue());
    assertEquals(com.smartbox.investory.longterm.api.model.InterestTreatmentModel.PAY_OUT,
        result.interestTreatment());
  }
}
