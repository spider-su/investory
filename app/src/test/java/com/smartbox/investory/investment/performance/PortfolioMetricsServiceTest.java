package com.smartbox.investory.investment.performance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.model.AccountBalance;
import com.smartbox.investory.investment.api.reporting.model.DividendGainer;
import com.smartbox.investory.investment.api.reporting.model.InstrumentPerformance;
import com.smartbox.investory.investment.api.reporting.model.PortfolioDataQualityIssue;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioCurrencyBreakdownEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioCurrencyBreakdownRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioDataQualityRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioFallbackReconciliationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.cash.CashFlowAggregator;
import com.smartbox.investory.investment.ledger.cash.CashOperationNormalizer;
import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationRepository;
import com.smartbox.investory.investment.ledger.cash.persistence.NormalizedCashOperationRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.performance.model.Performance;
import com.smartbox.investory.investment.performance.model.Portfolio;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQueryService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioBuilders;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AccountDefinition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Portfolio Service")
class PortfolioMetricsServiceTest {

  @Mock private CurrencyRateService currencyRateService;
  @Mock private PositionRepository closedPositionRepository;
  @Mock private PositionRepository openedPositionRepository;
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
  @Mock private PortfolioRiskExposureCalculator riskExposureCalculator;

  private PortfolioMetricsService portfolioMetricsService;
  private PortfolioProperties portfolioProperties;

  @BeforeEach
  void setUp() {
    // Use real helpers so the test still exercises end-to-end behaviour after the extraction.
    TaxCalculator taxCalculator = new TaxCalculator(currencyRateService);
    CashFlowAggregator cashFlowAggregator = new CashFlowAggregator(currencyRateService);
    portfolioProperties = new PortfolioProperties();
    portfolioProperties.setDataQualityIssuesEnabled(false);
    portfolioProperties.setDashboardEnrichmentEnabled(true);
    PortfolioPerformanceQueryService performanceQueryService =
        new PortfolioPerformanceQueryService(
            closedPositionRepository,
            accountDailyRepository,
            accountRepository,
            accountMonthlyPerformanceRepository,
            accountStatisticsRepository,
            portfolioMonthlyPerformanceRepository,
            symbolPerformanceRepository);
    portfolioMetricsService =
        new PortfolioMetricsService(
            currencyRateService,
            closedPositionRepository,
            openedPositionRepository,
            cashOperationRepository,
            normalizedCashOperationRepository,
            accountRepository,
            accountStatisticsRepository,
            portfolioAssetAllocationRepository,
            assetRepository,
            portfolioCurrencyBreakdownRepository,
            portfolioKpiSummaryRepository,
            fallbackReconciliationRepository,
            dataQualityRepository,
            symbolPerformanceRepository,
            taxCalculator,
            cashFlowAggregator,
            new CashOperationNormalizer(),
            portfolioProperties,
            performanceQueryService,
            riskExposureCalculator);
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
        .thenReturn(Optional.empty());
  }

  @DisplayName("calculate Total Profit Loss loads Detailed Data Quality Issues When Enabled")
  @Test
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

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(1, result.getDataQuality().issues().size());
    PortfolioDataQualityIssue issue = result.getDataQuality().issues().getFirst();
    assertEquals("51499241", issue.accountId());
    assertEquals("12501", issue.assetId());
    assertEquals("STALE_PRICE", issue.code());
    assertEquals(LocalDate.of(2026, 7, 20), issue.priceDate());
    assertEquals(77L, issue.priceHistoryId());
    verify(dataQualityRepository).findIssues();
  }

  @DisplayName("calculate Total Profit Loss uses Portfolio Kpi Summary For Dashboard Metrics")
  @Test
  void calculateTotalProfitLoss_usesPortfolioKpiSummaryForDashboardMetrics() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummaryEntity(
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
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(openedPositionRepository.findOpen())
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
    AccountEntity account = new AccountEntity();
    account.setId(17959259L);
    account.setName("IBKR");
    account.setCurrency(CurrencyType.PLN);
    AccountEntity emptyAccount = new AccountEntity();
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
        .thenReturn(Optional.of(BigDecimal.valueOf(4.0)));

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

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

  @DisplayName("calculate Total Profit Loss keeps Funded Zero Balance Accounts Visible")
  @Test
  void calculateTotalProfitLoss_keepsFundedZeroBalanceAccountsVisible() {
    AccountEntity account = new AccountEntity();
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
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    when(openedPositionRepository.findOpen()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(1, result.getAccountBalances().size());
    assertEquals(51747407L, result.getAccountBalances().getFirst().getAccountId());
    assertEquals(13370.17, result.getAccountBalances().getFirst().getNetDeposit(), 0.01);
    assertEquals(0.0, result.getAccountBalances().getFirst().getProfitLossPercent(), 0.01);
  }

  @DisplayName(
      "calculate Total Profit Loss uses Transfer Aware Normalized Net Deposits For Account Cards")
  @Test
  void calculateTotalProfitLoss_usesTransferAwareNormalizedNetDepositsForAccountCards() {
    AccountEntity account = new AccountEntity();
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
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    when(openedPositionRepository.findOpen()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(normalizedCashOperationRepository.findAllByAccountIdIn(any()))
        .thenReturn(
            List.of(
                normalizedCashOperationRow("EXTERNAL_DEPOSIT", 13370.17, 13370.17),
                normalizedCashOperationRow("INTERNAL_TRANSFER_OUT", -13358.49, -13358.49)));

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(1, result.getAccountBalances().size());
    assertEquals(11.68, result.getAccountBalances().getFirst().getNetDeposit(), 0.01);
    assertEquals(11.68, result.getAccountBalances().getFirst().getBaseNetDeposit(), 0.01);
  }

  @DisplayName(
      "calculate Total Profit Loss uses Base Balance For Base Currency Account Profit Loss")
  @Test
  void calculateTotalProfitLoss_usesBaseBalanceForBaseCurrencyAccountProfitLoss() {
    AccountEntity account = new AccountEntity();
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
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    when(openedPositionRepository.findOpen()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(1, result.getAccountBalances().size());
    assertEquals(51499241L, result.getAccountBalances().getFirst().getAccountId());
    assertEquals(22104.0, result.getAccountBalances().getFirst().getLocalBalance(), 0.01);
    assertEquals(1.9963, result.getAccountBalances().getFirst().getProfitLossPercent(), 0.001);
  }

  @DisplayName("xtb Usd Subaccount Transfer Adjusts Account Basis Not Portfolio Basis")
  @Test
  void xtbUsdSubaccountTransferAdjustsAccountBasisNotPortfolioBasis() {
    AccountEntity account = new AccountEntity();
    account.setId(51499241L);
    account.setName("XTB USD");
    account.setProvider("XTB");
    account.setCurrency(CurrencyType.USD);
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummaryEntity(
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
            List.of(normalizedCashOperationRow(51499241L, "EXTERNAL_DEPOSIT", 21670.90, 21670.90)));
    when(cashOperationRepository.findAllByAccountIn(any()))
        .thenReturn(
            List.of(
                subaccountTransfer(1L, -801.47, "Transfer from 51993106 to 51499241"),
                subaccountTransfer(2L, 801.47, "Transfer from 51993106 to 51499241")));
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(22472.37, result.getAccountBalances().getFirst().getNetDeposit(), 0.01);
    assertEquals(7527.63, result.getAccountBalances().getFirst().getProfit(), 0.01);
    assertEquals(21670.90, result.getNetDeposits(), 0.01);
    assertEquals(21670.90, result.getAccountBalancesTotal().getNetDeposit(), 0.01);
    assertEquals(8329.10, result.getAccountBalancesTotal().getProfit(), 0.01);
    assertEquals(
        result.getAccountBalancesTotal().getBalance().doubleValue()
            - result.getAccountBalancesTotal().getNetDeposit().doubleValue(),
        result.getAccountBalancesTotal().getProfit(),
        0.01);
    assertEquals(
        result.getAccountBalancesTotal().getProfit().doubleValue()
            / result.getAccountBalancesTotal().getNetDeposit().doubleValue()
            * 100.0,
        result.getAccountBalancesTotal().getProfitLossPercent(),
        0.01);
  }

  @DisplayName(
      "calculate Total Profit Loss derives Dashboard Total Profit From Balance And Net Deposit")
  @Test
  void calculateTotalProfitLoss_derivesDashboardTotalProfitFromBalanceAndNetDeposit() {
    AccountEntity account = new AccountEntity();
    account.setId(51499241L);
    account.setName("Trading USD");
    account.setCurrency(CurrencyType.USD);
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummaryEntity(
                    1L,
                    "Portfolio",
                    CurrencyType.USD,
                    143694.0,
                    143694.0,
                    162745.0,
                    0.0,
                    162745.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    ZonedDateTime.now())));
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.accountStatistics()
                    .account(
                        new AccountDefinition(51499241L, "Trading USD", CurrencyType.USD, "XTB"))
                    .netDeposits(143694.0, 143694.0)
                    .balances(0.0, 162745.0, 162745.0)
                    .build()));
    when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(51499241L, account));
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    when(cashOperationRepository.findAllByAccountIn(any())).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();
    AccountBalance total = result.getAccountBalancesTotal();

    assertEquals(143694.0, total.getNetDeposit(), 0.01);
    assertEquals(162745.0, total.getBalance(), 0.01);
    assertEquals(19051.0, total.getProfit(), 0.01);
    assertEquals(13.26, total.getProfitLossPercent(), 0.01);
    assertEquals(
        total.getBalance().doubleValue() - total.getNetDeposit().doubleValue(),
        total.getProfit(),
        0.01);
    assertEquals(
        total.getProfit().doubleValue() / total.getNetDeposit().doubleValue() * 100.0,
        total.getProfitLossPercent(),
        0.0001);
  }

  @DisplayName("calculate Total Profit Loss uses Non Throwing Fx Lookup For Exchange Rate Board")
  @Test
  void calculateTotalProfitLoss_usesNonThrowingFxLookupForExchangeRateBoard() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummaryEntity(
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
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    when(currencyRateService.findRate(CurrencyType.USD, CurrencyType.EUR))
        .thenReturn(Optional.empty());
    when(currencyRateService.findRate(CurrencyType.USD, CurrencyType.PLN))
        .thenReturn(Optional.of(BigDecimal.valueOf(3.75)));

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertFalse(result.getExchangeRates().containsKey(CurrencyType.EUR));
    assertEquals(3.75, result.getExchangeRates().get(CurrencyType.PLN), 0.01);
    verify(currencyRateService, never())
        .getRate(any(CurrencyType.class), any(CurrencyType.class), any(LocalDate.class));
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
      public Double getAccountFlowAmountInPortfolioBaseCurrency() {
        return null;
      }

      @Override
      public Double getPerformanceFlowAmountInPortfolioBaseCurrency() {
        return null;
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

  @DisplayName("calculate Total Profit Loss populates Dividend Tax When Kpi Summary Is Used")
  @Test
  void calculateTotalProfitLoss_populatesDividendTaxWhenKpiSummaryIsUsed() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummaryEntity(
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
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    when(accountStatisticsRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(4912.0, result.getDividends(), 0.01);
    assertEquals(-15.0, result.getDividendTax(), 0.01);
  }

  @DisplayName(
      "calculate Total Profit Loss populates Dividend Gainers Sorted By Converted Usd With Other Bucket")
  @Test
  void calculateTotalProfitLoss_populatesDividendGainersSortedByConvertedUsdWithOtherBucket() {
    when(symbolPerformanceRepository.findAll())
        .thenReturn(
            List.of(
                new SymbolPerformanceEntity(
                    "PZU.PL", 0.0, 0.0, 0.0, 300.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "AAPL.US", 0.0, 0.0, 0.0, 200.0, 50.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_1", 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_2", 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_3", 0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_4", 0.0, 0.0, 0.0, 4.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_5", 0.0, 0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_6", 0.0, 0.0, 0.0, 6.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_7", 0.0, 0.0, 0.0, 7.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_8", 0.0, 0.0, 0.0, 8.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_9", 0.0, 0.0, 0.0, 9.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "SMALL_10", 0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now())));
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    when(openedPositionRepository.findOpen()).thenReturn(List.of());
    when(accountStatisticsRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

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

  @DisplayName("calculate Total Profit Loss keeps Kpi Unrealized When Open Position Rows Differ")
  @Test
  void calculateTotalProfitLoss_keepsKpiUnrealizedWhenOpenPositionRowsDiffer() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummaryEntity(
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
    when(closedPositionRepository.findClosed()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(6575.81104821, result.getUnrealizedProfit(), 0.01);
    assertEquals(15949.1714029, result.getTotalProfit(), 0.01);
  }

  @DisplayName("calculate Total Profit Loss skips Currency Breakdown Projection When Kpi Exists")
  @Test
  void calculateTotalProfitLoss_skipsCurrencyBreakdownProjectionWhenKpiExists() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummaryEntity(
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
    when(closedPositionRepository.findClosed()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(999.0, result.getRealizedProfit(), 0.01);
    assertEquals(999.0, result.getUnrealizedProfit(), 0.01);
    assertEquals(999.0, result.getDividends(), 0.01);
    assertEquals(2997.0, result.getTotalProfit(), 0.01);
    verify(portfolioCurrencyBreakdownRepository, never()).findAll();
  }

  @DisplayName("calculate Total Profit Loss preserves Configured Portfolio Base Currency")
  @Test
  void calculateTotalProfitLoss_preservesConfiguredPortfolioBaseCurrency() {
    when(portfolioKpiSummaryRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioKpiSummaryEntity(
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
    when(closedPositionRepository.findClosed()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(CurrencyType.EUR, result.getBaseCurrency());
    assertEquals(CurrencyType.EUR, result.getAccountBalancesTotal().getLocalCurrency());
    assertEquals(CurrencyType.EUR, result.getOpenPositionValuesTotal().getCurrency());
  }

  @DisplayName(
      "calculate Total Profit Loss falls Back When Currency Breakdown Contains Unsupported Metric Type")
  @Test
  void calculateTotalProfitLoss_fallsBackWhenCurrencyBreakdownContainsUnsupportedMetricType() {
    when(portfolioCurrencyBreakdownRepository.findAll())
        .thenReturn(
            List.of(
                new PortfolioCurrencyBreakdownEntity(
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
    AccountEntity account = new AccountEntity();
    account.setId(1L);
    account.setCurrency(CurrencyType.USD);
    when(accountRepository.findAll()).thenReturn(List.of(account));
    when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(1L, account));
    when(closedPositionRepository.findClosed()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(10.0, result.getRealizedProfit(), 0.01);
    assertEquals(20.0, result.getUnrealizedProfit(), 0.01);
    assertEquals(30.0, result.getDividends(), 0.01);
    assertEquals(60.0, result.getTotalProfit(), 0.01);
  }

  @DisplayName("calculate Total Profit Loss aggregates Realized And Unrealized And Dividends")
  @Test
  void calculateTotalProfitLoss_aggregatesRealizedAndUnrealizedAndDividends() {
    when(closedPositionRepository.findClosed())
        .thenReturn(
            List.of(
                closed(
                    "AAPL.US", 100.0, -1.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR))));
    when(openedPositionRepository.findOpen()).thenReturn(List.of(opened("MSFT.US", 50.0, 0.0)));
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
    AccountEntity account = new AccountEntity();
    account.setId(1L);
    account.setCurrency(CurrencyType.USD);
    account.setName("Main");
    when(accountRepository.findMapByIdIn(any())).thenReturn(Map.of(1L, account));

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(99.0, result.getRealizedProfit(), 0.01); // 100 - 1
    assertEquals(50.0, result.getUnrealizedProfit(), 0.01);
    assertEquals(25.0, result.getDividends(), 0.01);
    assertEquals(174.0, result.getTotalProfit(), 0.01);
    assertEquals(1000.0, result.getDeposits(), 0.01);
    assertEquals(-200.0, result.getWithdrawals(), 0.01);
    assertEquals(800.0, result.getNetDeposits(), 0.01);
    assertEquals(5000.0, result.getBalance(), 0.01);
  }

  @DisplayName("calculate Total Profit Loss excludes Currency Conversion Deposits")
  @Test
  void calculateTotalProfitLoss_excludesCurrencyConversionDeposits() {
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    when(openedPositionRepository.findOpen()).thenReturn(List.of());
    when(cashOperationRepository.findAll())
        .thenReturn(
            List.of(
                cash(CashOperationType.DEPOSIT, 500.0, "Currency Conversion EUR -> USD"),
                cash(CashOperationType.DEPOSIT, 1000.0, "Bank deposit")));

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    // Currency-conversion row excluded -> only the 1000 USD deposit counts.
    assertEquals(1000.0, result.getDeposits(), 0.01);
  }

  @DisplayName("calculate Total Profit Loss applies Capital Gains Tax On Current Year Gains")
  @Test
  void calculateTotalProfitLoss_appliesCapitalGainsTaxOnCurrentYearGains() {
    int year = java.time.Year.now().getValue();
    when(closedPositionRepository.findClosed())
        .thenReturn(
            List.of(
                closed(
                    "AAPL.US", 1000.0, 0.0, PortfolioTestData.atNoon(LocalDate.of(year, 6, 30)))));
    when(openedPositionRepository.findOpen()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());

    Portfolio result = portfolioMetricsService.calculateTotalProfitLoss();

    assertEquals(190.0, result.getCapitalGainsTax(), 0.01); // 19% of 1000
    assertEquals(0.0, result.getLossCarryForward(), 0.01);
  }

  @DisplayName("calculate Win Rate returns Zero When No Trades")
  @Test
  void calculateWinRate_returnsZeroWhenNoTrades() {
    when(closedPositionRepository.findClosed()).thenReturn(List.of());
    assertEquals(0.0, portfolioMetricsService.calculateWinRate());
  }

  @DisplayName("calculate Win Rate counts Only Profitable Trades")
  @Test
  void calculateWinRate_countsOnlyProfitableTrades() {
    when(closedPositionRepository.findClosed())
        .thenReturn(
            List.of(
                closed("A", 10.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("B", -5.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("C", 20.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR)),
                closed("D", -2.0, 0.0, PortfolioTestData.atNoon(PortfolioTestData.MID_YEAR))));

    assertEquals(50.0, portfolioMetricsService.calculateWinRate());
  }

  @DisplayName("calculate Performance Per Instrument includes Both Open And Closed Positions")
  @Test
  void calculatePerformancePerInstrument_includesBothOpenAndPositionEntitys() {
    when(symbolPerformanceRepository.findAll())
        .thenReturn(
            List.of(
                new SymbolPerformanceEntity(
                    "AAPL.US", 100.0, 50.0, 150.0, 0.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now()),
                new SymbolPerformanceEntity(
                    "MSFT.US", 0.0, 30.0, 30.0, 0.0, 0.0, 0.0, 0.0, 0.0, ZonedDateTime.now())));

    List<InstrumentPerformance> performance =
        portfolioMetricsService.calculatePerformancePerInstrument();

    assertEquals(2, performance.size());
    InstrumentPerformance aapl =
        performance.stream().filter(p -> "AAPL.US".equals(p.getSymbol())).findFirst().orElseThrow();
    assertEquals(100.0, aapl.getClosedProfit(), 0.01);
    assertEquals(50.0, aapl.getUnrealizedProfit(), 0.01);
    assertEquals(150.0, aapl.getTotal(), 0.01);
  }

  @DisplayName("calculate Monthly Performance buckets By Year And Month")
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

    Performance perf = portfolioMetricsService.calculateMonthlyPerformance();
    Map<String, Double> monthly = perf.getCalculateMonthlyPerformance();
    assertTrue(monthly.containsKey(String.format("%d-07", year - 1)));
    assertTrue(monthly.keySet().stream().anyMatch(k -> k.startsWith(year + "-")));
    assertEquals(25.0, perf.getMonthlyCashflow().get(String.format("%d-07", year - 1)), 0.01);
    assertEquals(1L, perf.getMonthlyOperationsCount().get(String.format("%d-07", year - 1)));
  }

  @DisplayName("calculate Monthly Performance counts Distinct Visible Accounts Per Year Bucket")
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

    Performance perf = portfolioMetricsService.calculateMonthlyPerformance();

    assertEquals(1L, perf.getMonthlyOperationsCount().get(String.format("%d-01", year - 1)));
    assertEquals(1L, perf.getMonthlyOperationsCount().get(String.format("%d-02", year - 1)));
  }

  @DisplayName(
      "calculate Monthly Performance uses Portfolio Monthly Rows For Portfolio Profit And Flow")
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

    Performance perf = portfolioMetricsService.calculateMonthlyPerformance();

    assertEquals(
        2400.0, perf.getCalculateMonthlyPerformance().get(String.format("%d-01", year)), 0.01);
    assertEquals(82600.0, perf.getMonthlyCashflow().get(String.format("%d-01", year)), 0.01);
    assertEquals(2L, perf.getMonthlyOperationsCount().get(String.format("%d-01", year)));
  }

  private static PositionEntity closed(
      String symbol, double profit, double commission, ZonedDateTime closeTime) {
    PositionEntity cp =
        PortfolioBuilders.closedPosition(PortfolioTestData.AAPL)
            .symbol(symbol)
            .currency(CurrencyType.USD)
            .profit(profit)
            .commission(commission)
            .swap(0.0)
            .closeOn(closeTime.toLocalDate())
            .build();
    cp.setCloseTime(closeTime);
    cp.setVolume(java.math.BigDecimal.valueOf(1.0));
    cp.setOpenPrice(java.math.BigDecimal.valueOf(100.0));
    cp.setClosePrice(java.math.BigDecimal.valueOf(100.0 + profit));
    return cp;
  }

  private static PositionEntity opened(String symbol, double profit, double commission) {
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

  private static PortfolioMonthlyPerformanceEntity portfolioMonthlyPerformance(
      LocalDate month, double profit, double netCashflow) {
    double deposits = Math.max(0.0, netCashflow);
    double withdrawals = Math.max(0.0, -netCashflow);
    return new PortfolioMonthlyPerformanceEntity(
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
        0.0,
        ZonedDateTime.now());
  }

  private static AccountMonthlyPerformanceEntity monthlyPerformance(
      Long accountId, LocalDate month, double profit, double netCashflow, double endEquity) {
    return new AccountMonthlyPerformanceEntity(
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

  private static AccountStatisticsEntity visibleAccountStats(
      Long accountId, double balance, double netDeposit) {
    return PortfolioBuilders.accountStatistics()
        .account(new AccountDefinition(accountId, "A" + accountId, CurrencyType.USD, "Test"))
        .balances(0.0, balance, balance)
        .deposits(netDeposit, 0.0)
        .build();
  }

  private static CashOperationEntity cash(CashOperationType type, double amount, String comment) {
    return PortfolioBuilders.cashOperation()
        .type(type)
        .deposit(amount, CurrencyType.USD)
        .type(type)
        .comment(comment)
        .on(PortfolioTestData.MID_YEAR)
        .build();
  }

  private static CashOperationEntity subaccountTransfer(long id, double amount, String comment) {
    CashOperationEntity operation = cash(CashOperationType.SUBACCOUNT_TRANSFER, amount, comment);
    operation.setId(id);
    operation.setAccount(51499241L);
    operation.setDate(ZonedDateTime.parse("2026-01-01T12:00:00Z").plusMinutes(id));
    return operation;
  }

  private static CashOperationEntity dividend() {
    CashOperationEntity c = cash(CashOperationType.DIVIDEND, 100.0, null);
    c.setSymbol("AAPL.US");
    c.setCurrency(CurrencyType.USD);
    return c;
  }

  private static CashOperationEntity dividendTax() {
    CashOperationEntity c = cash(CashOperationType.WITHHOLDING_TAX, -15.0, null);
    c.setSymbol("AAPL.US");
    c.setCurrency(CurrencyType.USD);
    return c;
  }

  private static void assertEquals(Object expected, Object actual, Object... extras) {
    if (extras.length == 0) {
      if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
        org.junit.jupiter.api.Assertions.assertEquals(
            expectedNumber.doubleValue(), actualNumber.doubleValue());
        return;
      }
      org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
      return;
    }
    org.junit.jupiter.api.Assertions.assertEquals(
        ((Number) expected).doubleValue(),
        ((Number) actual).doubleValue(),
        ((Number) extras[0]).doubleValue());
  }
}
