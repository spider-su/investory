package com.example.demo.services;

import com.example.demo.data.CashOperationType;
import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.CashOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowAggregatorTest {

    @Mock private CurrencyRateService currencyRateService;

    private CashFlowAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new CashFlowAggregator(currencyRateService);
        // Identity FX so amounts pass through unchanged. lenient() because the empty-input test
        // never triggers conversion.
        org.mockito.Mockito.lenient().when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
    }

    @Test
    void aggregate_sumsDepositsWithdrawalsInterestAndDividends() {
        CashFlowAggregator.CashFlowSummary summary = aggregator.aggregate(List.of(
                op(CashOperationType.DEPOSIT, 1000.0, "wire"),
                op(CashOperationType.WITHDRAWAL, -200.0, "wire"),
                op(CashOperationType.FREE_FUNDS_INTEREST, 12.0, null),
                op(CashOperationType.FREE_FUNDS_INTEREST_TAX, -2.0, null),
                op(CashOperationType.DIVIDEND, 50.0, null),
                op(CashOperationType.WITHHOLDING_TAX, -7.5, null)
        ), CurrencyType.USD);

        assertEquals(1000.0, summary.deposits(), 0.01);
        assertEquals(-200.0, summary.withdrawals(), 0.01);
        assertEquals(800.0, summary.netDeposits(), 0.01);
        assertEquals(10.0, summary.interest(), 0.01);
        // Dividends are net of withholding tax: 50 + (-7.5) = 42.5
        assertEquals(42.5, summary.dividends(), 0.01);
        assertEquals(-7.5, summary.dividendTax(), 0.01);
        assertEquals(42.5, summary.dividendsByCurrency().get(CurrencyType.USD), 0.01);
    }

    @Test
    void aggregate_excludesInternalTransfersAndCurrencyConversions() {
        CashFlowAggregator.CashFlowSummary summary = aggregator.aggregate(List.of(
                op(CashOperationType.DEPOSIT, 500.0, "Currency Conversion EUR -> USD"),
                op(CashOperationType.DEPOSIT, 100.0, "Transfer from sub-account"),
                op(CashOperationType.WITHDRAWAL, -50.0, "Transfer to sub-account"),
                op(CashOperationType.DEPOSIT, 1000.0, "Electronic Fund Transfer")
        ), CurrencyType.USD);

        // Currency conversion + sub-account transfers excluded -> only the real wire counts.
        assertEquals(1000.0, summary.deposits(), 0.01);
        assertEquals(0.0, summary.withdrawals(), 0.01);
    }

    @Test
    void aggregate_returnsZeroSummaryForEmptyInput() {
        CashFlowAggregator.CashFlowSummary summary = aggregator.aggregate(List.of(), CurrencyType.USD);
        assertEquals(0.0, summary.deposits());
        assertEquals(0.0, summary.dividends());
        assertEquals(0.0, summary.interest());
        assertEquals(0, summary.dividendsByCurrency().size());
    }

    private static CashOperation op(CashOperationType type, double amount, String comment) {
        CashOperation c = new CashOperation();
        c.setType(type);
        c.setAmount(amount);
        c.setCurrency(CurrencyType.USD);
        c.setComment(comment);
        return c;
    }
}

