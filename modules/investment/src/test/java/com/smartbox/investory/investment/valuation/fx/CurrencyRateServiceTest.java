package com.smartbox.investory.investment.valuation.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.valuation.fx.persistence.CurrencyRateEntity;
import com.smartbox.investory.investment.valuation.fx.persistence.CurrencyRateRepository;
import com.smartbox.investory.investment.valuation.fx.persistence.FxRateResolutionRow;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Currency Rate Service")
class CurrencyRateServiceTest {

  @Mock private CurrencyRateRepository currencyRateRepository;

  private CurrencyRateService service;

  @BeforeEach
  void setUp() {
    service = new CurrencyRateService(currencyRateRepository);
  }

  @DisplayName("activate Daily History updates only when coverage is supported")
  @Test
  void activateDailyHistoryUpdatesOnlyWhenCoverageIsSupported() {
    LocalDate firstSupportedDate = LocalDate.of(2026, 8, 20);
    when(currencyRateRepository.setDailyHistoryStartIfSupported(firstSupportedDate)).thenReturn(1);

    service.activateDailyHistoryAt(firstSupportedDate);

    verify(currencyRateRepository).flush();
    verify(currencyRateRepository).setDailyHistoryStartIfSupported(firstSupportedDate);
  }

  @DisplayName("activate Daily History rejects unsupported coverage")
  @Test
  void activateDailyHistoryRejectsUnsupportedCoverage() {
    LocalDate unsupportedDate = LocalDate.of(2026, 8, 19);
    when(currencyRateRepository.setDailyHistoryStartIfSupported(unsupportedDate)).thenReturn(0);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class, () -> service.activateDailyHistoryAt(unsupportedDate));

    assertEquals(
        "Daily FX history cannot start before supported neutral coverage: 2026-08-19",
        failure.getMessage());
    verify(currencyRateRepository).flush();
    verify(currencyRateRepository).setDailyHistoryStartIfSupported(unsupportedDate);
  }

  @DisplayName("convert To Base Currency returns Amount Unchanged For Same Currency")
  @Test
  void convertToBaseCurrency_returnsAmountUnchangedForSameCurrency() {
    assertEquals(
        100.0,
        service.convertToBaseCurrency(
            100.0, CurrencyType.USD, CurrencyType.USD, LocalDate.of(2026, 7, 5)));
    verifyNoInteractions(currencyRateRepository);
  }

  @DisplayName("shared Conversion Keeps Big Decimal Same Currency Result")
  @Test
  void sharedConversionKeepsBigDecimalSameCurrencyResult() {
    CurrencyConversion conversion = service;

    assertEquals(
        new BigDecimal("100.00000000"),
        conversion.convertToBaseCurrency(
            new BigDecimal("100"), CurrencyType.USD, CurrencyType.USD, LocalDate.of(2026, 7, 5)));
    verifyNoInteractions(currencyRateRepository);
  }

  @DisplayName("convert To Base Currency uses Historical Rate For Requested Date")
  @Test
  void convertToBaseCurrency_usesHistoricalRateForRequestedDate() {
    when(currencyRateRepository.resolveFxRatesForDate(LocalDate.of(2026, 6, 15)))
        .thenReturn(
            List.of(
                resolution(
                    "EUR", "USD", "1.1", "HISTORICAL_MONTHLY", "NBP", "2026-06-01", "ESTIMATED")));
    when(currencyRateRepository.resolveFxRatesForDate(LocalDate.of(2026, 7, 5)))
        .thenReturn(
            List.of(
                resolution(
                    "EUR", "USD", "1.1", "HISTORICAL_MONTHLY", "NBP", "2026-06-01", "ESTIMATED")));
    // Direct EUR->USD rate exists for 2026-06-01, so direct wins over inverse USD->EUR.
    assertEquals(
        99.0,
        service.convertToBaseCurrency(
            90.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 6, 15)),
        1e-9);
    // Direct historical EUR->USD still wins on later dates until a newer direct rate exists.
    assertEquals(
        99.0,
        service.convertToBaseCurrency(
            90.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 5)),
        1e-9);
  }

  @DisplayName("shared Conversion Keeps Historical Big Decimal Result")
  @Test
  void sharedConversionKeepsHistoricalBigDecimalResult() {
    LocalDate date = LocalDate.of(2026, 6, 15);
    when(currencyRateRepository.resolveFxRatesForDate(date))
        .thenReturn(
            List.of(
                resolution(
                    "EUR", "USD", "1.1", "HISTORICAL_MONTHLY", "NBP", "2026-06-01", "ESTIMATED")));

    CurrencyConversion conversion = service;

    assertEquals(
        new BigDecimal("99.00000000"),
        conversion.convertToBaseCurrency(
            new BigDecimal("90"), CurrencyType.USD, CurrencyType.EUR, date));
  }

  @DisplayName("first Miss Loads All Currency Pairs For Date In One Batch")
  @Test
  void firstMissLoadsAllCurrencyPairsForDateInOneBatch() {
    FxRateResolutionRow eurToUsd =
        resolution("EUR", "USD", "1.1", "HISTORICAL_MONTHLY", "NBP", "2026-06-01", "ESTIMATED");
    FxRateResolutionRow usdToPln =
        resolution("USD", "PLN", "4.0", "MARKET_DAILY", "FX", "2026-06-15", "OK");
    when(currencyRateRepository.resolveFxRatesForDate(LocalDate.of(2026, 6, 15)))
        .thenReturn(List.of(eurToUsd, usdToPln));

    assertEquals(
        99.0,
        service.convertToBaseCurrency(
            90.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 6, 15)),
        1e-9);
    assertEquals(
        40.0,
        service.convertToBaseCurrency(
            10.0, CurrencyType.PLN, CurrencyType.USD, LocalDate.of(2026, 6, 15)),
        1e-9);

    verify(currencyRateRepository, times(1)).resolveFxRatesForDate(LocalDate.of(2026, 6, 15));
  }

  @DisplayName("convert To Base Currency uses Inverse Rate When Direct Missing")
  @Test
  void convertToBaseCurrency_usesInverseRateWhenDirectMissing() {
    when(currencyRateRepository.resolveFxRatesForDate(LocalDate.of(2026, 6, 15)))
        .thenReturn(
            List.of(
                resolution(
                    "EUR", "USD", "1.1", "INVERSE", "STATIC_BOOTSTRAP", "2026-06-01", "OK")));
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);

    assertEquals(
        121.0,
        freshService.convertToBaseCurrency(
            110.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 6, 15)),
        1e-9);
  }

  @DisplayName("convert To Base Currency throws When Rate Missing")
  @Test
  void convertToBaseCurrency_throwsWhenRateMissing() {
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
    assertThrows(
        FxRateUnavailableException.class,
        () ->
            freshService.convertToBaseCurrency(
                123.0, CurrencyType.PLN, CurrencyType.EUR, LocalDate.of(2026, 7, 5)));
  }

  @DisplayName("shared Conversion Keeps Missing Rate Failure")
  @Test
  void sharedConversionKeepsMissingRateFailure() {
    CurrencyConversion conversion = new CurrencyRateService(currencyRateRepository);

    assertThrows(
        FxRateUnavailableException.class,
        () ->
            conversion.convertToBaseCurrency(
                new BigDecimal("123"),
                CurrencyType.PLN,
                CurrencyType.EUR,
                LocalDate.of(2026, 7, 5)));
  }

  @DisplayName("update Rates persists New Rate When Absent")
  @Test
  void updateRates_persistsNewRateWhenAbsent() {
    when(currencyRateRepository.findFirstByRateDateAndBaseAndToCurrencyAndSourceAndMethod(
            LocalDate.of(2026, 7, 5),
            CurrencyType.USD,
            CurrencyType.EUR,
            "EXCHANGERATE_HOST",
            "MARKET_DAILY"))
        .thenReturn(Optional.empty());

    service.updateRates(CurrencyType.USD, Map.of(CurrencyType.EUR, 0.95), LocalDate.of(2026, 7, 5));

    ArgumentCaptor<CurrencyRateEntity> captor = ArgumentCaptor.forClass(CurrencyRateEntity.class);
    verify(currencyRateRepository).save(captor.capture());
    CurrencyRateEntity saved = captor.getValue();
    assertEquals(LocalDate.of(2026, 7, 5), saved.getRateDate());
    assertEquals(CurrencyType.USD, saved.getBase());
    assertEquals(CurrencyType.EUR, saved.getToCurrency());
    assertEquals(new BigDecimal("0.95000000"), saved.getRate());
  }

  @DisplayName("update Rates updates Existing Rate")
  @Test
  void updateRates_updatesExistingRate() {
    CurrencyRateEntity existing =
        rate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 5), 0.8);
    when(currencyRateRepository.findFirstByRateDateAndBaseAndToCurrencyAndSourceAndMethod(
            LocalDate.of(2026, 7, 5),
            CurrencyType.USD,
            CurrencyType.EUR,
            "EXCHANGERATE_HOST",
            "MARKET_DAILY"))
        .thenReturn(Optional.of(existing));

    service.updateRates(CurrencyType.USD, Map.of(CurrencyType.EUR, 0.92), LocalDate.of(2026, 7, 5));

    verify(currencyRateRepository).save(existing);
    assertEquals(LocalDate.of(2026, 7, 5), existing.getRateDate());
    assertEquals(new BigDecimal("0.92000000"), existing.getRate());
  }

  @DisplayName("get Rate returns Persisted Rate")
  @Test
  void getRate_returnsPersistedRate() {
    when(currencyRateRepository.resolveFxRatesForDate(LocalDate.of(2026, 7, 5)))
        .thenReturn(
            List.of(
                resolution(
                    "USD", "EUR", "0.91", "DIRECT", "EXCHANGERATE_HOST", "2026-07-01", "OK")));
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);

    assertEquals(
        new BigDecimal("0.91"),
        freshService.getRate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 5)));
  }

  @DisplayName("get Rate throws When Missing")
  @Test
  void getRate_throwsWhenMissing() {
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
    assertThrows(
        RuntimeException.class,
        () -> freshService.getRate(CurrencyType.USD, CurrencyType.PLN, LocalDate.of(2026, 7, 5)));
  }

  @DisplayName("harvest Xtb Execution Rate Preserves Pair Date And Method")
  @Test
  void harvestXtbExecutionRatePreservesPairDateAndMethod() {
    com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity operation =
        new com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity();
    operation.setId(42L);
    operation.setDate(ZonedDateTime.of(2026, 1, 2, 19, 54, 12, 0, ZoneOffset.UTC));
    operation.setComment("Currency conversion, USD to PLN, Exchange rate:3.573631");

    service.harvestXtbExecutionRates(List.of(operation));

    ArgumentCaptor<CurrencyRateEntity> captor = ArgumentCaptor.forClass(CurrencyRateEntity.class);
    verify(currencyRateRepository).save(captor.capture());
    CurrencyRateEntity saved = captor.getValue();
    assertEquals(LocalDate.of(2026, 1, 2), saved.getRateDate());
    assertEquals(CurrencyType.USD, saved.getBase());
    assertEquals(CurrencyType.PLN, saved.getToCurrency());
    assertEquals(new BigDecimal("3.57363100"), saved.getRate());
    assertEquals("XTB_EXECUTION", saved.getMethod());
    assertEquals(CurrencyType.USD, operation.getExecutionFxBase());
    assertEquals(CurrencyType.PLN, operation.getExecutionFxToCurrency());
    assertEquals(new BigDecimal("3.573631"), operation.getExecutionFxRate());
  }

  @DisplayName(
      "harvest Xtb Execution Rates Keep Different Same Day Rates Attached To Their Operations")
  @Test
  void harvestXtbExecutionRatesKeepDifferentSameDayRatesAttachedToTheirOperations() {
    com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity first =
        new com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity();
    first.setId(100L);
    first.setDate(ZonedDateTime.of(2026, 1, 2, 10, 0, 0, 0, ZoneOffset.UTC));
    first.setComment("Currency conversion, USD to PLN, Exchange rate:3.50");
    com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity second =
        new com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity();
    second.setId(101L);
    second.setDate(ZonedDateTime.of(2026, 1, 2, 15, 0, 0, 0, ZoneOffset.UTC));
    second.setComment("Currency conversion, USD to PLN, Exchange rate:3.60");

    service.harvestXtbExecutionRates(List.of(first, second));

    assertEquals(new BigDecimal("3.50"), first.getExecutionFxRate());
    assertEquals(new BigDecimal("3.60"), second.getExecutionFxRate());
    assertEquals("XTB:OPERATION:100", first.getExecutionFxReference());
    assertEquals("XTB:OPERATION:101", second.getExecutionFxReference());
  }

  @DisplayName("transaction Resolver Does Not Borrow Unbound Execution Rate")
  @Test
  void transactionResolverDoesNotBorrowUnboundExecutionRate() {
    CurrencyRateEntity execution =
        rate(CurrencyType.USD, CurrencyType.PLN, LocalDate.of(2026, 1, 2), 3.573631);
    execution.setMethod("XTB_EXECUTION");
    execution.setSource("XTB");
    execution.setObservedAt(ZonedDateTime.of(2026, 1, 2, 19, 54, 12, 0, ZoneOffset.UTC));
    CurrencyRateService.FxRateResolution result =
        service.resolveTransactionRate(
            ZonedDateTime.of(2026, 1, 2, 20, 0, 0, 0, ZoneOffset.UTC),
            CurrencyType.USD,
            CurrencyType.PLN);

    assertEquals("MISSING_RATE", result.conversionStatus());
  }

  @Test
  void transactionResolverUsesWarsawTransactionDay() {
    when(currencyRateRepository.findExecutionRateAtOrBefore(
            any(), eq(LocalDate.of(2026, 1, 2)), eq("USD"), eq("PLN")))
        .thenReturn(Optional.empty());

    service.resolveTransactionRate(
        ZonedDateTime.of(2026, 1, 1, 23, 30, 0, 0, ZoneOffset.UTC),
        CurrencyType.USD,
        CurrencyType.PLN);

    verify(currencyRateRepository)
        .findExecutionRateAtOrBefore(any(), eq(LocalDate.of(2026, 1, 2)), eq("USD"), eq("PLN"));
  }

  @DisplayName("caches Complete Matrix For Repeated Same Pair")
  @Test
  void cachesCompleteMatrixForRepeatedSamePair() {
    LocalDate date = LocalDate.of(2026, 8, 10);
    when(currencyRateRepository.resolveFxRatesForDate(date))
        .thenReturn(
            List.of(resolution("PLN", "USD", "0.25", "MARKET_DAILY", "NBP", "2026-08-10", "OK")));

    for (int index = 0; index < 100; index++) {
      assertEquals(
          25.0, service.convertToBaseCurrency(100.0, CurrencyType.USD, CurrencyType.PLN, date));
    }

    verify(currencyRateRepository, times(1)).resolveFxRatesForDate(date);
  }

  @DisplayName("caches Complete Matrix For Multiple Pairs And Missing Results")
  @Test
  void cachesCompleteMatrixForMultiplePairsAndMissingResults() {
    LocalDate date = LocalDate.of(2026, 8, 10);
    when(currencyRateRepository.resolveFxRatesForDate(date))
        .thenReturn(
            List.of(
                resolution("USD", "PLN", "4.0", "MARKET_DAILY", "NBP", "2026-08-10", "OK"),
                resolution("EUR", "PLN", "4.3", "MARKET_DAILY", "NBP", "2026-08-10", "OK"),
                resolution("PLN", "EUR", "0.23", "MARKET_DAILY", "NBP", "2026-08-10", "OK"),
                resolution("EUR", "USD", "1", "HISTORICAL_MONTHLY", "NBP", "2026-07-31", "STALE")));

    assertTrue(service.resolveRate(CurrencyType.USD, CurrencyType.PLN, date).isUsable());
    assertTrue(service.resolveRate(CurrencyType.EUR, CurrencyType.PLN, date).isUsable());
    assertTrue(service.resolveRate(CurrencyType.PLN, CurrencyType.EUR, date).isUsable());
    assertFalse(service.resolveRate(CurrencyType.EUR, CurrencyType.USD, date).isUsable());
    assertFalse(service.resolveRate(CurrencyType.USD, CurrencyType.EUR, date).isUsable());
    assertFalse(service.resolveRate(CurrencyType.USD, CurrencyType.EUR, date).isUsable());

    verify(currencyRateRepository, times(1)).resolveFxRatesForDate(date);
    verify(currencyRateRepository, org.mockito.Mockito.never()).resolveFxRate(date, "USD", "EUR");
  }

  @DisplayName("cache Separates Dates And Invalidates After Clear Or Rate Update")
  @Test
  void cacheSeparatesDatesAndInvalidatesAfterClearOrRateUpdate() {
    LocalDate first = LocalDate.of(2026, 8, 10);
    LocalDate second = first.plusDays(1);
    when(currencyRateRepository.resolveFxRatesForDate(first))
        .thenReturn(
            List.of(resolution("PLN", "USD", "0.25", "MARKET_DAILY", "NBP", "2026-08-10", "OK")));
    when(currencyRateRepository.resolveFxRatesForDate(second))
        .thenReturn(
            List.of(resolution("PLN", "USD", "0.24", "MARKET_DAILY", "NBP", "2026-08-11", "OK")));

    service.resolveRate(CurrencyType.PLN, CurrencyType.USD, first);
    service.resolveRate(CurrencyType.PLN, CurrencyType.USD, second);
    verify(currencyRateRepository, times(1)).resolveFxRatesForDate(first);
    verify(currencyRateRepository, times(1)).resolveFxRatesForDate(second);

    service.clearValuationResolutionCache();
    service.resolveRate(CurrencyType.PLN, CurrencyType.USD, first);
    verify(currencyRateRepository, times(2)).resolveFxRatesForDate(first);

    service.updateRates(CurrencyType.USD, Map.of(), first);
    service.resolveRate(CurrencyType.PLN, CurrencyType.USD, first);
    verify(currencyRateRepository, times(3)).resolveFxRatesForDate(first);
  }

  private static CurrencyRateEntity rate(
      CurrencyType base, CurrencyType to, LocalDate date, double value) {
    CurrencyRateEntity r = new CurrencyRateEntity();
    r.setRateDate(date);
    r.setBase(base);
    r.setToCurrency(to);
    r.setRate(java.math.BigDecimal.valueOf(value));
    r.setSource("STATIC_BOOTSTRAP");
    r.setMethod("HISTORICAL_MONTHLY");
    return r;
  }

  @DisplayName("warm Valuation Matrices Preloads Range In One Query And Serves Cache")
  @Test
  void warmValuationMatricesPreloadsRangeInOneQueryAndServesCache() {
    LocalDate start = LocalDate.of(2026, 6, 15);
    LocalDate end = LocalDate.of(2026, 6, 17);
    when(currencyRateRepository.resolveFxRatesForDateRange(start, end))
        .thenReturn(
            List.of(
                resolution(
                    "USD", "PLN", "4.0", "MARKET_DAILY", "FX", "2026-06-15", "OK", "2026-06-15"),
                resolution(
                    "USD", "PLN", "4.1", "MARKET_DAILY", "FX", "2026-06-16", "OK", "2026-06-16")));

    service.warmValuationMatrices(start, end);

    // Every date in the range is served from the warmed cache without a per-date lazy query.
    assertEquals(
        40.0, service.convertToBaseCurrency(10.0, CurrencyType.PLN, CurrencyType.USD, start), 1e-9);
    assertEquals(
        41.0,
        service.convertToBaseCurrency(
            10.0, CurrencyType.PLN, CurrencyType.USD, LocalDate.of(2026, 6, 16)),
        1e-9);
    // A day with no resolved rows is still cached (as MISSING) so it does not trigger a lazy query.
    assertFalse(
        service
            .findRate(CurrencyType.PLN, CurrencyType.USD, LocalDate.of(2026, 6, 17))
            .isPresent());

    verify(currencyRateRepository, times(1)).resolveFxRatesForDateRange(start, end);
    verify(currencyRateRepository, times(0)).resolveFxRatesForDate(any());
  }

  @DisplayName("warm Valuation Matrices Ignores Invalid Ranges")
  @Test
  void warmValuationMatricesIgnoresInvalidRanges() {
    service.warmValuationMatrices(LocalDate.of(2026, 6, 17), LocalDate.of(2026, 6, 15));
    service.warmValuationMatrices(null, LocalDate.of(2026, 6, 15));

    verifyNoInteractions(currencyRateRepository);
  }

  private static FxRateResolutionRow resolution(
      String source,
      String target,
      String rate,
      String method,
      String rateSource,
      String date,
      String status) {
    return resolution(source, target, rate, method, rateSource, date, status, null);
  }

  private static FxRateResolutionRow resolution(
      String source,
      String target,
      String rate,
      String method,
      String rateSource,
      String date,
      String status,
      String valuationDate) {
    return new FxRateResolutionRow() {
      public LocalDate getValuationDate() {
        return valuationDate == null ? null : LocalDate.parse(valuationDate);
      }

      public String getSourceCurrency() {
        return source;
      }

      public String getTargetCurrency() {
        return target;
      }

      public BigDecimal getFxRateToTarget() {
        return new BigDecimal(rate);
      }

      public String getSource() {
        return method;
      }

      public String getRateMethod() {
        return method;
      }

      public String getRateSource() {
        return rateSource;
      }

      public LocalDate getSourceRateDate() {
        return LocalDate.parse(date);
      }

      public Integer getAgeDays() {
        return 0;
      }

      public String getConversionStatus() {
        return status;
      }
    };
  }
}
