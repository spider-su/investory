package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.infrastructure.repository.account.AccountDaily;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.infrastructure.repository.account.AccountStatistics;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformanceRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioAssetAllocationRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioCurrencyBreakdownRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioKpiSummaryRepository;
import com.example.demo.infrastructure.repository.portfolio.SymbolPerformanceRepository;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.testsupport.portfolio.PortfolioBuilders;
import com.example.demo.testsupport.portfolio.PortfolioTestData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioProjectionServiceTest {

  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private ClosedPositionRepository closedPositionRepository;
  @Mock private CashOperationRepository cashOperationRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private AccountDailyRepository accountDailyRepository;
  @Mock private AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
  @Mock private AccountStatisticsRepository accountStatisticsRepository;
  @Mock private PortfolioAssetAllocationRepository portfolioAssetAllocationRepository;
  @Mock private PortfolioCurrencyBreakdownRepository portfolioCurrencyBreakdownRepository;
  @Mock private PortfolioKpiSummaryRepository portfolioKpiSummaryRepository;
  @Mock private SymbolPerformanceRepository symbolPerformanceRepository;
  @Mock private CurrencyRateService currencyRateService;
  @Spy private CashOperationNormalizer cashOperationNormalizer = new CashOperationNormalizer();

  @InjectMocks private PortfolioProjectionService service;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient().when(accountRepository.findAll()).thenReturn(List.of());
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_buildsAndPersistsAllProjectionTables() {
    OpenedPosition opened =
        PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
            .forAccount(PortfolioTestData.IBKR_USD)
            .quantity(2.0)
            .price(100.0)
            .marketPrice(105.0)
            .commission(-1.0)
            .on(PortfolioTestData.AAPL_FIRST_BUY_DATE)
            .build();

    CashOperation dividend =
        PortfolioBuilders.cashOperation()
            .forAccount(PortfolioTestData.IBKR_USD)
            .dividend(PortfolioTestData.AAPL, 3.0)
            .on(PortfolioTestData.JANUARY_MONTH_END)
            .build();

    CashOperation deposit =
        PortfolioBuilders.cashOperation()
            .forAccount(PortfolioTestData.IBKR_USD)
            .deposit(200.0, CurrencyType.USD)
            .on(PortfolioTestData.JANUARY_DEPOSIT_DATE)
            .build();

    Asset asset =
        PortfolioBuilders.asset(PortfolioTestData.AAPL)
            .withLatestPrice(120.0, 120.0, PortfolioTestData.JANUARY_MONTH_END)
            .build();

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, dividend));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(currencyRateService.convertToBaseCurrency(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> accountDailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(accountDailyCaptor.capture());
    verify(accountDailyRepository).refreshReportingViews();
    assertFalse(toList(accountDailyCaptor.getValue()).isEmpty());
  }

  @Test
  void recalculateAll_handlesEmptyInputs() {
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(assetRepository.findAll()).thenReturn(List.of());

    service.recalculateAll();

    verify(accountDailyRepository).saveAll(anyList());
    verify(accountDailyRepository).refreshReportingViews();
    verify(currencyRateService, never())
        .convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_storesPlnAccountStatisticsInUsd() {
    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51551301L);
    opened.setSymbol("AAPL.US");
    opened.setCurrency(CurrencyType.PLN);
    opened.setType(PositionType.BUY);
    opened.setVolume(1.0);
    opened.setOpenPrice(400.0);
    opened.setPurchaseValue(400.0);
    opened.setOpenTime(ZonedDateTime.now().minusDays(1));

    CashOperation deposit = new CashOperation();
    deposit.setAccount(51551301L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(400.0);
    deposit.setCurrency(CurrencyType.PLN);
    deposit.setDate(ZonedDateTime.now().minusDays(2));

    Asset asset = new Asset();
    asset.setSymbol("AAPL.US");
    asset.setMarketPriceUsd(120.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              double amount = invocation.getArgument(0);
              CurrencyType target = invocation.getArgument(1);
              CurrencyType source = invocation.getArgument(2);
              if (target == CurrencyType.USD && source == CurrencyType.PLN) {
                return amount / 4.0;
              }
              if (target == CurrencyType.PLN && source == CurrencyType.USD) {
                return amount * 4.0;
              }
              return amount;
            });

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> accountDailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(accountDailyCaptor.capture());
    AccountDaily latest =
        toList(accountDailyCaptor.getValue()).stream()
            .filter(row -> row.getAccountId().equals(51551301L))
            .max(java.util.Comparator.comparing(AccountDaily::getDate))
            .orElseThrow();

    assertEquals(120.0, latest.getMarketValue(), 0.01);
    assertEquals(100.0, latest.getCostBase(), 0.01);
    assertEquals(20.0, latest.getUnrealizedProfit(), 0.01);

  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_usesHistoricalPriceForDailyMarketValue() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51499241L);
    opened.setSymbol("AAPL.US");
    opened.setCurrency(CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(10.0);
    opened.setOpenPrice(100.0);
    opened.setPurchaseValue(1000.0);
    opened.setOpenTime(tradeDate);

    CashOperation deposit = new CashOperation();
    deposit.setAccount(51499241L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(1000.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(tradeDate.minusDays(1));

    Asset asset = new Asset();
    asset.setSymbol("AAPL.US");
    asset.setMarketPriceUsd(999.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(List.of(historicalPrice("AAPL.US", tradeDate.toLocalDate(), 110.0, "USD", 95)));
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.eq(CurrencyType.USD),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily tradeDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(tradeDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(1100.0, tradeDay.getMarketValue(), 0.01);
    assertEquals(100.0, tradeDay.getUnrealizedProfit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_carriesOpenPositionsForwardToToday() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51499241L);
    opened.setSymbol("AAPL.US");
    opened.setCurrency(CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(10.0);
    opened.setOpenPrice(100.0);
    opened.setPurchaseValue(1000.0);
    opened.setOpenTime(tradeDate);

    Asset asset = new Asset();
    asset.setSymbol("AAPL.US");
    asset.setMarketPriceUsd(120.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.eq(CurrencyType.USD),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily today =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(LocalDate.now(ZoneId.of("Europe/Warsaw"))))
            .findFirst()
            .orElseThrow();

    assertEquals(1200.0, today.getMarketValue(), 0.01);
    assertEquals(200.0, today.getUnrealizedProfit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_prefersHistoricalPriceOverCashTradeFallback() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    CashOperation deposit = new CashOperation();
    deposit.setAccount(51499241L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(1000.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(tradeDate.minusDays(1));

    CashOperation stockPurchase = new CashOperation();
    stockPurchase.setAccount(51499241L);
    stockPurchase.setType(CashOperationType.STOCK_PURCHASE);
    stockPurchase.setAmount(-1000.0);
    stockPurchase.setCurrency(CurrencyType.USD);
    stockPurchase.setDate(tradeDate);
    stockPurchase.setSymbol("AAPL.US");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51499241L);
    opened.setSymbol("AAPL.US");
    opened.setCurrency(CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(10.0);
    opened.setOpenPrice(100.0);
    opened.setPurchaseValue(1000.0);
    opened.setOpenTime(tradeDate);

    Asset asset = new Asset();
    asset.setSymbol("AAPL.US");
    asset.setMarketPriceUsd(100.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, stockPurchase));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(List.of(historicalPrice("AAPL.US", tradeDate.toLocalDate(), 80.0, "USD", 95)));
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.eq(CurrencyType.USD),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily tradeDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(tradeDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(800.0, tradeDay.getMarketValue(), 0.01);
    assertEquals(-200.0, tradeDay.getUnrealizedProfit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_usesCostBasisBeforeFirstKnownHistoricalPriceInsteadOfFutureSnapshotPrice() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-02-13T12:00:00Z");

    CashOperation deposit = new CashOperation();
    deposit.setAccount(51499241L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(5000.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(tradeDate.minusDays(1));

    CashOperation stockPurchase = new CashOperation();
    stockPurchase.setAccount(51499241L);
    stockPurchase.setType(CashOperationType.STOCK_PURCHASE);
    stockPurchase.setAmount(-1901.80);
    stockPurchase.setCurrency(CurrencyType.USD);
    stockPurchase.setDate(tradeDate);
    stockPurchase.setSymbol("DTLA.UK");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51499241L);
    opened.setSymbol("DTLA.UK");
    opened.setCurrency(CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(400.0);
    opened.setOpenPrice(4.7545);
    opened.setPurchaseValue(1901.80);
    opened.setOpenTime(tradeDate);

    Asset asset = new Asset();
    asset.setSymbol("DTLA.UK");
    asset.setMarketPriceUsd(0.55);
    asset.setPriceUpdatedAt(ZonedDateTime.parse("2026-05-13T12:00:00Z"));

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, stockPurchase));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.eq(CurrencyType.USD),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily tradeDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(tradeDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(1901.80, tradeDay.getMarketValue(), 0.01);
    assertEquals(5000.0, tradeDay.getEquity(), 0.01);
    assertEquals(0.0, tradeDay.getUnrealizedProfit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_doesNotReuseMonthsOldHistoricalPriceForPastValuation() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2025-12-05T12:00:00Z");

    CashOperation deposit = new CashOperation();
    deposit.setAccount(51499241L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(5000.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(tradeDate.minusDays(1));

    CashOperation stockPurchase = new CashOperation();
    stockPurchase.setAccount(51499241L);
    stockPurchase.setType(CashOperationType.STOCK_PURCHASE);
    stockPurchase.setAmount(-2329.205436);
    stockPurchase.setCurrency(CurrencyType.USD);
    stockPurchase.setDate(tradeDate);
    stockPurchase.setSymbol("JGPI.DE");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51499241L);
    opened.setSymbol("JGPI.DE");
    opened.setCurrency(CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(87.0);
    opened.setOpenPrice(2329.205436 / 87.0);
    opened.setPurchaseValue(2329.205436);
    opened.setOpenTime(tradeDate);

    Asset asset = new Asset();
    asset.setSymbol("JGPI.DE");
    asset.setMarketPriceUsd(30.90414402);
    asset.setPriceSource("OpenPositionWeightedAverage");
    asset.setPriceUpdatedAt(ZonedDateTime.parse("2026-07-27T12:00:00Z"));

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, stockPurchase));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(List.of(historicalPrice("JGPI.DE", LocalDate.parse("2025-03-03"), 26.65, "USD", 95)));
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.eq(CurrencyType.USD),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily tradeDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(tradeDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(2329.205436, tradeDay.getMarketValue(), 0.01);
    assertEquals(5000.0, tradeDay.getEquity(), 0.01);
    assertEquals(0.0, tradeDay.getUnrealizedProfit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_doesNotCarryCashTradeFallbackAfterValuedPositionCloses() {
    ZonedDateTime openDate = ZonedDateTime.parse("2026-02-06T08:03:29Z");
    ZonedDateTime closeDate = ZonedDateTime.parse("2026-02-25T11:26:38Z");

    CashOperation stockPurchase = new CashOperation();
    stockPurchase.setAccount(51707603L);
    stockPurchase.setType(CashOperationType.STOCK_PURCHASE);
    stockPurchase.setAmount(-1006.50);
    stockPurchase.setCurrency(CurrencyType.PLN);
    stockPurchase.setDate(openDate);
    stockPurchase.setSymbol("ETFBW20TR.PL");

    CashOperation stockSell = new CashOperation();
    stockSell.setAccount(51707603L);
    stockSell.setType(CashOperationType.STOCK_SELL);
    stockSell.setAmount(1031.25);
    stockSell.setCurrency(CurrencyType.PLN);
    stockSell.setDate(closeDate);
    stockSell.setSymbol("ETFBW20TR.PL");

    ClosedPosition closed = new ClosedPosition();
    closed.setAccount(51707603L);
    closed.setSymbol("ETFBW20TR.PL");
    closed.setCurrency(CurrencyType.PLN);
    closed.setType(PositionType.BUY);
    closed.setVolume(15.0);
    closed.setOpenTime(openDate);
    closed.setCloseTime(closeDate);
    closed.setOpenPrice(67.10);
    closed.setClosePrice(68.75);
    closed.setPurchaseValue(1006.50);
    closed.setSaleValue(1031.25);
    closed.setProfit(24.75);
    closed.setCommission(0.0);
    closed.setSwap(0.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of(closed));
    when(cashOperationRepository.findAll()).thenReturn(List.of(stockPurchase, stockSell));
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily closeDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(closeDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(0.0, closeDay.getMarketValue(), 0.01);
    assertEquals(0.0, closeDay.getCostBase(), 0.01);
    assertEquals(24.75, closeDay.getCashBalance(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_doesNotTreatTradeTurnoverAsMonthlyReturn() {
    ZonedDateTime january = ZonedDateTime.parse("2026-01-10T12:00:00Z");
    ZonedDateTime februaryOpen = ZonedDateTime.parse("2026-02-10T12:00:00Z");
    ZonedDateTime februaryClose = ZonedDateTime.parse("2026-02-11T12:00:00Z");

    OpenedPosition januaryHolding = new OpenedPosition();
    januaryHolding.setAccount(51499241L);
    januaryHolding.setSymbol("AAPL.US");
    januaryHolding.setCurrency(CurrencyType.USD);
    januaryHolding.setType(PositionType.BUY);
    januaryHolding.setVolume(10.0);
    januaryHolding.setOpenPrice(100.0);
    januaryHolding.setPurchaseValue(1000.0);
    januaryHolding.setOpenTime(january);

    ClosedPosition februaryRoundTrip = new ClosedPosition();
    februaryRoundTrip.setAccount(51499241L);
    februaryRoundTrip.setSymbol("MSFT.US");
    februaryRoundTrip.setCurrency(CurrencyType.USD);
    februaryRoundTrip.setType(PositionType.BUY);
    februaryRoundTrip.setVolume(10.0);
    februaryRoundTrip.setOpenPrice(100.0);
    februaryRoundTrip.setClosePrice(100.0);
    februaryRoundTrip.setPurchaseValue(1000.0);
    februaryRoundTrip.setSaleValue(1000.0);
    februaryRoundTrip.setProfit(0.0);
    februaryRoundTrip.setCommission(0.0);
    februaryRoundTrip.setSwap(0.0);
    februaryRoundTrip.setOpenTime(februaryOpen);
    februaryRoundTrip.setCloseTime(februaryClose);

    Asset apple = new Asset();
    apple.setSymbol("AAPL.US");
    apple.setMarketPriceUsd(100.0);

    Asset microsoft = new Asset();
    microsoft.setSymbol("MSFT.US");
    microsoft.setMarketPriceUsd(100.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of(januaryHolding));
    when(closedPositionRepository.findAll()).thenReturn(List.of(februaryRoundTrip));
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(assetRepository.findAll()).thenReturn(List.of(apple, microsoft));
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    List<AccountDaily> rows = toList(dailyCaptor.getValue());
    AccountDaily february =
        rows.stream()
            .filter(row -> row.getDate().equals(februaryClose.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(0.0, rows.getFirst().getCashBalance(), 0.01);
    assertEquals(0.0, february.getCashBalance(), 0.01);
    assertEquals(1000.0, february.getEquity(), 0.01);
    assertEquals(0.0, february.getDailyReturn(), 0.0001);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_usesStockCashOperationsForCashSettlementWhenPresent() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51551301L);
    opened.setSymbol("PKO.WA");
    opened.setCurrency(CurrencyType.PLN);
    opened.setType(PositionType.BUY);
    opened.setVolume(10.0);
    opened.setOpenPrice(100.0);
    opened.setPurchaseValue(1000.0);
    opened.setOpenTime(tradeDate);

    CashOperation deposit = new CashOperation();
    deposit.setAccount(51551301L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(1000.0);
    deposit.setCurrency(CurrencyType.PLN);
    deposit.setDate(tradeDate.minusDays(1));

    CashOperation stockPurchase = new CashOperation();
    stockPurchase.setAccount(51551301L);
    stockPurchase.setType(CashOperationType.STOCK_PURCHASE);
    stockPurchase.setAmount(-990.0);
    stockPurchase.setCurrency(CurrencyType.PLN);
    stockPurchase.setDate(tradeDate);

    Asset asset = new Asset();
    asset.setSymbol("PKO.WA");
    asset.setMarketPriceUsd(100.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, stockPurchase));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily january =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(tradeDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(10.0, january.getCashBalance(), 0.01);
    assertEquals(1000.0, january.getMarketValue(), 0.01);
    assertEquals(1010.0, january.getEquity(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_carriesUnvaluedStockPurchasesUntilPositionValuationExists() {
    ZonedDateTime depositDate = ZonedDateTime.parse("2026-01-09T12:00:00Z");
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    CashOperation deposit = new CashOperation();
    deposit.setAccount(17959259L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(1000.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(depositDate);

    CashOperation stockPurchase = new CashOperation();
    stockPurchase.setAccount(17959259L);
    stockPurchase.setType(CashOperationType.STOCK_PURCHASE);
    stockPurchase.setAmount(-900.0);
    stockPurchase.setCurrency(CurrencyType.USD);
    stockPurchase.setDate(tradeDate);
    stockPurchase.setSymbol("T458022826");
    stockPurchase.setComment("United States Treasury");

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, stockPurchase));
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily tradeDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(tradeDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(100.0, tradeDay.getCashBalance(), 0.01);
    assertEquals(900.0, tradeDay.getMarketValue(), 0.01);
    assertEquals(1000.0, tradeDay.getEquity(), 0.01);
    assertEquals(0.0, tradeDay.getDailyReturn(), 0.0001);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_countsTransfersInAccountStatisticsNetDeposit() {
    ZonedDateTime transferDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    CashOperation transferOut = new CashOperation();
    transferOut.setAccount(51499241L);
    transferOut.setType(CashOperationType.SUBACCOUNT_TRANSFER);
    transferOut.setAmount(-500.0);
    transferOut.setCurrency(CurrencyType.USD);
    transferOut.setDate(transferDate);

    CashOperation transferIn = new CashOperation();
    transferIn.setAccount(51822121L);
    transferIn.setType(CashOperationType.SUBACCOUNT_TRANSFER);
    transferIn.setAmount(500.0);
    transferIn.setCurrency(CurrencyType.USD);
    transferIn.setDate(transferDate.plusMinutes(1));

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(transferOut, transferIn));
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountStatistics>> statsCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(accountStatisticsRepository).saveAll(statsCaptor.capture());
    Map<Long, AccountStatistics> statsByAccount =
        toList(statsCaptor.getValue()).stream()
            .collect(java.util.stream.Collectors.toMap(AccountStatistics::getAccountId, stat -> stat));

    assertEquals(-500.0, statsByAccount.get(51499241L).getTotalWithdrawal(), 0.01);
    assertEquals(-500.0, statsByAccount.get(51499241L).getNetDeposit(), 0.01);
    assertEquals(500.0, statsByAccount.get(51822121L).getTotalDeposit(), 0.01);
    assertEquals(500.0, statsByAccount.get(51822121L).getNetDeposit(), 0.01);
    assertEquals(0.0, statsByAccount.values().stream().mapToDouble(AccountStatistics::getNetDeposit).sum(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_excludesIbkrBondCallFromAccountStatisticsNetDepositAndDoesNotDoubleCountCash() {
    ZonedDateTime depositDate = ZonedDateTime.parse("2026-01-05T12:00:00Z");
    ZonedDateTime callDate = ZonedDateTime.parse("2026-02-27T12:00:00Z");

    CashOperation deposit = new CashOperation();
    deposit.setAccount(17959259L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(48_131.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(depositDate);
    deposit.setComment("Electronic Fund Transfer");

    CashOperation bondPurchase = new CashOperation();
    bondPurchase.setAccount(17959259L);
    bondPurchase.setType(CashOperationType.STOCK_PURCHASE);
    bondPurchase.setAmount(-10_000.0);
    bondPurchase.setCurrency(CurrencyType.USD);
    bondPurchase.setDate(depositDate.plusDays(1));
    bondPurchase.setSymbol("T458022826.US");
    bondPurchase.setComment("T 4 5/8 02/28/26");

    CashOperation bondCall = new CashOperation();
    bondCall.setAccount(17959259L);
    bondCall.setType(CashOperationType.TRANSFER);
    bondCall.setAmount(10_000.0);
    bondCall.setCurrency(CurrencyType.USD);
    bondCall.setDate(callDate);
    bondCall.setComment(
        "(US91282CKB62) Full Call / Early Redemption for USD 1.00 per Bond "
            + "(T 4 5/8 02/28/26, T 4 5/8 02/28/26, US91282CKB62)");

    ClosedPosition closedBond = new ClosedPosition();
    closedBond.setAccount(17959259L);
    closedBond.setSymbol("T458022826.US");
    closedBond.setCurrency(CurrencyType.USD);
    closedBond.setType(PositionType.BUY);
    closedBond.setVolume(10_000.0);
    closedBond.setOpenTime(depositDate.plusDays(1));
    closedBond.setCloseTime(callDate);
    closedBond.setPurchaseValue(10_000.0);
    closedBond.setSaleValue(10_000.0);
    closedBond.setProfit(0.0);
    closedBond.setCommission(0.0);
    closedBond.setSwap(0.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of(closedBond));
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, bondPurchase, bondCall));
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountStatistics>> statsCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(accountStatisticsRepository).saveAll(statsCaptor.capture());
    AccountStatistics stats = toList(statsCaptor.getValue()).getFirst();

    assertEquals(48_131.0, stats.getTotalDeposit(), 0.01);
    assertEquals(0.0, stats.getTotalWithdrawal(), 0.01);
    assertEquals(48_131.0, stats.getNetDeposit(), 0.01);
    assertEquals(48_131.0, stats.getCashBalance(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_usesXtbCurrencyConversionRateForAccountBoundaryFunding() {
    ZonedDateTime conversionDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    CashOperation plnOut = new CashOperation();
    plnOut.setAccount(50290466L);
    plnOut.setType(CashOperationType.TRANSFER);
    plnOut.setAmount(-20_000.0);
    plnOut.setCurrency(CurrencyType.PLN);
    plnOut.setDate(conversionDate);
    plnOut.setComment(
        "Currency conversion, PLN to USD from TA: 50290466 to: 51499241, Exchange rate:0.250206");

    CashOperation usdIn = new CashOperation();
    usdIn.setAccount(51499241L);
    usdIn.setType(CashOperationType.TRANSFER);
    usdIn.setAmount(5_004.12);
    usdIn.setCurrency(CurrencyType.USD);
    usdIn.setDate(conversionDate.plusMinutes(1));
    usdIn.setComment(
        "Currency conversion, PLN to USD from TA: 50290466 to: 51499241, Exchange rate:0.250206");

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(plnOut, usdIn));
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountStatistics>> statsCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(accountStatisticsRepository).saveAll(statsCaptor.capture());
    Map<Long, AccountStatistics> statsByAccount =
        toList(statsCaptor.getValue()).stream()
            .collect(java.util.stream.Collectors.toMap(AccountStatistics::getAccountId, stat -> stat));

    assertEquals(-5_004.12, statsByAccount.get(50290466L).getNetDeposit(), 0.01);
    assertEquals(5_004.12, statsByAccount.get(51499241L).getNetDeposit(), 0.01);
    assertEquals(0.0, statsByAccount.values().stream().mapToDouble(AccountStatistics::getNetDeposit).sum(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_usesIbkrForexBaseAmountForCashFlowValuation() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-05-08T12:00:00Z");

    CashOperation forex = new CashOperation();
    forex.setAccount(17959259L);
    forex.setType(CashOperationType.DEPOSIT);
    forex.setAmount(-0.0158696);
    forex.setCurrency(CurrencyType.USD);
    forex.setDate(tradeDate);
    forex.setComment("Net Amount in Base from Forex Trade: -332.00 EUR.USD");

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(forex));
    when(assetRepository.findAll()).thenReturn(List.of());

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily daily = toList(dailyCaptor.getValue()).getFirst();

    assertEquals(-332.00, daily.getWithdrawals(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_countsXtbTransferOutOperationAsAccountWithdrawal() {
    ZonedDateTime transferDate = ZonedDateTime.parse("2026-04-20T12:00:00Z");

    CashOperation transferOut = new CashOperation();
    transferOut.setAccount(50290466L);
    transferOut.setType(CashOperationType.DEPOSIT);
    transferOut.setAmount(-5_000.0);
    transferOut.setCurrency(CurrencyType.PLN);
    transferOut.setDate(transferDate);
    transferOut.setComment("Transfer out operation on account with id 50290466");

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(transferOut));
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountStatistics>> statsCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(accountStatisticsRepository).saveAll(statsCaptor.capture());
    AccountStatistics stats = toList(statsCaptor.getValue()).getFirst();

    assertEquals(0.0, stats.getTotalDeposit(), 0.01);
    assertEquals(-5_000.0, stats.getTotalWithdrawal(), 0.01);
    assertEquals(-5_000.0, stats.getNetDeposit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_convertsNativeCashBalanceInsteadOfHistoricalCashMovements() {
    ZonedDateTime depositDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");
    ZonedDateTime withdrawalDate = ZonedDateTime.parse("2026-03-10T12:00:00Z");

    CashOperation deposit = new CashOperation();
    deposit.setAccount(51548444L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(100.0);
    deposit.setCurrency(CurrencyType.EUR);
    deposit.setDate(depositDate);

    CashOperation withdrawal = new CashOperation();
    withdrawal.setAccount(51548444L);
    withdrawal.setType(CashOperationType.WITHDRAWAL);
    withdrawal.setAmount(-100.0);
    withdrawal.setCurrency(CurrencyType.EUR);
    withdrawal.setDate(withdrawalDate);

    when(accountRepository.findAll()).thenReturn(List.of(account(51548444L, CurrencyType.EUR)));
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, withdrawal));
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.eq(CurrencyType.USD),
            org.mockito.ArgumentMatchers.eq(CurrencyType.EUR),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              double amount = invocation.getArgument(0);
              LocalDate date = invocation.getArgument(3);
              return date.getMonthValue() == 1 ? amount * 1.20 : amount * 1.10;
            });

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily latest =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(withdrawalDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(0.0, latest.getCashBalance(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_clearsDepositBasisForNearEmptyAccountResiduals() {
    ZonedDateTime transferDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    CashOperation residualFunding = new CashOperation();
    residualFunding.setAccount(50290466L);
    residualFunding.setType(CashOperationType.TRANSFER);
    residualFunding.setAmount(127.0);
    residualFunding.setCurrency(CurrencyType.USD);
    residualFunding.setDate(transferDate);
    residualFunding.setComment(
        "Currency conversion, USD to PLN from TA: 51499241 to: 50290466, Exchange rate:3.569154");

    CashOperation residualCashOut = new CashOperation();
    residualCashOut.setAccount(50290466L);
    residualCashOut.setType(CashOperationType.CORRECTION);
    residualCashOut.setAmount(-114.0);
    residualCashOut.setCurrency(CurrencyType.USD);
    residualCashOut.setDate(transferDate.plusMinutes(1));
    residualCashOut.setComment("FX rounding residual correction");

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(residualFunding, residualCashOut));
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountStatistics>> statsCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(accountStatisticsRepository).saveAll(statsCaptor.capture());
    AccountStatistics stats = toList(statsCaptor.getValue()).getFirst();

    assertEquals(0.0, stats.getTotalDeposit(), 0.01);
    assertEquals(0.0, stats.getTotalWithdrawal(), 0.01);
    assertEquals(0.0, stats.getNetDeposit(), 0.01);
    assertEquals(13.0, stats.getCashBalance(), 0.01);
  }

  private static AssetPriceHistoryRepository.HistoricalAssetPriceRow historicalPrice(
      String symbol, LocalDate priceDate, Double closePrice, String currency, Integer qualityScore) {
    return new AssetPriceHistoryRepository.HistoricalAssetPriceRow() {
      @Override
      public String getSymbol() {
        return symbol;
      }

      @Override
      public LocalDate getPriceDate() {
        return priceDate;
      }

      @Override
      public Double getClosePrice() {
        return closePrice;
      }

      @Override
      public String getPriceCurrency() {
        return currency;
      }

      @Override
      public Double getPriceScaleFactor() {
        return 1.0;
      }

      @Override
      public Integer getQualityScore() {
        return qualityScore;
      }
    };
  }

  private static Account account(Long id, CurrencyType currency) {
    Account account = new Account();
    account.setId(id);
    account.setCurrency(currency);
    account.setName(String.valueOf(id));
    return account;
  }

  private static <T> List<T> toList(Iterable<T> iterable) {
    List<T> list = new java.util.ArrayList<>();
    for (T item : iterable) {
      list.add(item);
    }
    return list;
  }
}



