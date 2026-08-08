package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.*;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformance;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformanceRepository;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.infrastructure.repository.account.AccountStatistics;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioAssetAllocationRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioCurrencyBreakdown;
import com.example.demo.infrastructure.repository.portfolio.PortfolioCurrencyBreakdownRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioDataQualityRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioFallbackReconciliationRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioKpiSummary;
import com.example.demo.infrastructure.repository.portfolio.PortfolioKpiSummaryRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioMonthlyPerformance;
import com.example.demo.infrastructure.repository.portfolio.PortfolioMonthlyPerformanceRepository;
import com.example.demo.infrastructure.repository.portfolio.SymbolPerformance;
import com.example.demo.infrastructure.repository.portfolio.SymbolPerformanceRepository;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.models.DividendGainer;
import com.example.demo.services.models.InstrumentPerformance;
import com.example.demo.services.models.Performance;
import com.example.demo.services.models.Portfolio;
import com.example.demo.services.models.PortfolioDataQualityIssue;
import com.example.demo.testsupport.portfolio.PortfolioBuilders;
import com.example.demo.testsupport.portfolio.PortfolioTestData;
import com.example.demo.testsupport.portfolio.PortfolioTestData.AccountDefinition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

  @Mock private CurrencyRateService currencyRateService;
  @Mock private ClosedPositionRepository closedPositionRepository;
  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private CashOperationRepository cashOperationRepository;
  @Mock private NormalizedCashOperationRepository normalizedCashOperationRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private AccountStatisticsRepository accountStatisticsRepository;
  @Mock private AccountDailyRepository accountDailyRepository;
  @Mock private AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
  @Mock private PortfolioAssetAllocationRepository portfolioAssetAllocationRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private PortfolioCurrencyBreakdownRepository portfolioCurrencyBreakdownRepository;
  @Mock private PortfolioKpiSummaryRepository portfolioKpiSummaryRepository;
  @Mock private PortfolioMonthlyPerformanceRepository portfolioMonthlyPerformanceRepository;
  @Mock private SymbolPerformanceRepository symbolPerformanceRepository;
  @Mock private PortfolioFallbackReconciliationRepository fallbackReconciliationRepository;
  @Mock private PortfolioDataQualityRepository dataQualityRepository;

  private PortfolioService portfolioService;
  private PortfolioProperties portfolioProperties;

  @BeforeEach
  void setUp() {
    // Use real helpers so the test still exercises end-to-end behaviour after the extraction.
    TaxCalculator taxCalculator = new TaxCalculator(currencyRateService);
    CashFlowAggregator cashFlowAggregator = new CashFlowAggregator(currencyRateService);
    portfolioProperties = new PortfolioProperties();
    portfolioProperties.setDataQualityIssuesEnabled(false);
    portfolioService =
        new PortfolioService(
            currencyRateService,
            closedPositionRepository,
            openedPositionRepository,
            cashOperationRepository,
            normalizedCashOperationRepository,
            accountRepository,
            accountStatisticsRepository,
            accountDailyRepository,
            accountMonthlyPerformanceRepository,
            portfolioAssetAllocationRepository,
            assetRepository,
            portfolioCurrencyBreakdownRepository,
            portfolioKpiSummaryRepository,
            portfolioMonthlyPerformanceRepository,
            fallbackReconciliationRepository,
            dataQualityRepository,
            symbolPerformanceRepository,
            taxCalculator,
            cashFlowAggregator,
            new CashOperationNormalizer(),
            portfolioProperties);
    org.mockito.Mockito.lenient()
        .when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(portfolioAssetAllocationRepository.findAll())
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(portfolioCurrencyBreakdownRepository.findAll())
        .thenReturn(List.of());
    org.mockito.Mockito.lenient().when(symbolPerformanceRepository.findAll()).thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(portfolioMonthlyPerformanceRepository.findAllByOrderByMonthAscPortfolioIdAsc())
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(normalizedCashOperationRepository.findAllByAccountIdIn(any()))
        .thenReturn(List.of());
    // Identity FX so tests are arithmetic-only. lenient() because some tests don't trigger FX
    // conversion.
    org.mockito.Mockito.lenient()
        .when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
    org.mockito.Mockito.lenient()
        .when(
            currencyRateService.convertToBaseCurrency(
                anyDouble(),
                any(CurrencyType.class),
                any(CurrencyType.class),
                any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
    org.mockito.Mockito.lenient()
        .when(currencyRateService.convertToBaseCurrency(any(BigDecimal.class), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));
    org.mockito.Mockito.lenient()
        .when(
            currencyRateService.convertToBaseCurrency(
                any(BigDecimal.class),
                any(CurrencyType.class),
                any(CurrencyType.class),
                any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));
    org.mockito.Mockito.lenient()
        .when(currencyRateService.findRate(any(CurrencyType.class), any(CurrencyType.class)))
        .thenReturn(OptionalDouble.empty());
  }

  @Test
  @Disabled
  void calculateTotalProfitLoss_loadsDetailedDataQualityIssuesWhenEnabled() {
    portfolioProperties.setDataQualityIssuesEnabled(true);
    when(dataQualityRepository.findSnapshot()).thenReturn(List.<Object[]>of(dataQualitySnapshot()));
    when(dataQualityRepository.findIssues())
        .thenReturn(
            List.<Object[]>of(
                new Object[] {
                  "PRICE",
                  51499241L,
                  12501L,
                  "STALE_PRICE",
                  12,
                  java.sql.Date.valueOf("2026-07-20"),
                  "USD",
                  "STALE_CARRY_FORWARD",
                  "HISTORICAL",
                  "WARN",
                  77L
                }));

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(1, result.getDataQuality().issues().size());
    PortfolioDataQualityIssue issue = result.getDataQuality().issues().getFirst();
    assertEquals("51499241", issue.accountId());
    assertEquals("12501", issue.assetId());
    assertEquals("STALE_PRICE", issue.code());
    assertEquals(LocalDate.of(2026, 7, 20), issue.priceDate());
    assertEquals(77L, issue.priceHistoryId());
    verify(dataQualityRepository).findIssues();
  }

  @Test
  void calculateTotalProfitLoss_usesPortfolioKpiSummaryForDashboardMetrics() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummary(
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
                    ZonedDateTime.now())));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(openedPositionRepository.findAll())
        .thenReturn(List.of(opened("AAPL.US", 50.0, -2.0)));
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
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
    when(accountRepository.findMapByIdIn(any()))
        .thenReturn(
            Map.of(
                17959259L, account,
                999L, emptyAccount));
    when(currencyRateService.findRate(
            any(CurrencyType.class), any(CurrencyType.class), any(LocalDate.class)))
        .thenReturn(OptionalDouble.of(4.0));

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(200.0, result.getRealizedProfit(), 0.01);
    assertEquals(75.0, result.getUnrealizedProfit(), 0.01);
    assertEquals(25.0, result.getDividends(), 0.01);
    assertEquals(300.0, result.getTotalProfit(), 0.01);
    assertEquals(125.0, result.getCash(), 0.01);
    assertEquals(1100.0, result.getBalance(), 0.01);
    assertEquals(1000.0, result.getDeposits(), 0.01);
    assertEquals(200.0, result.getWithdrawals(), 0.01);
    assertEquals(800.0, result.getNetDeposits(), 0.01);
    assertEquals(37.5, result.getRoi(), 0.01);
    assertEquals(200.0, result.getRealizedByCurrency().get(CurrencyType.USD), 0.01);
    assertEquals(75.0, result.getUnrealizedByCurrency().get(CurrencyType.USD), 0.01);
    assertEquals(25.0, result.getDividendsByCurrency().get(CurrencyType.USD), 0.01);
    assertEquals(1, result.getAccountBalances().size());
    assertEquals(17959259L, result.getAccountBalances().getFirst().getAccountId());
    assertEquals("IBKR", result.getAccountBalances().getFirst().getAccountName());
    assertEquals(800.0, result.getAccountBalances().getFirst().getNetDeposit(), 0.01);
    assertEquals(37.5, result.getAccountBalances().getFirst().getProfitLossPercent(), 0.01);
    assertEquals(1100.0, result.getAccountBalances().getFirst().getBalance(), 0.01);
    assertEquals(125.0, result.getAccountBalances().getFirst().getCash(), 0.01);
    assertEquals(CurrencyType.PLN, result.getAccountBalances().getFirst().getLocalCurrency());
    assertEquals(4400.0, result.getAccountBalances().getFirst().getLocalBalance(), 0.01);
    assertTrue(result.getOpenPositionValues().isEmpty());
    assertTrue(result.getDividendGainers().isEmpty());
  }

  @Test
  void calculateTotalProfitLoss_keepsFundedZeroBalanceAccountsVisible() {
    Account account = new Account();
    account.setId(51747407L);
    account.setName("Trading EUR");
    account.setCurrency(CurrencyType.EUR);
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.accountStatistics()
                    .account(
                        new AccountDefinition(51747407L, "Trading EUR", CurrencyType.EUR, "Broker"))
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
    assertEquals(0.0, result.getAccountBalances().getFirst().getProfitLossPercent(), 0.01);
  }

  @Test
  void calculateTotalProfitLoss_usesTransferAwareNormalizedNetDepositsForAccountCards() {
    Account account = new Account();
    account.setId(51747407L);
    account.setName("Trading EUR");
    account.setCurrency(CurrencyType.EUR);
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.accountStatistics()
                    .account(
                        new AccountDefinition(51747407L, "Trading EUR", CurrencyType.EUR, "Broker"))
                    .deposits(13370.17, 0.0)
                    .netDeposits(13370.17, 13370.17)
                    .balances(0.0, 0.0, 0.0)
                    .build()));
    when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(51747407L, account));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(normalizedCashOperationRepository.findAllByAccountIdIn(any()))
        .thenReturn(
            List.of(
                normalizedCashOperationRow("EXTERNAL_DEPOSIT", 13370.17, 13370.17),
                normalizedCashOperationRow("INTERNAL_TRANSFER_OUT", -13358.49, -13358.49)));

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(1, result.getAccountBalances().size());
    assertEquals(11.68, result.getAccountBalances().getFirst().getNetDeposit(), 0.01);
    assertEquals(11.68, result.getAccountBalances().getFirst().getBaseNetDeposit(), 0.01);
  }

  @Test
  void calculateTotalProfitLoss_usesBaseBalanceForBaseCurrencyAccountProfitLoss() {
    Account account = new Account();
    account.setId(51499241L);
    account.setName("Trading USD");
    account.setCurrency(CurrencyType.USD);
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.accountStatistics()
                    .account(
                        new AccountDefinition(51499241L, "Trading USD", CurrencyType.USD, "Broker"))
                    .deposits(0.0, 0.0)
                    .balances(69.0, 22035.0, 22104.0)
                    .netDeposits(21671.38, 21671.38)
                    .build()));
    when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(51499241L, account));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(1, result.getAccountBalances().size());
    assertEquals(51499241L, result.getAccountBalances().getFirst().getAccountId());
    assertEquals(22104.0, result.getAccountBalances().getFirst().getLocalBalance(), 0.01);
    assertEquals(1.9963, result.getAccountBalances().getFirst().getProfitLossPercent(), 0.001);
  }

  @Test
  void xtbUsdSubaccountTransferAdjustsAccountBasisNotPortfolioBasis() {
    Account account = new Account();
    account.setId(51499241L);
    account.setName("XTB USD");
    account.setProvider("XTB");
    account.setCurrency(CurrencyType.USD);
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummary(
                    1L,
                    "Portfolio",
                    CurrencyType.USD,
                    21670.90,
                    21670.90,
                    30000.0,
                    0.0,
                    30000.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    ZonedDateTime.now())));
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.accountStatistics()
                    .account(new AccountDefinition(51499241L, "XTB USD", CurrencyType.USD, "XTB"))
                    .netDeposits(21670.90, 21670.90)
                    .balances(0.0, 30000.0, 30000.0)
                    .build()));
    when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(51499241L, account));
    when(normalizedCashOperationRepository.findAllByAccountIdIn(any()))
        .thenReturn(
            List.of(
                normalizedCashOperationRow(
                    51499241L, "EXTERNAL_DEPOSIT", 21670.90, 21670.90)));
    when(cashOperationRepository.findAllByAccountIn(any()))
        .thenReturn(
            List.of(
                subaccountTransfer(1L, -801.47, "Transfer from 51993106 to 51499241"),
                subaccountTransfer(2L, 801.47, "Transfer from 51993106 to 51499241")));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(22472.37, result.getAccountBalances().getFirst().getNetDeposit(), 0.01);
    assertEquals(7527.63, result.getAccountBalances().getFirst().getProfit(), 0.01);
    assertEquals(21670.90, result.getNetDeposits(), 0.01);
    assertEquals(21670.90, result.getAccountBalancesTotal().getNetDeposit(), 0.01);
    assertEquals(0.0, result.getAccountBalancesTotal().getProfit(), 0.01);
  }

  @Test
  void calculateTotalProfitLoss_usesNonThrowingFxLookupForExchangeRateBoard() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummary(
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
                    ZonedDateTime.now())));
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

  private static NormalizedCashOperationRepository.NormalizedCashOperationRow
      normalizedCashOperationRow(String category, double amount, double amountInBaseCurrency) {
    return normalizedCashOperationRow(51747407L, category, amount, amountInBaseCurrency);
  }

  private static NormalizedCashOperationRepository.NormalizedCashOperationRow
      normalizedCashOperationRow(
          Long accountId, String category, double amount, double amountInBaseCurrency) {
    return new NormalizedCashOperationRepository.NormalizedCashOperationRow() {
      @Override
      public Long getOperationId() {
        return null;
      }

      @Override
      public Long getAccountId() {
        return accountId;
      }

      @Override
      public String getAccountCurrency() {
        return null;
      }

      @Override
      public String getCurrency() {
        return null;
      }

      @Override
      public String getBaseCurrency() {
        return null;
      }

      @Override
      public String getRawOperation() {
        return null;
      }

      @Override
      public String getNormalizedCategory() {
        return category;
      }

      @Override
      public String getSymbol() {
        return null;
      }

      @Override
      public Double getAmount() {
        return amount;
      }

      @Override
      public Double getAmountInPortfolioBaseCurrency() {
        return amountInBaseCurrency;
      }

      @Override
      public String getPortfolioConversionStatus() {
        return "OK";
      }

      @Override
      public Double getAmountInAccountCurrency() {
        return amountInBaseCurrency;
      }

      @Override
      public String getAccountConversionStatus() {
        return "OK";
      }

      @Override
      public String getComment() {
        return null;
      }

      @Override
      public LocalDate getDate() {
        return null;
      }

      @Override
      public LocalDate getRateMonth() {
        return null;
      }

      @Override
      public Double getFxRateToBase() {
        return 1.0;
      }
    };
  }

  private static Object[] dataQualitySnapshot() {
    return new Object[] {
      "HEALTHY",
      1L,
      1L,
      1L,
      1L,
      0L,
      0L,
      0L,
      0L,
      0L,
      0L,
      0L,
      null,
      null,
      java.sql.Date.valueOf("2026-08-01"),
      java.sql.Date.valueOf("2026-08-01"),
      null
    };
  }

  @Test
  void calculateTotalProfitLoss_populatesDividendTaxWhenKpiSummaryIsUsed() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummary(
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
                    ZonedDateTime.now())));
    when(cashOperationRepository.findAll()).thenReturn(List.of(dividend(), dividendTax()));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(accountStatisticsRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(4912.0, result.getDividends(), 0.01);
    assertEquals(-15.0, result.getDividendTax(), 0.01);
  }

  @Test
  void calculateTotalProfitLoss_populatesDividendGainersSortedByConvertedUsdWithOtherBucket() {
    when(symbolPerformanceRepository.findAll())
        .thenReturn(
            List.of(
                new SymbolPerformance(
                    "PZU.PL", 0.0, 0.0, 0.0, 300.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "AAPL.US", 0.0, 0.0, 0.0, 200.0, 50.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_1", 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_2", 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_3", 0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_4", 0.0, 0.0, 0.0, 4.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_5", 0.0, 0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_6", 0.0, 0.0, 0.0, 6.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_7", 0.0, 0.0, 0.0, 7.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_8", 0.0, 0.0, 0.0, 8.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_9", 0.0, 0.0, 0.0, 9.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "SMALL_10", 0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now())));
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
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummary(
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
                    ZonedDateTime.now())));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(openedPositionRepository.findAll()).thenReturn(List.of(opened("VWRA", -250.0, -1.0)));

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(6575.81104821, result.getUnrealizedProfit(), 0.01);
    assertEquals(15949.1714029, result.getTotalProfit(), 0.01);
  }

  @Test
  void calculateTotalProfitLoss_usesCurrencyBreakdownProjectionForDetailRowsOnlyWhenKpiExists() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummary(
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
                    ZonedDateTime.now())));
    when(portfolioCurrencyBreakdownRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioCurrencyBreakdown(
                    1L,
                    CurrencyType.USD,
                    "REALIZED",
                    CurrencyType.USD,
                    100.0,
                    100.0,
                    ZonedDateTime.now()),
                new PortfolioCurrencyBreakdown(
                    1L,
                    CurrencyType.USD,
                    "UNREALIZED",
                    CurrencyType.USD,
                    -250.0,
                    -250.0,
                    ZonedDateTime.now()),
                new PortfolioCurrencyBreakdown(
                    1L,
                    CurrencyType.USD,
                    "DIVIDENDS",
                    CurrencyType.PLN,
                    400.0,
                    100.0,
                    ZonedDateTime.now())));
    when(closedPositionRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(999.0, result.getRealizedProfit(), 0.01);
    assertEquals(999.0, result.getUnrealizedProfit(), 0.01);
    assertEquals(999.0, result.getDividends(), 0.01);
    assertEquals(2997.0, result.getTotalProfit(), 0.01);
    assertEquals(100.0, result.getRealizedByCurrency().get(CurrencyType.USD), 0.01);
    assertEquals(-250.0, result.getUnrealizedByCurrency().get(CurrencyType.USD), 0.01);
    assertEquals(400.0, result.getDividendsByCurrency().get(CurrencyType.PLN), 0.01);
  }

  @Test
  void calculateTotalProfitLoss_preservesConfiguredPortfolioBaseCurrency() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummary(
                    2L,
                    "EUR portfolio",
                    CurrencyType.EUR,
                    1000.0,
                    800.0,
                    100.0,
                    700.0,
                    800.0,
                    40.0,
                    60.0,
                    20.0,
                    0.0,
                    ZonedDateTime.now())));
    when(closedPositionRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(CurrencyType.EUR, result.getBaseCurrency());
    assertEquals(CurrencyType.EUR, result.getAccountBalancesTotal().getLocalCurrency());
    assertEquals(CurrencyType.EUR, result.getOpenPositionValuesTotal().getCurrency());
  }

  @Test
  void calculateTotalProfitLoss_fallsBackWhenCurrencyBreakdownContainsUnsupportedMetricType() {
    when(portfolioCurrencyBreakdownRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioCurrencyBreakdown(
                    1L,
                    CurrencyType.USD,
                    "ACCOUNT_LATEST",
                    CurrencyType.USD,
                    500.0,
                    500.0,
                    ZonedDateTime.now())));
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.accountStatistics()
                    .account(new AccountDefinition(1L, "Main", CurrencyType.USD, "Broker"))
                    .performance(10.0, 20.0, 30.0)
                    .build()));
    Account account = new Account();
    account.setId(1L);
    account.setCurrency(CurrencyType.USD);
    when(accountRepository.findAll()).thenReturn(List.of(account));
    when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(1L, account));
    when(closedPositionRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(10.0, result.getRealizedProfit(), 0.01);
    assertEquals(20.0, result.getUnrealizedProfit(), 0.01);
    assertEquals(30.0, result.getDividends(), 0.01);
    assertEquals(60.0, result.getTotalProfit(), 0.01);
    assertEquals(10.0, result.getRealizedByCurrency().get(CurrencyType.USD), 0.01);
    assertEquals(20.0, result.getUnrealizedByCurrency().get(CurrencyType.USD), 0.01);
    assertEquals(30.0, result.getDividendsByCurrency().get(CurrencyType.USD), 0.01);
  }

  @Test
  void calculateTotalProfitLoss_aggregatesRealizedAndUnrealizedAndDividends() {
    when(closedPositionRepository.findAll())
        .thenReturn(
            List.of(
                closed(
                    "AAPL.US", 100.0, -1.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR))));
    when(openedPositionRepository.findAll()).thenReturn(List.of(opened("MSFT.US", 50.0, 0.0)));
    when(cashOperationRepository.findAll())
        .thenReturn(
            List.of(
                cash(CashOperationType.DIVIDEND, 25.0, null),
                cash(CashOperationType.DEPOSIT, 1000.0, "wire transfer"),
                cash(CashOperationType.WITHDRAWAL, -200.0, "wire transfer")));
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
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

    assertEquals(99.0, result.getRealizedProfit(), 0.01); // 100 - 1
    assertEquals(50.0, result.getUnrealizedProfit(), 0.01);
    assertEquals(25.0, result.getDividends(), 0.01);
    assertEquals(174.0, result.getTotalProfit(), 0.01);
    assertEquals(1000.0, result.getDeposits(), 0.01);
    assertEquals(-200.0, result.getWithdrawals(), 0.01);
    assertEquals(800.0, result.getNetDeposits(), 0.01);
    assertEquals(5000.0, result.getBalance(), 0.01);
  }

  @Test
  void calculateTotalProfitLoss_excludesCurrencyConversionDeposits() {
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll())
        .thenReturn(
            List.of(
                cash(CashOperationType.DEPOSIT, 500.0, "Currency Conversion EUR -> USD"),
                cash(CashOperationType.DEPOSIT, 1000.0, "Bank deposit")));

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    // Currency-conversion row excluded -> only the 1000 USD deposit counts.
    assertEquals(1000.0, result.getDeposits(), 0.01);
  }

  @Test
  void calculateTotalProfitLoss_appliesCapitalGainsTaxOnCurrentYearGains() {
    int year = java.time.Year.now().getValue();
    when(closedPositionRepository.findAll())
        .thenReturn(
            List.of(
                closed(
                    "AAPL.US", 1000.0, 0.0, PortfolioTestData.atNoon(LocalDate.of(year, 6, 30)))));
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioService.calculateTotalProfitLoss();

    assertEquals(190.0, result.getCapitalGainsTax(), 0.01); // 19% of 1000
    assertEquals(0.0, result.getLossCarryForward(), 0.01);
  }

  @Test
  void calculateWinRate_returnsZeroWhenNoTrades() {
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    assertEquals(0.0, portfolioService.calculateWinRate());
  }

  @Test
  void calculateWinRate_countsOnlyProfitableTrades() {
    when(closedPositionRepository.findAll())
        .thenReturn(
            List.of(
                closed("A", 10.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("B", -5.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("C", 20.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("D", -2.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR))));

    assertEquals(50.0, portfolioService.calculateWinRate());
  }

  @Test
  void calculatePerformancePerInstrument_includesBothOpenAndClosedPositions() {
    when(symbolPerformanceRepository.findAll())
        .thenReturn(
            List.of(
                new SymbolPerformance(
                    "AAPL.US", 100.0, 50.0, 150.0, 0.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformance(
                    "MSFT.US", 0.0, 30.0, 30.0, 0.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now())));

    List<InstrumentPerformance> performance = portfolioService.calculatePerformancePerInstrument();

    assertEquals(2, performance.size());
    InstrumentPerformance aapl =
        performance.stream().filter(p -> "AAPL.US".equals(p.getSymbol())).findFirst().orElseThrow();
    assertEquals(100.0, aapl.getClosedProfit(), 0.01);
    assertEquals(50.0, aapl.getUnrealizedProfit(), 0.01);
    assertEquals(150.0, aapl.getTotal(), 0.01);
  }

  @Test
  void calculateMonthlyPerformance_bucketsByYearAndMonth() {
    int year = java.time.Year.now().getValue();
    when(accountStatisticsRepository.findAll())
        .thenReturn(List.of(visibleAccountStats(1L, 1050.0, 75.0)));
    when(portfolioMonthlyPerformanceRepository.findAllByOrderByMonthAscPortfolioIdAsc())
        .thenReturn(
            List.of(
                portfolioMonthlyPerformance(LocalDate.of(year, 3, 1), 100.0, 0.0),
                portfolioMonthlyPerformance(LocalDate.of(year - 1, 7, 1), 50.0, 25.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, LocalDate.of(year, 3, 1), 100.0, 0.0, 1100.0),
                monthlyPerformance(1L, LocalDate.of(year - 1, 7, 1), 50.0, 25.0, 1050.0)));

    Performance perf = portfolioService.calculateMonthlyPerformance();
    Map<String, Double> monthly = perf.getCalculateMonthlyPerformance();
    assertTrue(monthly.containsKey(String.format("%d-07", year - 1)));
    assertTrue(monthly.keySet().stream().anyMatch(k -> k.startsWith(year + "-")));
    assertEquals(25.0, perf.getMonthlyCashflow().get(String.format("%d-07", year - 1)), 0.01);
    assertEquals(1L, perf.getMonthlyOperationsCount().get(String.format("%d-07", year - 1)));
  }

  @Test
  void calculateMonthlyPerformance_countsDistinctVisibleAccountsPerYearBucket() {
    int year = java.time.Year.now().getValue();
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
                visibleAccountStats(1L, 1000.0, 1000.0),
                visibleAccountStats(2L, 1000.0, 1000.0),
                visibleAccountStats(3L, 0.0, 0.0)));
    when(portfolioMonthlyPerformanceRepository.findAllByOrderByMonthAscPortfolioIdAsc())
        .thenReturn(List.of(portfolioMonthlyPerformance(LocalDate.of(year - 1, 1, 1), 160.0, 0.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, LocalDate.of(year - 1, 1, 1), 100.0, 0.0, 1000.0),
                monthlyPerformance(1L, LocalDate.of(year - 1, 2, 1), 50.0, 0.0, 1050.0),
                monthlyPerformance(2L, LocalDate.of(year - 1, 3, 1), -10.0, 0.0, 990.0),
                monthlyPerformance(2L, LocalDate.of(year - 1, 4, 1), 20.0, 0.0, 1010.0),
                monthlyPerformance(3L, LocalDate.of(year - 1, 5, 1), 5.0, 0.0, 5.0)));

    Performance perf = portfolioService.calculateMonthlyPerformance();

    assertEquals(1L, perf.getMonthlyOperationsCount().get(String.format("%d-01", year - 1)));
    assertEquals(1L, perf.getMonthlyOperationsCount().get(String.format("%d-02", year - 1)));
  }

  @Test
  void calculateMonthlyPerformance_usesPortfolioMonthlyRowsForPortfolioProfitAndFlow() {
    int year = java.time.Year.now().getValue() - 1;
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
                visibleAccountStats(1L, 1000.0, 1000.0), visibleAccountStats(2L, 1000.0, 1000.0)));
    when(portfolioMonthlyPerformanceRepository.findAllByOrderByMonthAscPortfolioIdAsc())
        .thenReturn(
            List.of(portfolioMonthlyPerformance(LocalDate.of(year, 1, 1), 2400.0, 82600.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, LocalDate.of(year, 1, 1), -39035.91, 17903.83, 1000.0),
                monthlyPerformance(2L, LocalDate.of(year, 1, 1), 24521.86, -5869.47, 1000.0)));

    Performance perf = portfolioService.calculateMonthlyPerformance();

    assertEquals(
        2400.0, perf.getCalculateMonthlyPerformance().get(String.format("%d-01", year)), 0.01);
    assertEquals(82600.0, perf.getMonthlyCashflow().get(String.format("%d-01", year)), 0.01);
    assertEquals(2L, perf.getMonthlyOperationsCount().get(String.format("%d-01", year)));
  }

  private static ClosedPosition closed(
      String symbol, double profit, double commission, ZonedDateTime closeTime) {
    ClosedPosition cp =
        PortfolioBuilders.closedPosition(PortfolioTestData.AAPL)
            .symbol(symbol)
            .currency(CurrencyType.USD)
            .profit(profit)
            .commission(commission)
            .swap(0.0)
            .closeOn(closeTime.toLocalDate())
            .build();
    cp.setCloseTime(closeTime);
    cp.setVolume(1.0);
    cp.setOpenPrice(100.0);
    cp.setClosePrice(100.0 + profit);
    return cp;
  }

  private static OpenedPosition opened(String symbol, double profit, double commission) {
    return PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
        .symbol(symbol)
        .currency(CurrencyType.USD)
        .quantity(1.0)
        .price(100.0)
        .marketPrice(100.0 + profit)
        .commission(commission)
        .swap(0.0)
        .build();
  }

  private static PortfolioMonthlyPerformance portfolioMonthlyPerformance(
      LocalDate month, double profit, double netCashflow) {
    double deposits = Math.max(0.0, netCashflow);
    double withdrawals = Math.max(0.0, -netCashflow);
    return new PortfolioMonthlyPerformance(
        1L,
        month,
        month,
        month.plusMonths(1).minusDays(1),
        0.0,
        profit + deposits - withdrawals,
        CurrencyType.USD,
        deposits,
        withdrawals,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        profit,
        null,
        ZonedDateTime.now());
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

  private static AccountStatistics visibleAccountStats(
      Long accountId, double balance, double netDeposit) {
    return PortfolioBuilders.accountStatistics()
        .account(new AccountDefinition(accountId, "A" + accountId, CurrencyType.USD, "Test"))
        .balances(0.0, balance, balance)
        .deposits(netDeposit, 0.0)
        .build();
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

  private static CashOperation subaccountTransfer(long id, double amount, String comment) {
    CashOperation operation = cash(CashOperationType.SUBACCOUNT_TRANSFER, amount, comment);
    operation.setId(id);
    operation.setAccount(51499241L);
    operation.setDate(ZonedDateTime.parse("2026-01-01T12:00:00Z").plusMinutes(id));
    return operation;
  }

  private static CashOperation dividend() {
    CashOperation c = cash(CashOperationType.DIVIDEND, 100.0, null);
    c.setSymbol("AAPL.US");
    c.setCurrency(CurrencyType.USD);
    return c;
  }

  private static CashOperation dividendTax() {
    CashOperation c = cash(CashOperationType.WITHHOLDING_TAX, -15.0, null);
    c.setSymbol("AAPL.US");
    c.setCurrency(CurrencyType.USD);
    return c;
  }
}
