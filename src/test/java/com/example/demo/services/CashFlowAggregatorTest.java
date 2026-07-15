package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.testsupport.portfolio.PortfolioBuilders;
import com.example.demo.testsupport.portfolio.PortfolioScenarios;
import com.example.demo.testsupport.portfolio.PortfolioTestContext;
import com.example.demo.testsupport.portfolio.PortfolioTestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;

@ExtendWith(MockitoExtension.class)
class CashFlowAggregatorTest {

    @Mock private CurrencyRateService currencyRateService;

    private CashFlowAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new CashFlowAggregator(currencyRateService);
        // Identity FX so amounts pass through unchanged. lenient() because the empty-input test
        // never triggers conversion.
        org.mockito.Mockito.lenient().when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any(), any(LocalDate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
    }

    @Test
    void aggregate_sumsDepositsWithdrawalsInterestAndDividends() {
        PortfolioTestContext dividendScenario = PortfolioScenarios.createDividendScenario();
        CashFlowAggregator.CashFlowSummary summary = aggregator.aggregate(List.of(
                PortfolioBuilders.cashOperation()
                        .deposit(1000.0, CurrencyType.USD)
                        .comment("wire")
                        .build(),
                PortfolioBuilders.cashOperation()
                        .withdrawal(200.0, CurrencyType.USD)
                        .comment("wire")
                        .build(),
                op(CashOperationType.FREE_FUNDS_INTEREST, 12.0, null),
                op(CashOperationType.FREE_FUNDS_INTEREST_TAX, -2.0, null),
                dividendScenario.operations().aaplDividend(),
                dividendScenario.operations().aaplWithholdingTax()
        ), CurrencyType.USD);

        assertEquals(1000.0, summary.deposits(), 0.01);
        assertEquals(-200.0, summary.withdrawals(), 0.01);
        assertEquals(800.0, summary.netDeposits(), 0.01);
        assertEquals(10.0, summary.interest(), 0.01);
        assertEquals(dividendScenario.expected().dividend().netCashIncrease(), summary.dividends(), 0.01);
        assertEquals(-dividendScenario.expected().dividend().tax(), summary.dividendTax(), 0.01);
        assertEquals(dividendScenario.expected().dividend().netCashIncrease(), summary.dividendsByCurrency().get(CurrencyType.USD), 0.01);
    }

    @Test
    void aggregate_excludesInternalTransfersAndCurrencyConversions() {
        PortfolioTestContext transferScenario = PortfolioScenarios.createInternalCashTransferScenario();
        CashFlowAggregator.CashFlowSummary summary = aggregator.aggregate(List.of(
                op(CashOperationType.DEPOSIT, 500.0, "Currency Conversion EUR -> USD"),
                op(CashOperationType.DEPOSIT, 100.0, "Transfer from sub-account"),
                op(CashOperationType.WITHDRAWAL, -50.0, "Transfer to sub-account"),
                transferScenario.operations().transferOut(),
                transferScenario.operations().transferIn(),
                PortfolioBuilders.cashOperation()
                        .deposit(1000.0, CurrencyType.USD)
                        .comment("Electronic Fund Transfer")
                        .build()
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
        return PortfolioBuilders.cashOperation()
                .type(type)
                .deposit(amount, CurrencyType.USD)
                .type(type)
                .comment(comment)
                .on(PortfolioTestData.JANUARY_DEPOSIT_DATE)
                .build();
    }
}

