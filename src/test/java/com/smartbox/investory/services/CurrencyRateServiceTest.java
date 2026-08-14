package com.smartbox.investory.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.infrastructure.repository.CurrencyRate;
import com.smartbox.investory.infrastructure.repository.CurrencyRateRepository;
import com.smartbox.investory.infrastructure.repository.FxRateResolutionRow;
import com.smartbox.investory.services.currency.CurrencyRateService;
import com.smartbox.investory.services.currency.FxRateUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrencyRateServiceTest {

  @Mock private CurrencyRateRepository currencyRateRepository;

  private CurrencyRateService service;

  @BeforeEach
  void setUp() {
    service = new CurrencyRateService(currencyRateRepository);
  }

  @Test
  void convertToBaseCurrency_returnsAmountUnchangedForSameCurrency() {
    assertEquals(
        100.0,
        service.convertToBaseCurrency(
            100.0, CurrencyType.USD, CurrencyType.USD, LocalDate.of(2026, 7, 5)));
    verifyNoInteractions(currencyRateRepository);
  }

  @Test
  void convertToBaseCurrency_usesHistoricalRateForRequestedDate() {
    when(currencyRateRepository.resolveFxRate(LocalDate.of(2026, 6, 15), "EUR", "USD", "VALUATION"))
        .thenReturn(
            Optional.of(
                resolution(
                    "EUR", "USD", "1.1", "HISTORICAL_MONTHLY", "NBP", "2026-06-01", "ESTIMATED")));
    when(currencyRateRepository.resolveFxRate(LocalDate.of(2026, 7, 5), "EUR", "USD", "VALUATION"))
        .thenReturn(
            Optional.of(
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

  @Test
  void convertToBaseCurrency_usesInverseRateWhenDirectMissing() {
    when(currencyRateRepository.resolveFxRate(LocalDate.of(2026, 6, 15), "EUR", "USD", "VALUATION"))
        .thenReturn(
            Optional.of(
                resolution(
                    "EUR", "USD", "1.1", "INVERSE", "STATIC_BOOTSTRAP", "2026-06-01", "OK")));
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);

    assertEquals(
        121.0,
        freshService.convertToBaseCurrency(
            110.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 6, 15)),
        1e-9);
  }

  @Test
  void convertToBaseCurrency_throwsWhenRateMissing() {
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
    assertThrows(
        FxRateUnavailableException.class,
        () ->
            freshService.convertToBaseCurrency(
                123.0, CurrencyType.PLN, CurrencyType.EUR, LocalDate.of(2026, 7, 5)));
  }

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

    ArgumentCaptor<CurrencyRate> captor = ArgumentCaptor.forClass(CurrencyRate.class);
    verify(currencyRateRepository).save(captor.capture());
    CurrencyRate saved = captor.getValue();
    assertEquals(LocalDate.of(2026, 7, 5), saved.getRateDate());
    assertEquals(CurrencyType.USD, saved.getBase());
    assertEquals(CurrencyType.EUR, saved.getToCurrency());
    assertEquals(0.95, saved.getRate());
  }

  @Test
  void updateRates_updatesExistingRate() {
    CurrencyRate existing = rate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 5), 0.8);
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
    assertEquals(0.92, existing.getRate());
  }

  @Test
  void getRate_returnsPersistedRate() {
    when(currencyRateRepository.resolveFxRate(LocalDate.of(2026, 7, 5), "USD", "EUR", "VALUATION"))
        .thenReturn(
            Optional.of(
                resolution(
                    "USD", "EUR", "0.91", "DIRECT", "EXCHANGERATE_HOST", "2026-07-01", "OK")));
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);

    assertEquals(
        0.91, freshService.getRate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 5)));
  }

  @Test
  void getRate_throwsWhenMissing() {
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
    assertThrows(
        RuntimeException.class,
        () -> freshService.getRate(CurrencyType.USD, CurrencyType.PLN, LocalDate.of(2026, 7, 5)));
  }

  @Test
  void harvestXtbExecutionRatePreservesPairDateAndMethod() {
    com.smartbox.investory.infrastructure.repository.CashOperation operation =
        new com.smartbox.investory.infrastructure.repository.CashOperation();
    operation.setId(42L);
    operation.setDate(ZonedDateTime.of(2026, 1, 2, 19, 54, 12, 0, ZoneOffset.UTC));
    operation.setComment("Currency conversion, USD to PLN, Exchange rate:3.573631");

    service.harvestXtbExecutionRates(List.of(operation));

    ArgumentCaptor<CurrencyRate> captor = ArgumentCaptor.forClass(CurrencyRate.class);
    verify(currencyRateRepository).save(captor.capture());
    CurrencyRate saved = captor.getValue();
    assertEquals(LocalDate.of(2026, 1, 2), saved.getRateDate());
    assertEquals(CurrencyType.USD, saved.getBase());
    assertEquals(CurrencyType.PLN, saved.getToCurrency());
    assertEquals(new BigDecimal("3.57363100"), saved.getRateValue());
    assertEquals("XTB_EXECUTION", saved.getMethod());
    assertEquals(CurrencyType.USD, operation.getExecutionFxBase());
    assertEquals(CurrencyType.PLN, operation.getExecutionFxToCurrency());
    assertEquals(new BigDecimal("3.573631"), operation.getExecutionFxRate());
  }

  @Test
  void harvestXtbExecutionRatesKeepDifferentSameDayRatesAttachedToTheirOperations() {
    com.smartbox.investory.infrastructure.repository.CashOperation first =
        new com.smartbox.investory.infrastructure.repository.CashOperation();
    first.setId(100L);
    first.setDate(ZonedDateTime.of(2026, 1, 2, 10, 0, 0, 0, ZoneOffset.UTC));
    first.setComment("Currency conversion, USD to PLN, Exchange rate:3.50");
    com.smartbox.investory.infrastructure.repository.CashOperation second =
        new com.smartbox.investory.infrastructure.repository.CashOperation();
    second.setId(101L);
    second.setDate(ZonedDateTime.of(2026, 1, 2, 15, 0, 0, 0, ZoneOffset.UTC));
    second.setComment("Currency conversion, USD to PLN, Exchange rate:3.60");

    service.harvestXtbExecutionRates(List.of(first, second));

    assertEquals(new BigDecimal("3.50"), first.getExecutionFxRate());
    assertEquals(new BigDecimal("3.60"), second.getExecutionFxRate());
    assertEquals("XTB:OPERATION:100", first.getExecutionFxReference());
    assertEquals("XTB:OPERATION:101", second.getExecutionFxReference());
  }

  @Test
  void transactionResolverDoesNotBorrowUnboundExecutionRate() {
    CurrencyRate execution =
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
  void cachesUsableCanonicalResolutionByDateAndPair() {
    LocalDate date = LocalDate.of(2026, 8, 10);
    when(currencyRateRepository.resolveFxRate(date, "PLN", "USD", "VALUATION"))
        .thenReturn(
            Optional.of(
                resolution("PLN", "USD", "0.25", "MARKET_DAILY", "NBP", "2026-08-10", "OK")));

    for (int index = 0; index < 100; index++) {
      assertEquals(
          25.0, service.convertToBaseCurrency(100.0, CurrencyType.USD, CurrencyType.PLN, date));
    }

    verify(currencyRateRepository, times(1)).resolveFxRate(date, "PLN", "USD", "VALUATION");
  }

  @Test
  void cacheInvalidatesAfterRatesChangeAndDoesNotCacheStaleResults() {
    LocalDate date = LocalDate.of(2026, 8, 10);
    when(currencyRateRepository.resolveFxRate(date, "PLN", "USD", "VALUATION"))
        .thenReturn(
            Optional.of(
                resolution("PLN", "USD", "0.25", "MARKET_DAILY", "NBP", "2026-08-10", "OK")));
    service.resolveRate(CurrencyType.PLN, CurrencyType.USD, date);
    service.updateRates(CurrencyType.USD, Map.of(), date);
    service.resolveRate(CurrencyType.PLN, CurrencyType.USD, date);
    verify(currencyRateRepository, times(2)).resolveFxRate(date, "PLN", "USD", "VALUATION");

    when(currencyRateRepository.resolveFxRate(date, "EUR", "USD", "VALUATION"))
        .thenReturn(
            Optional.of(
                resolution("EUR", "USD", "1", "HISTORICAL_MONTHLY", "NBP", "2026-07-31", "STALE")));
    assertFalse(service.resolveRate(CurrencyType.EUR, CurrencyType.USD, date).isUsable());
    assertFalse(service.resolveRate(CurrencyType.EUR, CurrencyType.USD, date).isUsable());
    verify(currencyRateRepository, times(2)).resolveFxRate(date, "EUR", "USD", "VALUATION");
  }

  @Test
  void cacheSeparatesDatesAndCurrencyPairs() {
    LocalDate first = LocalDate.of(2026, 8, 10);
    LocalDate second = first.plusDays(1);
    when(currencyRateRepository.resolveFxRate(first, "PLN", "USD", "VALUATION"))
        .thenReturn(
            Optional.of(
                resolution("PLN", "USD", "0.25", "MARKET_DAILY", "NBP", "2026-08-10", "OK")));
    when(currencyRateRepository.resolveFxRate(second, "PLN", "USD", "VALUATION"))
        .thenReturn(
            Optional.of(
                resolution("PLN", "USD", "0.24", "MARKET_DAILY", "NBP", "2026-08-11", "OK")));
    when(currencyRateRepository.resolveFxRate(first, "EUR", "USD", "VALUATION"))
        .thenReturn(
            Optional.of(
                resolution("EUR", "USD", "1.1", "MARKET_DAILY", "NBP", "2026-08-10", "OK")));

    service.resolveRate(CurrencyType.PLN, CurrencyType.USD, first);
    service.resolveRate(CurrencyType.PLN, CurrencyType.USD, second);
    service.resolveRate(CurrencyType.EUR, CurrencyType.USD, first);

    verify(currencyRateRepository).resolveFxRate(first, "PLN", "USD", "VALUATION");
    verify(currencyRateRepository).resolveFxRate(second, "PLN", "USD", "VALUATION");
    verify(currencyRateRepository).resolveFxRate(first, "EUR", "USD", "VALUATION");
  }

  private static CurrencyRate rate(
      CurrencyType base, CurrencyType to, LocalDate date, double value) {
    CurrencyRate r = new CurrencyRate();
    r.setRateDate(date);
    r.setBase(base);
    r.setToCurrency(to);
    r.setRate(value);
    r.setSource("STATIC_BOOTSTRAP");
    r.setMethod("HISTORICAL_MONTHLY");
    return r;
  }

  private static FxRateResolutionRow resolution(
      String source,
      String target,
      String rate,
      String method,
      String rateSource,
      String date,
      String status) {
    return new FxRateResolutionRow() {
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
