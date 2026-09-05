package com.smartbox.investory.investment.ledger.cash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioBuilders;
import com.smartbox.investory.testsupport.portfolio.PortfolioScenarios;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestContext;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData;
import com.smartbox.investory.testsupport.time.MutableApplicationTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cash Flow Aggregator")
class CashFlowAggregatorTest {

  @Mock private CurrencyRateService currencyRateService;

  private CashFlowAggregator aggregator;

  @BeforeEach
  void setUp() {
    aggregator =
        new CashFlowAggregator(
            currencyRateService,
            MutableApplicationTime.fixed(
                Instant.parse("2026-09-05T08:00:00Z"), ZoneId.of("Europe/Warsaw")));
    // Identity FX so amounts pass through unchanged. lenient() because the empty-input test
    // never triggers conversion.
    org.mockito.Mockito.lenient()
        .when(
            currencyRateService.convertToBaseCurrency(
                any(BigDecimal.class), any(), any(), any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));
  }

  @DisplayName("aggregate sums Deposits Withdrawals Interest And Dividends")
  @Test
  void aggregate_sumsDepositsWithdrawalsInterestAndDividends() {
    PortfolioTestContext dividendScenario = PortfolioScenarios.createDividendScenario();
    CashFlowAggregator.CashFlowSummary summary =
        aggregator.aggregate(
            List.of(
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
                dividendScenario.operations().aaplWithholdingTax()),
            CurrencyType.USD);

    assertEquals(new BigDecimal("1000.00000000"), summary.deposits());
    assertEquals(new BigDecimal("-200.00000000"), summary.withdrawals());
    assertEquals(new BigDecimal("800.00000000"), summary.netDeposits());
    assertEquals(new BigDecimal("10.00000000"), summary.interest());
    assertEquals(
        BigDecimal.valueOf(dividendScenario.expected().dividend().netCashIncrease())
            .setScale(8, RoundingMode.HALF_UP),
        summary.dividends());
    assertEquals(
        BigDecimal.valueOf(-dividendScenario.expected().dividend().tax())
            .setScale(8, RoundingMode.HALF_UP),
        summary.dividendTax());
    assertEquals(
        BigDecimal.valueOf(dividendScenario.expected().dividend().netCashIncrease())
            .setScale(8, RoundingMode.HALF_UP),
        summary.dividendsByCurrency().get(CurrencyType.USD));
  }

  @DisplayName("aggregate excludes Internal Transfers And Currency Conversions")
  @Test
  void aggregate_excludesInternalTransfersAndCurrencyConversions() {
    PortfolioTestContext transferScenario = PortfolioScenarios.createInternalCashTransferScenario();
    CashFlowAggregator.CashFlowSummary summary =
        aggregator.aggregate(
            List.of(
                op(CashOperationType.DEPOSIT, 500.0, "Currency Conversion EUR -> USD"),
                op(CashOperationType.DEPOSIT, 100.0, "Transfer from sub-account"),
                op(CashOperationType.WITHDRAWAL, -50.0, "Transfer to sub-account"),
                transferScenario.operations().transferOut(),
                transferScenario.operations().transferIn(),
                PortfolioBuilders.cashOperation()
                    .deposit(1000.0, CurrencyType.USD)
                    .comment("Electronic Fund Transfer")
                    .build()),
            CurrencyType.USD);

    // Currency conversion + sub-account transfers excluded -> only the real wire counts.
    assertEquals(new BigDecimal("1000.00000000"), summary.deposits());
    assertEquals(new BigDecimal("0E-8"), summary.withdrawals());
  }

  @DisplayName("aggregate returns Zero Summary For Empty Input")
  @Test
  void aggregate_returnsZeroSummaryForEmptyInput() {
    CashFlowAggregator.CashFlowSummary summary = aggregator.aggregate(List.of(), CurrencyType.USD);
    assertEquals(new BigDecimal("0E-8"), summary.deposits());
    assertEquals(new BigDecimal("0E-8"), summary.dividends());
    assertEquals(new BigDecimal("0E-8"), summary.interest());
    assertEquals(0, summary.dividendsByCurrency().size());
  }

  private static CashOperationEntity op(CashOperationType type, double amount, String comment) {
    return PortfolioBuilders.cashOperation()
        .type(type)
        .deposit(amount, CurrencyType.USD)
        .type(type)
        .comment(comment)
        .on(PortfolioTestData.JANUARY_DEPOSIT_DATE)
        .build();
  }
}
