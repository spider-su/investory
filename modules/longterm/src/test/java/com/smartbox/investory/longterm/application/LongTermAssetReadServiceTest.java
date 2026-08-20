package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.LongTermAssetProjection;
import com.smartbox.investory.longterm.api.LongTermAssetType;
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
    when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class), eq(CurrencyType.USD), eq(CurrencyType.PLN), eq(DATE)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0, BigDecimal.class).divide(new BigDecimal("4")));
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

    LongTermAssetProjection result = readService.projectionInputs(1L, DATE).getFirst();

    assertEquals(CurrencyType.USD, result.currency());
    assertEquals(new BigDecimal("100000"), result.currentValue());
    assertEquals(new BigDecimal("12000"), result.periods().getFirst().annualIncome());
    assertEquals(new BigDecimal("3000"), result.periods().getFirst().annualExpense());
    assertEquals(new BigDecimal("100000"), result.redemptionValue());
    assertEquals(new BigDecimal("50000"), result.taxBase());
    assertEquals(new BigDecimal("0.01"), result.periods().getFirst().annualReturnRate());
  }
}
