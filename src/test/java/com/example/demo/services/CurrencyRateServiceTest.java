package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CurrencyRate;
import com.example.demo.infrastructure.repository.CurrencyRateRepository;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.currency.FxRateUnavailableException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
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
    when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscRateDateAsc())
        .thenReturn(
            List.of(
                rate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 6, 1), 0.9),
                rate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 1), 0.85),
                rate(CurrencyType.USD, CurrencyType.PLN, LocalDate.of(2026, 6, 1), 4.0),
                rate(CurrencyType.EUR, CurrencyType.USD, LocalDate.of(2026, 6, 1), 1.1)));
    service.preloadExchangeRates();
  }

  @Test
  void convertToBaseCurrency_returnsAmountUnchangedForSameCurrency() {
    assertEquals(
        100.0,
        service.convertToBaseCurrency(
            100.0, CurrencyType.USD, CurrencyType.USD, LocalDate.of(2026, 7, 5)));
  }

  @Test
  void convertToBaseCurrency_usesHistoricalRateForRequestedDate() {
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
  void convertToBaseCurrency_usesInverseRateWhenDirectMissing() {
    when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscRateDateAsc())
        .thenReturn(
            List.of(rate(CurrencyType.EUR, CurrencyType.USD, LocalDate.of(2026, 6, 1), 1.1)));
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
    freshService.preloadExchangeRates();

    assertEquals(
        121.0,
        freshService.convertToBaseCurrency(
            110.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 6, 15)),
        1e-9);
  }

  @Test
  void convertToBaseCurrency_throwsWhenRateMissing() {
    when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscRateDateAsc())
        .thenReturn(List.of());
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
    freshService.preloadExchangeRates();
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
    when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscRateDateAsc())
        .thenReturn(
            List.of(rate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 1), 0.91)));
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
    freshService.preloadExchangeRates();

    assertEquals(
        0.91, freshService.getRate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 5)));
  }

  @Test
  void getRate_throwsWhenMissing() {
    when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscRateDateAsc())
        .thenReturn(List.of());
    CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
    freshService.preloadExchangeRates();
    assertThrows(
        RuntimeException.class,
        () -> freshService.getRate(CurrencyType.USD, CurrencyType.PLN, LocalDate.of(2026, 7, 5)));
  }

  @Test
  void harvestXtbExecutionRatePreservesPairDateAndMethod() {
    com.example.demo.infrastructure.repository.CashOperation operation =
        new com.example.demo.infrastructure.repository.CashOperation();
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
  }

  @Test
  void transactionResolverUsesExecutionRateBeforeValuationRate() {
    CurrencyRate execution = rate(CurrencyType.USD, CurrencyType.PLN, LocalDate.of(2026, 1, 2), 3.573631);
    execution.setMethod("XTB_EXECUTION");
    execution.setSource("XTB");
    execution.setObservedAt(ZonedDateTime.of(2026, 1, 2, 19, 54, 12, 0, ZoneOffset.UTC));
    when(currencyRateRepository.findAllByMethodInAndObservedAtIsNotNullOrderByObservedAtDesc(List.of("XTB_EXECUTION", "IBKR_EXECUTION")))
        .thenReturn(List.of(execution));

    CurrencyRateService.FxRateResolution result = service.resolveTransactionRate(
        ZonedDateTime.of(2026, 1, 2, 20, 0, 0, 0, ZoneOffset.UTC), CurrencyType.USD, CurrencyType.PLN);

    assertEquals("XTB_EXECUTION", result.rateMethod());
    assertEquals("OK", result.conversionStatus());
    assertEquals(3.573631, result.fxRateToTarget().doubleValue(), 1e-8);
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
}
