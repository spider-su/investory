package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.*;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformance;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformanceRepository;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioAssetAllocation;
import com.example.demo.infrastructure.repository.portfolio.PortfolioAssetAllocationRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioCurrencyBreakdown;
import com.example.demo.infrastructure.repository.portfolio.PortfolioCurrencyBreakdownRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioKpiSummary;
import com.example.demo.infrastructure.repository.portfolio.PortfolioKpiSummaryRepository;
import com.example.demo.infrastructure.repository.portfolio.SymbolPerformance;
import com.example.demo.infrastructure.repository.portfolio.SymbolPerformanceRepository;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.models.DividendGainer;
import com.example.demo.services.models.InstrumentPerformance;
import com.example.demo.services.models.Performance;
import com.example.demo.services.models.Portfolio;
import com.example.demo.testsupport.portfolio.PortfolioBuilders;
import com.example.demo.testsupport.portfolio.PortfolioTestData;
import com.example.demo.testsupport.portfolio.PortfolioTestData.AccountDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock private CurrencyRateService currencyRateService;
    @Mock private ClosedPositionRepository closedPositionRepository;
    @Mock private OpenedPositionRepository openedPositionRepository;
    @Mock private CashOperationRepository cashOperationRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountStatisticsRepository accountStatisticsRepository;
    @Mock private AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
    @Mock private PortfolioAssetAllocationRepository portfolioAssetAllocationRepository;
    @Mock private PortfolioCurrencyBreakdownRepository portfolioCurrencyBreakdownRepository;
    @Mock private PortfolioKpiSummaryRepository portfolioKpiSummaryRepository;
    @Mock private SymbolPerformanceRepository symbolPerformanceRepository;

    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        // Use real helpers so the test still exercises end-to-end behaviour after the extraction.
        TaxCalculator taxCalculator = new TaxCalculator(currencyRateService);
        CashFlowAggregator cashFlowAggregator = new CashFlowAggregator(currencyRateService);
        portfolioService = new PortfolioService(currencyRateService,
                closedPositionRepository, openedPositionRepository,
                cashOperationRepository, accountRepository,
                accountStatisticsRepository, accountMonthlyPerformanceRepository, portfolioAssetAllocationRepository,
                portfolioCurrencyBreakdownRepository, portfolioKpiSummaryRepository,
                symbolPerformanceRepository,
                taxCalculator, cashFlowAggregator);
        org.mockito.Mockito.lenient().when(portfolioKpiSummaryRepository.findAll()).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(portfolioAssetAllocationRepository.findAll()).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(portfolioCurrencyBreakdownRepository.findAll()).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(symbolPerformanceRepository.findAll()).thenReturn(List.of());
        // Identity FX so tests are arithmetic-only. lenient() because some tests don't trigger FX conversion.
        org.mockito.Mockito.lenient().when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
        org.mockito.Mockito.lenient()
                .when(currencyRateService.convertToBaseCurrency(
                        anyDouble(), any(CurrencyType.class), any(CurrencyType.class), any(LocalDate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
        org.mockito.Mockito.lenient()
                .when(currencyRateService.findRate(any(CurrencyType.class), any(CurrencyType.class)))
                .thenReturn(OptionalDouble.empty());
    }

    @Test
    void calculateTotalProfitLoss_usesPortfolioKpiSummaryForDashboardMetrics() {
        when(portfolioKpiSummaryRepository.findAll()).thenReturn(List.of(new PortfolioKpiSummary(
                1L,
                "Main",
                CurrencyType.USD,
                1000.0,
                800.0,
                125.0,
                975.0,
                1100.0,
                200.0,
                75.0,
                25.0,
                0.0,
                ZonedDateTime.now()
        )));
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(openedPositionRepository.findAll()).thenReturn(List.of(opened("AAPL.US", 50.0, -2.0, 0.0)));
        when(accountStatisticsRepository.findAll()).thenReturn(List.of(
                PortfolioBuilders.accountStatistics()
                        .account(PortfolioTestData.IBKR_USD)
                        .deposits(800.0, 0.0)
                        .balances(125.0, 975.0, 900.0)
                        .performance(200.0, 75.0, 25.0)
                        .build(),
                PortfolioBuilders.accountStatistics()
                        .account(new AccountDefinition(999L, "Empty", CurrencyType.PLN, "Broker"))
                        .balances(0.0, 0.0, 0.0)
                        .performance(0.0, 0.0, 0.0)
                        .build()));
        Account account = new Account();
        account.setId(17959259L);
        account.setName("IBKR");
        account.setCurrency(CurrencyType.PLN);
        Account emptyAccount = new Account();
        emptyAccount.setId(999L);
        emptyAccount.setName("Empty");
        emptyAccount.setCurrency(CurrencyType.PLN);
        when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(
                17959259L, account,
                999L, emptyAccount));
        when(portfolioAssetAllocationRepository.findAll()).thenReturn(List.of(
                new PortfolioAssetAllocation(1L, CurrencyType.USD, "AAPL.US", 7.0, 650.0, 700.0, 50.0, ZonedDateTime.now()),
                new PortfolioAssetAllocation(1L, CurrencyType.USD, "MSFT.US", 3.0, 290.0, 300.0, 10.0, ZonedDateTime.now())
        ));
        when(currencyRateService.convertToBaseCurrency(anyDouble(), any(CurrencyType.class), any(CurrencyType.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    double amount = invocation.getArgument(0);
                    CurrencyType target = invocation.getArgument(1);
                    CurrencyType source = invocation.getArgument(2);
                    if (target == CurrencyType.PLN && source == CurrencyType.USD) {
                        return amount * 4.0;
                    }
                    return amount;
                });

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertEquals(200.0, result.getRealizedProfit(), 0.01);
        assertEquals(75.0, result.getUnrealizedProfit(), 0.01);
        assertEquals(25.0, result.getDividends(), 0.01);
        assertEquals(300.0, result.getTotalProfit(), 0.01);
        assertEquals(125.0, result.getCash(), 0.01);
        assertEquals(1100.0, result.getBalance(), 0.01);
        assertEquals(1000.0, result.getDeposits(), 0.01);
        assertEquals(-200.0, result.getWithdrawals(), 0.01);
        assertEquals(800.0, result.getNetDeposits(), 0.01);
        assertEquals(37.5, result.getRoi(), 0.01);
        assertEquals(200.0, result.getRealizedByCurrency().get(CurrencyType.PLN), 0.01);
        assertEquals(0.0, result.getUnrealizedByCurrency().get(CurrencyType.USD), 0.01);
        assertEquals(75.0, result.getUnrealizedByCurrency().get(CurrencyType.PLN), 0.01);
        assertEquals(25.0, result.getDividendsByCurrency().get(CurrencyType.PLN), 0.01);
        assertEquals(1, result.getAccountBalances().size());
        assertEquals(17959259L, result.getAccountBalances().getFirst().getAccountId());
        assertEquals("IBKR", result.getAccountBalances().getFirst().getAccountName());
        assertEquals(800.0, result.getAccountBalances().getFirst().getNetDeposit(), 0.01);
        assertEquals(37.5, result.getAccountBalances().getFirst().getProfitLossPercent(), 0.01);
        assertEquals(1100.0, result.getAccountBalances().getFirst().getBalance(), 0.01);
        assertEquals(125.0, result.getAccountBalances().getFirst().getCash(), 0.01);
        assertEquals(CurrencyType.PLN, result.getAccountBalances().getFirst().getLocalCurrency());
        assertEquals(4400.0, result.getAccountBalances().getFirst().getLocalBalance(), 0.01);
        assertEquals(2, result.getOpenPositionValues().size());
        assertEquals("AAPL.US", result.getOpenPositionValues().getFirst().getSymbol());
        assertEquals(7.0, result.getOpenPositionValues().getFirst().getVolume(), 0.01);
        assertEquals(650.0, result.getOpenPositionValues().getFirst().getCostBase(), 0.01);
        assertEquals(92.857, result.getOpenPositionValues().getFirst().getAverageOpenPrice(), 0.001);
        assertEquals(700.0, result.getOpenPositionValues().getFirst().getValue(), 0.01);
        assertEquals(50.0, result.getOpenPositionValues().getFirst().getUnrealized(), 0.01);
        assertEquals(7.692, result.getOpenPositionValues().getFirst().getProfitLossPercent(), 0.001);
        assertEquals(70.0, result.getOpenPositionValues().getFirst().getSharePercent(), 0.01);
        assertTrue(result.getDividendGainers().isEmpty());
    }

    @Test
    void calculateTotalProfitLoss_keepsFundedZeroBalanceAccountsVisible() {
        Account account = new Account();
        account.setId(51747407L);
        account.setName("Trading EUR");
        account.setCurrency(CurrencyType.EUR);
        when(accountStatisticsRepository.findAll()).thenReturn(List.of(
                PortfolioBuilders.accountStatistics()
                        .account(new AccountDefinition(51747407L, "Trading EUR", CurrencyType.EUR, "Broker"))
                        .deposits(13370.17, 0.0)
                        .balances(0.0, 0.0, 0.0)
                        .build()));
        when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(51747407L, account));
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        when(openedPositionRepository.findAll()).thenReturn(List.of());
        when(cashOperationRepository.findAll()).thenReturn(List.of());

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertEquals(1, result.getAccountBalances().size());
        assertEquals(51747407L, result.getAccountBalances().getFirst().getAccountId());
        assertEquals(13370.17, result.getAccountBalances().getFirst().getNetDeposit(), 0.01);
        assertEquals(-100.0, result.getAccountBalances().getFirst().getProfitLossPercent(), 0.01);
    }

    @Test
    void calculateTotalProfitLoss_usesNonThrowingFxLookupForExchangeRateBoard() {
        when(portfolioKpiSummaryRepository.findAll()).thenReturn(List.of(new PortfolioKpiSummary(
                1L,
                "Main",
                CurrencyType.USD,
                1000.0,
                800.0,
                125.0,
                975.0,
                1100.0,
                200.0,
                75.0,
                25.0,
                0.0,
                ZonedDateTime.now()
        )));
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        when(openedPositionRepository.findAll()).thenReturn(List.of());
        when(currencyRateService.findRate(CurrencyType.USD, CurrencyType.EUR))
                .thenReturn(OptionalDouble.empty());
        when(currencyRateService.findRate(CurrencyType.USD, CurrencyType.PLN))
                .thenReturn(OptionalDouble.of(3.75));

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertFalse(result.getExchangeRates().containsKey(CurrencyType.EUR));
        assertEquals(3.75, result.getExchangeRates().get(CurrencyType.PLN), 0.01);
        verify(currencyRateService, never()).getRate(any(CurrencyType.class), any(CurrencyType.class));
    }

    @Test
    void calculateTotalProfitLoss_populatesDividendTaxWhenKpiSummaryIsUsed() {
        when(portfolioKpiSummaryRepository.findAll()).thenReturn(List.of(new PortfolioKpiSummary(
                1L,
                "Main",
                CurrencyType.USD,
                1000.0,
                800.0,
                125.0,
                975.0,
                1100.0,
                200.0,
                75.0,
                4912.0,
                0.0,
                ZonedDateTime.now()
        )));
        when(cashOperationRepository.findAll()).thenReturn(List.of(
                dividend("AAPL.US", 100.0, CurrencyType.USD),
                dividendTax("AAPL.US", -15.0, CurrencyType.USD)));
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        when(openedPositionRepository.findAll()).thenReturn(List.of());
        when(accountStatisticsRepository.findAll()).thenReturn(List.of());

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertEquals(4912.0, result.getDividends(), 0.01);
        assertEquals(-15.0, result.getDividendTax(), 0.01);
    }

    @Test
    void calculateTotalProfitLoss_populatesDividendGainersSortedByConvertedUsdWithOtherBucket() {
        when(symbolPerformanceRepository.findAll()).thenReturn(List.of(
                new SymbolPerformance("PZU.PL", 0.0, 0.0, 0.0, 300.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("AAPL.US", 0.0, 0.0, 0.0, 200.0, 50.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_1", 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_2", 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_3", 0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_4", 0.0, 0.0, 0.0, 4.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_5", 0.0, 0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_6", 0.0, 0.0, 0.0, 6.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_7", 0.0, 0.0, 0.0, 7.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_8", 0.0, 0.0, 0.0, 8.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_9", 0.0, 0.0, 0.0, 9.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("SMALL_10", 0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now())
        ));
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        when(openedPositionRepository.findAll()).thenReturn(List.of());
        when(accountStatisticsRepository.findAll()).thenReturn(List.of());

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        List<DividendGainer> gainers = result.getDividendGainers();
        assertEquals(10, gainers.size());
        assertEquals("PZU.PL", gainers.getFirst().getSymbol());
        assertEquals(300.0, gainers.getFirst().getDividends(), 0.01);
        assertEquals("AAPL.US", gainers.get(1).getSymbol());
        assertEquals(200.0, gainers.get(1).getDividends(), 0.01);
        assertEquals("SMALL_10", gainers.get(2).getSymbol());
        assertEquals("SMALL_4", gainers.get(8).getSymbol());
        assertEquals("Other", gainers.get(9).getSymbol());
        assertEquals(6.0, gainers.get(9).getDividends(), 0.01);
    }

    @Test
    void calculateTotalProfitLoss_keepsKpiUnrealizedWhenOpenPositionRowsDiffer() {
        when(portfolioKpiSummaryRepository.findAll()).thenReturn(List.of(new PortfolioKpiSummary(
                1L,
                "Main",
                CurrencyType.USD,
                122336.80297403,
                100967.65298977,
                17723.60998057,
                90369.7900344,
                108093.40001497,
                6417.90706124,
                6575.81104821,
                2955.45329345,
                0.0,
                ZonedDateTime.now()
        )));
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        when(openedPositionRepository.findAll()).thenReturn(List.of(opened("VWRA", -250.0, -1.0, 0.0)));

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertEquals(6575.81104821, result.getUnrealizedProfit(), 0.01);
        assertEquals(7125.747025200006, result.getTotalProfit(), 0.01);
    }

    @Test
    void calculateTotalProfitLoss_usesCurrencyBreakdownProjectionForDetailRowsOnlyWhenKpiExists() {
        when(portfolioKpiSummaryRepository.findAll()).thenReturn(List.of(new PortfolioKpiSummary(
                1L,
                "Main",
                CurrencyType.USD,
                1000.0,
                800.0,
                100.0,
                700.0,
                800.0,
                999.0,
                999.0,
                999.0,
                0.0,
                ZonedDateTime.now()
        )));
        when(portfolioCurrencyBreakdownRepository.findAll()).thenReturn(List.of(
                new PortfolioCurrencyBreakdown(1L, CurrencyType.USD, "REALIZED", CurrencyType.USD, 100.0, 100.0, ZonedDateTime.now()),
                new PortfolioCurrencyBreakdown(1L, CurrencyType.USD, "UNREALIZED", CurrencyType.USD, -250.0, -250.0, ZonedDateTime.now()),
                new PortfolioCurrencyBreakdown(1L, CurrencyType.USD, "DIVIDENDS", CurrencyType.PLN, 400.0, 100.0, ZonedDateTime.now())
        ));
        when(closedPositionRepository.findAll()).thenReturn(List.of());

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertEquals(999.0, result.getRealizedProfit(), 0.01);
        assertEquals(999.0, result.getUnrealizedProfit(), 0.01);
        assertEquals(999.0, result.getDividends(), 0.01);
        assertEquals(0.0, result.getTotalProfit(), 0.01);
        assertEquals(100.0, result.getRealizedByCurrency().get(CurrencyType.USD), 0.01);
        assertEquals(-250.0, result.getUnrealizedByCurrency().get(CurrencyType.USD), 0.01);
        assertEquals(400.0, result.getDividendsByCurrency().get(CurrencyType.PLN), 0.01);
    }

    @Test
    void calculateTotalProfitLoss_aggregatesRealizedAndUnrealizedAndDividends() {
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("AAPL.US", 100.0, -1.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR))
        ));
        when(openedPositionRepository.findAll()).thenReturn(List.of(
                opened("MSFT.US", 50.0, 0.0, 0.0)
        ));
        when(cashOperationRepository.findAll()).thenReturn(List.of(
                cash(CashOperationType.DIVIDEND, 25.0, null),
                cash(CashOperationType.DEPOSIT, 1000.0, "wire transfer"),
                cash(CashOperationType.WITHDRAWAL, -200.0, "wire transfer")
        ));
        when(accountStatisticsRepository.findAll()).thenReturn(List.of(
                PortfolioBuilders.accountStatistics()
                        .account(new AccountDefinition(1L, "Main", CurrencyType.USD, "Broker"))
                        .deposits(1000.0, -200.0)
                        .balances(0.0, 5000.0, 4950.0)
                        .performance(99.0, 50.0, 25.0)
                        .build()));
        Account account = new Account();
        account.setId(1L);
        account.setCurrency(CurrencyType.USD);
        account.setName("Main");
        when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(1L, account));

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertEquals(99.0, result.getRealizedProfit(), 0.01);     // 100 - 1
        assertEquals(50.0, result.getUnrealizedProfit(), 0.01);
        assertEquals(25.0, result.getDividends(), 0.01);
        assertEquals(4200.0, result.getTotalProfit(), 0.01);
        assertEquals(1000.0, result.getDeposits(), 0.01);
        assertEquals(-200.0, result.getWithdrawals(), 0.01);
        assertEquals(800.0, result.getNetDeposits(), 0.01);
        assertEquals(5000.0, result.getBalance(), 0.01);
    }

    @Test
    void calculateTotalProfitLoss_excludesCurrencyConversionDeposits() {
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        when(openedPositionRepository.findAll()).thenReturn(List.of());
        when(cashOperationRepository.findAll()).thenReturn(List.of(
                cash(CashOperationType.DEPOSIT, 500.0, "Currency Conversion EUR -> USD"),
                cash(CashOperationType.DEPOSIT, 1000.0, "Bank deposit")
        ));

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        // Currency-conversion row excluded -> only the 1000 USD deposit counts.
        assertEquals(1000.0, result.getDeposits(), 0.01);
    }

    @Test
    void calculateTotalProfitLoss_appliesCapitalGainsTaxOnCurrentYearGains() {
        int year = java.time.Year.now().getValue();
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("AAPL.US", 1000.0, 0.0, 0.0, PortfolioTestData.atNoon(LocalDate.of(year, 6, 30)))
        ));
        when(openedPositionRepository.findAll()).thenReturn(List.of());
        when(cashOperationRepository.findAll()).thenReturn(List.of());

        Portfolio result = portfolioService.calculateTotalProfitLoss();

        assertEquals(190.0, result.getCapitalGainsTax(), 0.01);     // 19% of 1000
        assertEquals(0.0, result.getLossCarryForward(), 0.01);
    }

    @Test
    void calculateWinRate_returnsZeroWhenNoTrades() {
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        assertEquals(0.0, portfolioService.calculateWinRate());
    }

    @Test
    void calculateWinRate_countsOnlyProfitableTrades() {
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("A", 10.0, 0.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("B", -5.0, 0.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("C", 20.0, 0.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("D", -2.0, 0.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR))
        ));

        assertEquals(50.0, portfolioService.calculateWinRate());
    }

    @Test
    void calculateLargestWinLoss_returnsMaxAndMinProfit() {
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("A", 100.0, 0.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("B", -50.0, 0.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("C", 25.0, 0.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR))
        ));

        Map<String, Double> result = portfolioService.calculateLargestWinLoss();
        assertEquals(100.0, result.get("largestWin"));
        assertEquals(-50.0, result.get("largestLoss"));
    }

    @Test
    void calculatePerformancePerInstrument_includesBothOpenAndClosedPositions() {
        when(symbolPerformanceRepository.findAll()).thenReturn(List.of(
                new SymbolPerformance("AAPL.US", 100.0, 50.0, 150.0, 0.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance("MSFT.US", 0.0, 30.0, 30.0, 0.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now())
        ));

        List<InstrumentPerformance> performance = portfolioService.calculatePerformancePerInstrument(CurrencyType.USD);

        assertEquals(2, performance.size());
        InstrumentPerformance aapl = performance.stream()
                .filter(p -> "AAPL.US".equals(p.getSymbol())).findFirst().orElseThrow();
        assertEquals(100.0, aapl.getClosedProfit(), 0.01);
        assertEquals(50.0, aapl.getUnrealizedProfit(), 0.01);
        assertEquals(150.0, aapl.getTotal(), 0.01);
    }

    @Test
    void calculateMonthlyPerformance_bucketsByYearAndMonth() {
        int year = java.time.Year.now().getValue();
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc()).thenReturn(List.of(
                monthlyPerformance(1L, LocalDate.of(year, 3, 1), 100.0, 0.0, 1100.0),
                monthlyPerformance(1L, LocalDate.of(year - 1, 7, 1), 50.0, 25.0, 1050.0)
        ));

        Performance perf = portfolioService.calculateMonthlyPerformance();
        Map<String, Double> monthly = perf.getCalculateMonthlyPerformance();
        // Past year is bucketed by year only; current year by year-month.
        assertTrue(monthly.containsKey(String.valueOf(year - 1)));
        assertTrue(monthly.keySet().stream().anyMatch(k -> k.startsWith(year + "-")));
        assertEquals(25.0, perf.getMonthlyCashflow().get(String.valueOf(year - 1)), 0.01);
    }

    @Test
    void calculateCashFlowOverTime_groupsByCloseTime() {
        ZonedDateTime when = PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR);
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed("A", 10.0, 0.0, 0.0, when),
                closed("B", 5.0, 0.0, 0.0, when)
        ));

        Map<String, Double> cashFlow = portfolioService.calculateCashFlowOverTime(CurrencyType.USD);
        assertEquals(1, cashFlow.size());
        assertEquals(15.0, cashFlow.values().iterator().next());
    }

    private static ClosedPosition closed(String symbol, double profit, double commission, double swap, ZonedDateTime closeTime) {
        ClosedPosition cp = PortfolioBuilders.closedPosition(PortfolioTestData.AAPL)
                .symbol(symbol)
                .currency(CurrencyType.USD)
                .profit(profit)
                .commission(commission)
                .swap(swap)
                .closeOn(closeTime.toLocalDate())
                .build();
        cp.setCloseTime(closeTime);
        cp.setVolume(1.0);
        cp.setOpenPrice(100.0);
        cp.setClosePrice(100.0 + profit);
        return cp;
    }

    private static OpenedPosition opened(String symbol, double profit, double commission, double swap) {
        return PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
                .symbol(symbol)
                .currency(CurrencyType.USD)
                .quantity(1.0)
                .price(100.0)
                .marketPrice(100.0 + profit)
                .commission(commission)
                .swap(swap)
                .build();
    }

    private static AccountMonthlyPerformance monthlyPerformance(
            Long accountId, LocalDate month, double profit, double netCashflow, double endEquity) {
        return new AccountMonthlyPerformance(
                accountId + ":" + month,
                accountId,
                month,
                month.withDayOfMonth(month.lengthOfMonth()),
                endEquity - profit - netCashflow,
                endEquity,
                Math.max(netCashflow, 0.0),
                Math.min(netCashflow, 0.0),
                netCashflow,
                profit,
                0.0,
                ZonedDateTime.now());
    }

    private static CashOperation cash(CashOperationType type, double amount, String comment) {
        return PortfolioBuilders.cashOperation()
                .type(type)
                .deposit(amount, CurrencyType.USD)
                .type(type)
                .comment(comment)
                .on(PortfolioTestData.MID_YEAR)
                .build();
    }

    private static CashOperation dividend(String symbol, double amount, CurrencyType currency) {
        CashOperation c = cash(CashOperationType.DIVIDEND, amount, null);
        c.setSymbol(symbol);
        c.setCurrency(currency);
        return c;
    }

    private static CashOperation dividendTax(String symbol, double amount, CurrencyType currency) {
        CashOperation c = cash(CashOperationType.WITHHOLDING_TAX, amount, null);
        c.setSymbol(symbol);
        c.setCurrency(currency);
        return c;
    }

}
