package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.testsupport.portfolio.PortfolioBuilders;
import com.example.demo.testsupport.portfolio.PortfolioScenarios;
import com.example.demo.testsupport.portfolio.PortfolioTestContext;
import com.example.demo.testsupport.portfolio.PortfolioTestData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CashFlowAggregatorTest {

    @Mock private CurrencyRateService currencyRateService;

    private CashFlowAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new CashFlowAggregator(currencyRateService);
        // Identity FX so amounts pass through unchanged. lenient() because the empty-input test
        // never triggers conversion.
        org.mockito.Mockito.lenient().when(currencyRateService.convertToBaseCurrency(any(BigDecimal.class), any(), any(), any(LocalDate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));
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

        assertEquals(new BigDecimal("1000.00000000"), summary.deposits());
        assertEquals(new BigDecimal("-200.00000000"), summary.withdrawals());
        assertEquals(new BigDecimal("800.00000000"), summary.netDeposits());
        assertEquals(new BigDecimal("10.00000000"), summary.interest());
        assertEquals(BigDecimal.valueOf(dividendScenario.expected().dividend().netCashIncrease()).setScale(8, RoundingMode.HALF_UP), summary.dividends());
        assertEquals(BigDecimal.valueOf(-dividendScenario.expected().dividend().tax()).setScale(8, RoundingMode.HALF_UP), summary.dividendTax());
        assertEquals(BigDecimal.valueOf(dividendScenario.expected().dividend().netCashIncrease()).setScale(8, RoundingMode.HALF_UP), summary.dividendsByCurrency().get(CurrencyType.USD));
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
        assertEquals(new BigDecimal("1000.00000000"), summary.deposits());
        assertEquals(new BigDecimal("0E-8"), summary.withdrawals());
    }

    @Test
    void aggregate_returnsZeroSummaryForEmptyInput() {
        CashFlowAggregator.CashFlowSummary summary = aggregator.aggregate(List.of(), CurrencyType.USD);
        assertEquals(new BigDecimal("0E-8"), summary.deposits());
        assertEquals(new BigDecimal("0E-8"), summary.dividends());
        assertEquals(new BigDecimal("0E-8"), summary.interest());
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

