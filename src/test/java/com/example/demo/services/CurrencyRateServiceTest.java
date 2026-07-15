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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyRateServiceTest {

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    private CurrencyRateService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyRateService(currencyRateRepository);
        when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscMonthStartAsc()).thenReturn(List.of(
                rate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 6, 1), 0.9),
                rate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 1), 0.85),
                rate(CurrencyType.USD, CurrencyType.PLN, LocalDate.of(2026, 6, 1), 4.0),
                rate(CurrencyType.EUR, CurrencyType.USD, LocalDate.of(2026, 6, 1), 1.1)
        ));
        service.preloadExchangeRates();
    }

    @Test
    void convertToBaseCurrency_returnsAmountUnchangedForSameCurrency() {
        assertEquals(100.0, service.convertToBaseCurrency(100.0, CurrencyType.USD, CurrencyType.USD, LocalDate.of(2026, 7, 5)));
    }

    @Test
    void convertToBaseCurrency_usesHistoricalRateForRequestedDate() {
        // Uses the latest rate on or before 2026-06-15 => 2026-06-01.
        assertEquals(100.0, service.convertToBaseCurrency(90.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 6, 15)), 1e-9);
        // Uses the normalized month start 2026-07-01.
        assertEquals(105.88235294117646, service.convertToBaseCurrency(90.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 5)), 1e-9);
    }

    @Test
    void convertToBaseCurrency_usesInverseRateWhenDirectMissing() {
        when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscMonthStartAsc()).thenReturn(List.of(
                rate(CurrencyType.EUR, CurrencyType.USD, LocalDate.of(2026, 6, 1), 1.1)
        ));
        CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
        freshService.preloadExchangeRates();

        assertEquals(121.0, freshService.convertToBaseCurrency(110.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 6, 15)), 1e-9);
    }

    @Test
    void convertToBaseCurrency_returnsAmountUnconvertedWhenRateMissing() {
        when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscMonthStartAsc()).thenReturn(List.of());
        CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
        double result = freshService.convertToBaseCurrency(123.0, CurrencyType.PLN, CurrencyType.EUR, LocalDate.of(2026, 7, 5));
        assertEquals(123.0, result);
    }

    @Test
    void updateRates_persistsNewRateWhenAbsent() {
        when(currencyRateRepository.findByMonthStartAndBaseAndToCurrency(LocalDate.of(2026, 7, 1), CurrencyType.USD, CurrencyType.EUR))
                .thenReturn(Optional.empty());

        service.updateRates(CurrencyType.USD, Map.of(CurrencyType.EUR, 0.95), LocalDate.of(2026, 7, 5));

        ArgumentCaptor<CurrencyRate> captor = ArgumentCaptor.forClass(CurrencyRate.class);
        verify(currencyRateRepository).save(captor.capture());
        CurrencyRate saved = captor.getValue();
        assertEquals(LocalDate.of(2026, 7, 1), saved.getMonthStart());
        assertEquals(CurrencyType.USD, saved.getBase());
        assertEquals(CurrencyType.EUR, saved.getToCurrency());
        assertEquals(0.95, saved.getRate());
    }

    @Test
    void updateRates_updatesExistingRate() {
        CurrencyRate existing = rate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 1), 0.8);
        when(currencyRateRepository.findByMonthStartAndBaseAndToCurrency(LocalDate.of(2026, 7, 1), CurrencyType.USD, CurrencyType.EUR))
                .thenReturn(Optional.of(existing));

        service.updateRates(CurrencyType.USD, Map.of(CurrencyType.EUR, 0.92), LocalDate.of(2026, 7, 5));

        verify(currencyRateRepository).save(existing);
        assertEquals(LocalDate.of(2026, 7, 1), existing.getMonthStart());
        assertEquals(0.92, existing.getRate());
    }

    @Test
    void getRate_returnsPersistedRate() {
        when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscMonthStartAsc()).thenReturn(List.of(
                rate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 1), 0.91)
        ));
        CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
        freshService.preloadExchangeRates();

        assertEquals(0.91, freshService.getRate(CurrencyType.USD, CurrencyType.EUR, LocalDate.of(2026, 7, 5)));
    }

    @Test
    void getRate_throwsWhenMissing() {
        when(currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscMonthStartAsc()).thenReturn(List.of());
        CurrencyRateService freshService = new CurrencyRateService(currencyRateRepository);
        freshService.preloadExchangeRates();
        assertThrows(RuntimeException.class, () -> freshService.getRate(CurrencyType.USD, CurrencyType.PLN, LocalDate.of(2026, 7, 5)));
    }

    private static CurrencyRate rate(CurrencyType base, CurrencyType to, LocalDate date, double value) {
        CurrencyRate r = new CurrencyRate();
        r.setMonthStart(date);
        r.setBase(base);
        r.setToCurrency(to);
        r.setRate(value);
        return r;
    }
}

