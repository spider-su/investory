package com.example.demo.services;

import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.ClosedPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxCalculatorTest {

    @Mock private CurrencyRateService currencyRateService;

    private TaxCalculator taxCalculator;

    @BeforeEach
    void setUp() {
        taxCalculator = new TaxCalculator(currencyRateService);
        // lenient() because the empty-trades test never triggers FX conversion.
        org.mockito.Mockito.lenient().when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
    }

    @Test
    void calculate_returnsZeroWhenThereAreNoTrades() {
        TaxCalculator.TaxSummary tax = taxCalculator.calculate(List.of(), CurrencyType.USD, 2026);
        assertEquals(0.0, tax.capitalGainsTax());
        assertEquals(0.0, tax.lossCarryForward());
    }

    @Test
    void calculate_appliesNineteenPercentToCurrentYearNetGains() {
        TaxCalculator.TaxSummary tax = taxCalculator.calculate(List.of(
                closed(1000.0, 0.0, 0.0, ZonedDateTime.now().withYear(2026))
        ), CurrencyType.USD, 2026);

        assertEquals(190.0, tax.capitalGainsTax(), 0.01);
        assertEquals(0.0, tax.lossCarryForward());
    }

    @Test
    void calculate_consumesPriorYearLossesAgainstCurrentYearGains() {
        TaxCalculator.TaxSummary tax = taxCalculator.calculate(List.of(
                closed(-400.0, 0.0, 0.0, ZonedDateTime.now().withYear(2024)),
                closed(1000.0, 0.0, 0.0, ZonedDateTime.now().withYear(2026))
        ), CurrencyType.USD, 2026);

        // Gain (1000) - applied loss (400) = 600 taxable -> 19% = 114.
        assertEquals(114.0, tax.capitalGainsTax(), 0.01);
        assertEquals(400.0, tax.lossCarryForward(), 0.01);
    }

    @Test
    void calculate_ignoresLossesOlderThanFiveYears() {
        TaxCalculator.TaxSummary tax = taxCalculator.calculate(List.of(
                closed(-1000.0, 0.0, 0.0, ZonedDateTime.now().withYear(2018)),
                closed(500.0, 0.0, 0.0, ZonedDateTime.now().withYear(2026))
        ), CurrencyType.USD, 2026);

        // 2018 loss is outside the 5-year window for 2026 (2026 - 5 = 2021).
        assertEquals(95.0, tax.capitalGainsTax(), 0.01);
        assertEquals(0.0, tax.lossCarryForward());
    }

    @Test
    void calculate_returnsZeroTaxWhenCurrentYearIsNetLoss() {
        TaxCalculator.TaxSummary tax = taxCalculator.calculate(List.of(
                closed(-500.0, 0.0, 0.0, ZonedDateTime.now().withYear(2026))
        ), CurrencyType.USD, 2026);

        assertEquals(0.0, tax.capitalGainsTax());
    }

    private static ClosedPosition closed(double profit, double commission, double swap, ZonedDateTime closeTime) {
        ClosedPosition cp = new ClosedPosition();
        cp.setProfit(profit);
        cp.setCommission(commission);
        cp.setSwap(swap);
        cp.setCurrency(CurrencyType.USD);
        cp.setCloseTime(closeTime);
        return cp;
    }
}

