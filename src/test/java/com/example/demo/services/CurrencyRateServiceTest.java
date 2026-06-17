package com.example.demo.services;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CurrencyRate;
import com.example.demo.infrastructure.repository.CurrencyRateRepository;
import com.example.demo.services.currency.CurrencyRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyRateServiceTest {

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    private CurrencyRateService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyRateService(currencyRateRepository);
        // Reset the static cache so tests are isolated.
        when(currencyRateRepository.findAll()).thenReturn(List.of(
                rate(CurrencyType.USD, CurrencyType.EUR, 0.9),
                rate(CurrencyType.USD, CurrencyType.PLN, 4.0)
        ));
        service.preloadExchangeRates();
    }

    @Test
    void convertToBaseCurrency_returnsAmountUnchangedForSameCurrency() {
        assertEquals(100.0, service.convertToBaseCurrency(100.0, CurrencyType.USD, CurrencyType.USD));
    }

    @Test
    void convertToBaseCurrency_dividesByCachedRate() {
        // amount in EUR -> base USD: 90 EUR / 0.9 = 100 USD
        assertEquals(100.0, service.convertToBaseCurrency(90.0, CurrencyType.USD, CurrencyType.EUR), 1e-9);
        // amount in PLN -> base USD: 400 PLN / 4 = 100 USD
        assertEquals(100.0, service.convertToBaseCurrency(400.0, CurrencyType.USD, CurrencyType.PLN), 1e-9);
    }

    @Test
    void convertToBaseCurrency_returnsAmountUnconvertedWhenRateMissing() {
        // GBP rate is not in the cache (and not a supported enum, so simulate by reloading empty cache)
        when(currencyRateRepository.findAll()).thenReturn(List.of()); // empty preload
        CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
        // Convert EUR -> PLN (unknown direction in cache); falls through and returns amount.
        double result = freshService.convertToBaseCurrency(123.0, CurrencyType.PLN, CurrencyType.EUR);
        assertEquals(123.0, result);
    }

    @Test
    void updateRates_persistsNewRateWhenAbsent() {
        when(currencyRateRepository.findByBaseAndToCurrency(CurrencyType.USD, CurrencyType.EUR))
                .thenReturn(Optional.empty());

        service.updateRates(CurrencyType.USD, Map.of(CurrencyType.EUR, 0.95));

        ArgumentCaptor<CurrencyRate> captor = ArgumentCaptor.forClass(CurrencyRate.class);
        verify(currencyRateRepository).save(captor.capture());
        CurrencyRate saved = captor.getValue();
        assertEquals(CurrencyType.USD, saved.getBase());
        assertEquals(CurrencyType.EUR, saved.getToCurrency());
        assertEquals(0.95, saved.getRate());
    }

    @Test
    void updateRates_updatesExistingRate() {
        CurrencyRate existing = rate(CurrencyType.USD, CurrencyType.EUR, 0.8);
        when(currencyRateRepository.findByBaseAndToCurrency(CurrencyType.USD, CurrencyType.EUR))
                .thenReturn(Optional.of(existing));

        service.updateRates(CurrencyType.USD, Map.of(CurrencyType.EUR, 0.92));

        verify(currencyRateRepository).save(existing);
        assertEquals(0.92, existing.getRate());
    }

    @Test
    void getRate_returnsPersistedRate() {
        when(currencyRateRepository.findByBaseAndToCurrency(CurrencyType.USD, CurrencyType.EUR))
                .thenReturn(Optional.of(rate(CurrencyType.USD, CurrencyType.EUR, 0.91)));

        assertEquals(0.91, service.getRate(CurrencyType.USD, CurrencyType.EUR));
    }

    @Test
    void getRate_throwsWhenMissing() {
        when(currencyRateRepository.findByBaseAndToCurrency(any(), any())).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.getRate(CurrencyType.USD, CurrencyType.PLN));
    }

    private static CurrencyRate rate(CurrencyType base, CurrencyType to, double value) {
        CurrencyRate r = new CurrencyRate();
        r.setBase(base);
        r.setToCurrency(to);
        r.setRate(value);
        return r;
    }
}

