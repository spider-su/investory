package com.smartbox.investory.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.infrastructure.CashOperationType;
import com.smartbox.investory.infrastructure.PositionSettlementModel;
import com.smartbox.investory.infrastructure.PositionType;
import com.smartbox.investory.infrastructure.repository.Asset;
import com.smartbox.investory.infrastructure.repository.AssetPriceHistoryRepository;
import com.smartbox.investory.infrastructure.repository.AssetRepository;
import com.smartbox.investory.infrastructure.repository.CashOperation;
import com.smartbox.investory.infrastructure.repository.CashOperationRepository;
import com.smartbox.investory.infrastructure.repository.ClosedPosition;
import com.smartbox.investory.infrastructure.repository.ClosedPositionRepository;
import com.smartbox.investory.infrastructure.repository.NormalizedCashOperationRepository;
import com.smartbox.investory.infrastructure.repository.OpenedPosition;
import com.smartbox.investory.infrastructure.repository.OpenedPositionRepository;
import com.smartbox.investory.infrastructure.repository.account.Account;
import com.smartbox.investory.infrastructure.repository.account.AccountDaily;
import com.smartbox.investory.infrastructure.repository.account.AccountDailyRepository;
import com.smartbox.investory.infrastructure.repository.account.AccountRepository;
import com.smartbox.investory.services.currency.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioBuilders;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioProjectionServiceTest {
  private final CashOperationNormalizer fallbackNormalizer = new CashOperationNormalizer();

  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private ClosedPositionRepository closedPositionRepository;
  @Mock private CashOperationRepository cashOperationRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private AccountDailyRepository accountDailyRepository;
  @Mock private CurrencyRateService currencyRateService;
  @Mock private NormalizedCashOperationRepository normalizedCashOperationRepository;
  @Mock private AssetPriceHistoryGapFillService assetPriceHistoryGapFillService;

  @InjectMocks private PortfolioProjectionService service;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient().when(accountRepository.findAll()).thenReturn(defaultAccounts());
    org.mockito.Mockito.lenient()
        .when(accountRepository.findAllById(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              java.util.Collection<Long> accountIds = invocation.getArgument(0);
              return accountRepository.findAll().stream()
                  .filter(
                      account -> account.getId() != null && accountIds.contains(account.getId()))
                  .toList();
            });
    org.mockito.Mockito.lenient()
        .when(accountRepository.findPortfolioCurrenciesByAccountIdIn(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              java.util.Collection<Long> accountIds = invocation.getArgument(0);
              return accountRepository.findAll().stream()
                  .filter(
                      account -> account.getId() != null && accountIds.contains(account.getId()))
                  .<AccountRepository.AccountPortfolioCurrencyRow>map(
                      account ->
                          new AccountRepository.AccountPortfolioCurrencyRow() {
                            @Override
                            public Long getAccountId() {
                              return account.getId();
                            }

                            @Override
                            public String getBaseCurrency() {
                              return defaultPortfolioBaseCurrency(account).name();
                            }
                          })
                  .toList();
            });
    org.mockito.Mockito.lenient()
        .when(openedPositionRepository.findAllByAccountIn(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              java.util.Collection<Long> accountIds = invocation.getArgument(0);
              return openedPositionRepository.findAll().stream()
                  .filter(
                      position ->
                          position.getAccount() != null
                              && accountIds.contains(position.getAccount()))
                  .toList();
            });
    org.mockito.Mockito.lenient()
        .when(closedPositionRepository.findAllByAccountIn(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              java.util.Collection<Long> accountIds = invocation.getArgument(0);
              return closedPositionRepository.findAll().stream()
                  .filter(
                      position ->
                          position.getAccount() != null
                              && accountIds.contains(position.getAccount()))
                  .toList();
            });
    org.mockito.Mockito.lenient()
        .when(cashOperationRepository.findAllByAccountIn(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              java.util.Collection<Long> accountIds = invocation.getArgument(0);
              return cashOperationRepository.findAll().stream()
                  .filter(
                      operation ->
                          operation.getAccount() != null
                              && accountIds.contains(operation.getAccount()))
                  .toList();
            });
    org.mockito.Mockito.lenient()
        .when(normalizedCashOperationRepository.findAllDetailedByAccountIdIn(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              java.util.Collection<Long> accountIds = invocation.getArgument(0);
              return fallbackNormalizer
                  .normalize(cashOperationRepository.findAllByAccountIn(accountIds))
                  .stream()
                  .map(
                      normalized ->
                          normalizedRow(
                              normalized.operation().getId(),
                              normalized.operation().getAccount(),
                              accountCurrency(normalized.operation()).name(),
                              normalized.operation().getCurrency() != null
                                  ? normalized.operation().getCurrency().name()
                                  : null,
                              defaultPortfolioBaseCurrency(normalized.operation()).name(),
                              normalized.operation().getType() != null
                                  ? normalized.operation().getType().name()
                                  : null,
                              normalized.normalizedCategory().name(),
                              normalized.operation().getSymbol(),
                              normalized.operation().getAmount(),
                              amountInBaseCurrency(normalized.operation()),
                              normalized.operation().getAmount(),
                              normalized.operation().getComment(),
                              normalized.operation().getDate() != null
                                  ? normalized.operation().getDate().toLocalDate()
                                  : null))
                  .toList();
            });
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
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> accountDailyCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(accountDailyCaptor.capture());
    verify(accountDailyRepository).refreshReportingViews();
    List<AccountDaily> firstRows = toList(accountDailyCaptor.getValue());
    assertFalse(firstRows.isEmpty());

    service.recalculateAll();

    verify(accountDailyRepository, org.mockito.Mockito.times(2))
        .saveAll(accountDailyCaptor.capture());
    verify(accountDailyRepository, org.mockito.Mockito.times(2)).refreshReportingViews();
    List<AccountDaily> secondRows = toList(accountDailyCaptor.getAllValues().get(1));
    assertEquivalentProjectionRows(firstRows, secondRows);
  }

  @Test
  void recalculateAll_releasesLockAfterFailureSoRetryCanSucceed() {
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(accountDailyRepository.refreshReportingViews())
        .thenThrow(new IllegalStateException("refresh failed"))
        .thenReturn(null);

    assertThrows(IllegalStateException.class, service::recalculateAll);
    assertDoesNotThrow(service::recalculateAll);

    verify(accountDailyRepository, org.mockito.Mockito.times(2)).refreshReportingViews();
  }

  @Test
  void recalculateAll_handlesEmptyInputs() {
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());

    service.recalculateAll();

    verify(accountDailyRepository).refreshReportingViews();
    verify(accountDailyRepository, never()).saveAll(anyList());
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
    setCurrencies(opened, CurrencyType.PLN);
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
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(
            List.of(
                historicalPrice(
                    "AAPL.US", LocalDate.now(ZoneId.of("Europe/Warsaw")), 120.0, "USD", 95)));
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

    ArgumentCaptor<Iterable<AccountDaily>> accountDailyCaptor =
        ArgumentCaptor.forClass(Iterable.class);
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
    setCurrencies(opened, CurrencyType.USD);
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
    asset.setCurrency(CurrencyType.USD);
    asset.setMarketPrice(999.0);
    asset.setMarketPriceUsd(999.0);
    asset.setPriceUpdatedAt(tradeDate.plusMonths(1));

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(
            List.of(
                historicalPrice(
                    "AAPL.US", tradeDate.toLocalDate().minusDays(8), 506.70, "USD", 100),
                historicalPrice(
                    "AAPL.US",
                    tradeDate.toLocalDate(),
                    401.95,
                    "USD",
                    10,
                    1.0,
                    "TRADE_OBSERVATION",
                    "TRADE_OBSERVATION")));
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

    assertEquals(4019.5, tradeDay.getMarketValue(), 0.01);
    assertEquals(3019.5, tradeDay.getUnrealizedProfit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_prefersOpenLotObservationWhenTradePricesTie() {
    ZonedDateTime splitDate = ZonedDateTime.parse("2025-11-16T13:51:04Z");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51499241L);
    opened.setSymbol("NFLX.US");
    setCurrencies(opened, CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(10.0);
    opened.setOpenPrice(112.0);
    opened.setPurchaseValue(1120.0);
    opened.setOpenTime(splitDate);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(assetRepository.findAll()).thenReturn(List.of());
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(
            List.of(
                historicalPrice(
                    "NFLX.US",
                    splitDate.toLocalDate(),
                    1181.0,
                    "USD",
                    90,
                    1.0,
                    "XTB_TRADE_CLOSE",
                    "XTB_TRADE_CLOSE_OBSERVATION"),
                historicalPrice(
                    "NFLX.US",
                    splitDate.toLocalDate(),
                    112.0,
                    "USD",
                    90,
                    1.0,
                    "XTB_TRADE_OPEN",
                    "XTB_TRADE_OPEN_OBSERVATION")));
    when(currencyRateService.convertToBaseCurrency(
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily splitDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(splitDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(1120.0, splitDay.getMarketValue(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_treatsCashOnlyTradesAsReportingBoundaryFlows() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");
    long accountId = 51707603L;

    Account cashOnlyAccount = account(accountId, CurrencyType.USD);
    cashOnlyAccount.setCashOnly(true);

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(accountId);
    opened.setSymbol("CSPX.UK");
    setCurrencies(opened, CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(10.0);
    opened.setOpenPrice(100.0);
    opened.setPurchaseValue(1000.0);
    opened.setOpenTime(tradeDate);

    CashOperation deposit = new CashOperation();
    deposit.setAccount(accountId);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(1000.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(tradeDate.minusDays(1));

    CashOperation purchase = new CashOperation();
    purchase.setAccount(accountId);
    purchase.setType(CashOperationType.STOCK_PURCHASE);
    purchase.setSymbol("CSPX.UK");
    purchase.setAmount(-1000.0);
    purchase.setCurrency(CurrencyType.USD);
    purchase.setDate(tradeDate);

    CashOperation sale = new CashOperation();
    sale.setAccount(accountId);
    sale.setType(CashOperationType.STOCK_SELL);
    sale.setSymbol("CSPX.UK");
    sale.setAmount(1100.0);
    sale.setCurrency(CurrencyType.USD);
    sale.setDate(tradeDate.plusDays(1));

    Asset asset = new Asset();
    asset.setSymbol("CSPX.UK");
    asset.setMarketPriceUsd(100.0);

    when(accountRepository.findAll()).thenReturn(List.of(cashOnlyAccount));
    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, purchase, sale));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(List.of(historicalPrice("CSPX.UK", tradeDate.toLocalDate(), 100.0, "USD", 95)));
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
    AccountDaily saleDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(tradeDate.plusDays(1).toLocalDate()))
            .findFirst()
            .orElseThrow();
    AccountDaily currentDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(ReportingDateHelper.today()))
            .findFirst()
            .orElseThrow();

    assertEquals(0.0, tradeDay.getMarketValue(), 0.01);
    assertEquals(1000.0, tradeDay.getWithdrawals(), 0.01);
    assertEquals(0.0, tradeDay.getDailyProfitAmount(), 0.01);
    assertEquals(0.0, saleDay.getMarketValue(), 0.01);
    assertEquals(1100.0, saleDay.getDeposits(), 0.01);
    assertEquals(0.0, saleDay.getDailyProfitAmount(), 0.01);
    assertEquals(1100.0, currentDay.getCashBalance(), 0.01);
    assertEquals(1100.0, currentDay.getEquity(), 0.01);
    assertEquals(0.0, currentDay.getDailyProfitAmount(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_prefersExactMarketCloseOverTradeObservation() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51499241L);
    opened.setSymbol("AAPL.US");
    setCurrencies(opened, CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(10.0);
    opened.setOpenPrice(100.0);
    opened.setPurchaseValue(1000.0);
    opened.setOpenTime(tradeDate);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(assetRepository.findAll()).thenReturn(List.of());
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(
            List.of(
                historicalPrice(
                    "AAPL.US",
                    tradeDate.toLocalDate(),
                    95.0,
                    "USD",
                    90,
                    1.0,
                    "IBKR_TRADE",
                    "IBKR_TRADE_OBSERVATION"),
                historicalPrice(
                    "AAPL.US",
                    tradeDate.toLocalDate(),
                    110.0,
                    "USD",
                    95,
                    1.0,
                    "STOOQ",
                    "EXACT_LISTING_MARKET_CLOSE")));
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
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_appliesPriceScaleFactorExactlyOnce() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51499241L);
    opened.setSymbol("EMIM.UK");
    setCurrencies(opened, CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(100.0);
    opened.setOpenPrice(40.0);
    opened.setPurchaseValue(4000.0);
    opened.setOpenTime(tradeDate);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(assetRepository.findAll()).thenReturn(List.of());
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(
            List.of(
                historicalPrice(
                    "EMIM.UK",
                    tradeDate.toLocalDate(),
                    4.5,
                    "USD",
                    95,
                    10.0,
                    "STOOQ",
                    "EXACT_LISTING_SCALED")));
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

    assertEquals(4500.0, tradeDay.getMarketValue(), 0.01);
    assertEquals(500.0, tradeDay.getUnrealizedProfit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_carriesOpenPositionsForwardToToday() {
    ZonedDateTime tradeDate = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    OpenedPosition opened = new OpenedPosition();
    opened.setAccount(51499241L);
    opened.setSymbol("AAPL.US");
    setCurrencies(opened, CurrencyType.USD);
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
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(
            List.of(
                historicalPrice(
                    "AAPL.US", LocalDate.now(ZoneId.of("Europe/Warsaw")), 120.0, "USD", 95)));
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
    setCurrencies(opened, CurrencyType.USD);
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
  void recalculateAll_usesZeroMarketValueWhenNoHistoricalPriceExists() {
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
    setCurrencies(opened, CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(400.0);
    opened.setOpenPrice(4.7545);
    opened.setPurchaseValue(1901.80);
    opened.setOpenTime(tradeDate);

    Asset asset = new Asset();
    asset.setSymbol("DTLA.UK");

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

    assertEquals(0.0, tradeDay.getMarketValue(), 0.01);
    assertEquals(3098.20, tradeDay.getEquity(), 0.01);
    assertEquals(-1901.80, tradeDay.getUnrealizedProfit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_doesNotReuseStaleHistoricalPriceForPastValuation() {
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
    setCurrencies(opened, CurrencyType.USD);
    opened.setType(PositionType.BUY);
    opened.setVolume(87.0);
    opened.setOpenPrice(2329.205436 / 87.0);
    opened.setPurchaseValue(2329.205436);
    opened.setOpenTime(tradeDate);

    Asset asset = new Asset();
    asset.setSymbol("JGPI.DE");

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, stockPurchase));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(
            List.of(historicalPrice("JGPI.DE", LocalDate.parse("2025-03-03"), 26.65, "USD", 95)));
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

    assertEquals(0.0, tradeDay.getMarketValue(), 0.01);
    assertEquals(2670.794564, tradeDay.getEquity(), 0.01);
    assertEquals(-2329.205436, tradeDay.getUnrealizedProfit(), 0.01);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_carriesForwardStalePriceObservedDuringCurrentHolding() {
    ZonedDateTime depositDate = ZonedDateTime.parse("2025-01-06T12:00:00Z");
    ZonedDateTime openDate = ZonedDateTime.parse("2025-01-07T12:00:00Z");
    ZonedDateTime quoteDate = ZonedDateTime.parse("2025-01-17T12:00:00Z");
    ZonedDateTime closeDate = ZonedDateTime.parse("2025-02-13T12:00:00Z");
    String symbol = "CSPX.UK";

    CashOperation deposit = new CashOperation();
    deposit.setAccount(51499241L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(2500.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(depositDate);

    CashOperation purchase = new CashOperation();
    purchase.setAccount(51499241L);
    purchase.setType(CashOperationType.STOCK_PURCHASE);
    purchase.setAmount(-2500.0);
    purchase.setCurrency(CurrencyType.USD);
    purchase.setDate(openDate);
    purchase.setSymbol(symbol);

    CashOperation sale = new CashOperation();
    sale.setAccount(51499241L);
    sale.setType(CashOperationType.STOCK_SELL);
    sale.setAmount(2600.0);
    sale.setCurrency(CurrencyType.USD);
    sale.setDate(closeDate);
    sale.setSymbol(symbol);

    ClosedPosition closed = new ClosedPosition();
    closed.setId(9001L);
    closed.setAccount(51499241L);
    closed.setSymbol(symbol);
    setCurrencies(closed, CurrencyType.USD);
    closed.setType(PositionType.BUY);
    closed.setVolume(100.0);
    closed.setOpenTime(openDate);
    closed.setCloseTime(closeDate);
    closed.setOpenPrice(25.0);
    closed.setClosePrice(26.0);
    closed.setPurchaseValue(2500.0);
    closed.setSaleValue(2600.0);
    closed.setProfit(100.0);
    closed.setCommission(0.0);
    closed.setSwap(0.0);

    Asset asset = new Asset();
    asset.setSymbol(symbol);

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of(closed));
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, purchase, sale));
    when(assetRepository.findAll()).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(
            List.of(
                historicalPrice(
                    symbol,
                    openDate.toLocalDate(),
                    25.0,
                    "USD",
                    90,
                    1.0,
                    "XTB_TRADE_OPEN",
                    "XTB_TRADE_OPEN_OBSERVATION"),
                historicalPrice(symbol, quoteDate.toLocalDate(), 25.0, "USD", 95),
                historicalPrice(
                    symbol,
                    closeDate.toLocalDate(),
                    26.0,
                    "USD",
                    90,
                    1.0,
                    "XTB_TRADE_CLOSE",
                    "XTB_TRADE_CLOSE_OBSERVATION")));
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

    AccountDaily firstStaleDay =
        rows.stream()
            .filter(row -> row.getDate().equals(LocalDate.of(2025, 1, 28)))
            .findFirst()
            .orElseThrow();
    AccountDaily dayBeforeClose =
        rows.stream()
            .filter(row -> row.getDate().equals(closeDate.minusDays(1).toLocalDate()))
            .findFirst()
            .orElseThrow();
    AccountDaily closeDay =
        rows.stream()
            .filter(row -> row.getDate().equals(closeDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(2500.0, firstStaleDay.getMarketValue(), 0.01);
    assertEquals(0.0, firstStaleDay.getDailyProfitAmount(), 0.01);
    assertEquals(2500.0, dayBeforeClose.getMarketValue(), 0.01);
    assertEquals(0.0, closeDay.getMarketValue(), 0.01);
    assertEquals(100.0, closeDay.getDailyProfitAmount(), 0.01);
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
    setCurrencies(closed, CurrencyType.PLN);
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
  void recalculateAll_doesNotValueResultOnlyCfdAtFullNotional() {
    ZonedDateTime openDate = ZonedDateTime.parse("2026-03-06T17:58:43Z");
    ZonedDateTime closeDate = ZonedDateTime.parse("2026-03-08T21:00:06Z");

    ClosedPosition cfd = new ClosedPosition();
    cfd.setId(2422831730L);
    cfd.setAccount(51499241L);
    cfd.setSymbol("NATGAS");
    setCurrencies(cfd, CurrencyType.USD);
    cfd.setType(PositionType.BUY);
    cfd.setSettlementModel(PositionSettlementModel.RESULT_ONLY);
    cfd.setVolume(0.01);
    cfd.setOpenTime(openDate);
    cfd.setCloseTime(closeDate);
    cfd.setOpenPrice(3.212);
    cfd.setClosePrice(3.347);
    cfd.setMargin(96.36);
    cfd.setProfit(40.50);
    cfd.setSwap(-0.54);
    cfd.setCommission(0.0);

    CashOperation closeResult = new CashOperation();
    closeResult.setId(2422831730L);
    closeResult.setAccount(51499241L);
    closeResult.setType(CashOperationType.CLOSE_TRADE);
    closeResult.setAmount(40.50);
    closeResult.setCurrency(CurrencyType.USD);
    closeResult.setDate(closeDate);
    closeResult.setSymbol("NATGAS");

    CashOperation swap = new CashOperation();
    swap.setId(2422831731L);
    swap.setAccount(51499241L);
    swap.setType(CashOperationType.SWAP);
    swap.setAmount(-0.54);
    swap.setCurrency(CurrencyType.USD);
    swap.setDate(closeDate);
    swap.setSymbol("NATGAS");

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of(cfd));
    when(cashOperationRepository.findAll()).thenReturn(List.of(closeResult, swap));
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> dailyCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(dailyCaptor.capture());
    AccountDaily closeDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getDate().equals(closeDate.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(0.0, closeDay.getMarketValue(), 0.0001);
    assertEquals(0.0, closeDay.getCostBase(), 0.0001);
    assertEquals(39.96, closeDay.getCashBalance(), 0.0001);
    assertEquals(39.96, closeDay.getRealizedProfit(), 0.0001);
    assertEquals(39.96, closeDay.getDailyProfitAmount(), 0.0001);
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
    setCurrencies(januaryHolding, CurrencyType.USD);
    januaryHolding.setType(PositionType.BUY);
    januaryHolding.setVolume(10.0);
    januaryHolding.setOpenPrice(100.0);
    januaryHolding.setPurchaseValue(1000.0);
    januaryHolding.setOpenTime(january);

    ClosedPosition februaryRoundTrip = new ClosedPosition();
    februaryRoundTrip.setAccount(51499241L);
    februaryRoundTrip.setSymbol("MSFT.US");
    setCurrencies(februaryRoundTrip, CurrencyType.USD);
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
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(
            List.of(
                historicalPrice("AAPL.US", january.toLocalDate(), 100.0, "USD", 95),
                historicalPrice("AAPL.US", februaryClose.toLocalDate(), 100.0, "USD", 95),
                historicalPrice("MSFT.US", februaryClose.toLocalDate(), 100.0, "USD", 95)));
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
    setCurrencies(opened, CurrencyType.PLN);
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
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
        .thenReturn(List.of(historicalPrice("PKO.WA", tradeDate.toLocalDate(), 100.0, "PLN", 95)));
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
  void recalculateAll_doesNotInventMarketValueFromCashTradeOnly() {
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
    assertEquals(0.0, tradeDay.getMarketValue(), 0.01);
    assertEquals(100.0, tradeDay.getEquity(), 0.01);
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

    verify(accountDailyRepository).refreshReportingViews();
  }

  @Test
  @SuppressWarnings("unchecked")
  void
      recalculateAll_excludesIbkrBondCallFromAccountStatisticsNetDepositAndDoesNotDoubleCountCash() {
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
    bondPurchase.setSymbol("US91282CKB62");
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
    closedBond.setSymbol("US91282CKB62");
    setCurrencies(closedBond, CurrencyType.USD);
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

    verify(accountDailyRepository).refreshReportingViews();
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

    verify(accountDailyRepository).refreshReportingViews();
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

    assertEquals(0.0, daily.getWithdrawals(), 0.01);
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

    verify(accountDailyRepository).refreshReportingViews();
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

    verify(accountDailyRepository).refreshReportingViews();
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_projectsDailyCashFlowsFromCanonicalLedgerWithoutFeeDoubleCounting() {
    ZonedDateTime day1 = ZonedDateTime.parse("2026-01-10T12:00:00Z");
    ZonedDateTime day2 = ZonedDateTime.parse("2026-01-11T12:00:00Z");

    CashOperation deposit = new CashOperation();
    deposit.setAccount(17959259L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(100.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(day1);

    CashOperation dividend = new CashOperation();
    dividend.setAccount(17959259L);
    dividend.setType(CashOperationType.DIVIDEND);
    dividend.setAmount(4.0);
    dividend.setCurrency(CurrencyType.USD);
    dividend.setDate(day2);
    dividend.setSymbol("AAPL.US");

    CashOperation interest = new CashOperation();
    interest.setAccount(17959259L);
    interest.setType(CashOperationType.FREE_FUNDS_INTEREST);
    interest.setAmount(3.0);
    interest.setCurrency(CurrencyType.USD);
    interest.setDate(day2);

    CashOperation commission = new CashOperation();
    commission.setAccount(17959259L);
    commission.setType(CashOperationType.COMMISSION);
    commission.setAmount(-54.34);
    commission.setCurrency(CurrencyType.USD);
    commission.setDate(day2);

    CashOperation secFee = new CashOperation();
    secFee.setAccount(17959259L);
    secFee.setType(CashOperationType.SEC_FEE);
    secFee.setAmount(-0.03);
    secFee.setCurrency(CurrencyType.USD);
    secFee.setDate(day2);

    CashOperation commissionRefund = new CashOperation();
    commissionRefund.setAccount(17959259L);
    commissionRefund.setType(CashOperationType.CORRECTION);
    commissionRefund.setAmount(10.00);
    commissionRefund.setCurrency(CurrencyType.USD);
    commissionRefund.setDate(day2);
    commissionRefund.setComment("Commission Refund");

    CashOperation secFeeAdjustment = new CashOperation();
    secFeeAdjustment.setAccount(17959259L);
    secFeeAdjustment.setType(CashOperationType.CORRECTION);
    secFeeAdjustment.setAmount(0.01);
    secFeeAdjustment.setCurrency(CurrencyType.USD);
    secFeeAdjustment.setDate(day2);
    secFeeAdjustment.setComment("corr Sec Fee adj");

    CashOperation withholdingTax = new CashOperation();
    withholdingTax.setAccount(17959259L);
    withholdingTax.setType(CashOperationType.WITHHOLDING_TAX);
    withholdingTax.setAmount(-1.25);
    withholdingTax.setCurrency(CurrencyType.USD);
    withholdingTax.setDate(day2);
    withholdingTax.setSymbol("AAPL.US");

    CashOperation withdrawal = new CashOperation();
    withdrawal.setAccount(17959259L);
    withdrawal.setType(CashOperationType.WITHDRAWAL);
    withdrawal.setAmount(-20.0);
    withdrawal.setCurrency(CurrencyType.USD);
    withdrawal.setDate(day2);

    when(accountRepository.findAll()).thenReturn(List.of(account(17959259L, CurrencyType.USD)));
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll())
        .thenReturn(
            List.of(
                deposit,
                dividend,
                interest,
                commission,
                secFee,
                commissionRefund,
                secFeeAdjustment,
                withholdingTax,
                withdrawal));
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
    List<AccountDaily> rows = toList(dailyCaptor.getValue());

    AccountDaily firstDay =
        rows.stream()
            .filter(row -> row.getAccountId().equals(17959259L))
            .filter(row -> row.getDate().equals(day1.toLocalDate()))
            .findFirst()
            .orElseThrow();
    AccountDaily secondDay =
        rows.stream()
            .filter(row -> row.getAccountId().equals(17959259L))
            .filter(row -> row.getDate().equals(day2.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals("USD", secondDay.getValuationCurrency());
    assertEquals(100.0, firstDay.getDeposits(), 0.0001);
    assertEquals(0.0, firstDay.getFees(), 0.0001);

    assertEquals(0.0, secondDay.getDeposits(), 0.0001);
    assertEquals(20.0, secondDay.getWithdrawals(), 0.0001);
    assertEquals(4.0, secondDay.getDividends(), 0.0001);
    assertEquals(3.0, secondDay.getInterest(), 0.0001);
    assertEquals(44.36, secondDay.getFees(), 0.0001);
    assertEquals(1.25, secondDay.getTaxes(), 0.0001);

    double expectedCashDelta =
        dividend.getAmount()
            + interest.getAmount()
            + commission.getAmount()
            + secFee.getAmount()
            + commissionRefund.getAmount()
            + secFeeAdjustment.getAmount()
            + withholdingTax.getAmount()
            + withdrawal.getAmount();
    assertEquals(expectedCashDelta, secondDay.getCashBalance() - firstDay.getCashBalance(), 0.0001);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_projectsSingleFeeRowsExactlyOnce() {
    ZonedDateTime day = ZonedDateTime.parse("2026-01-10T12:00:00Z");

    CashOperation commission = new CashOperation();
    commission.setAccount(17959259L);
    commission.setType(CashOperationType.COMMISSION);
    commission.setAmount(-54.34);
    commission.setCurrency(CurrencyType.USD);
    commission.setDate(day);

    CashOperation secFee = new CashOperation();
    secFee.setAccount(17959259L);
    secFee.setType(CashOperationType.SEC_FEE);
    secFee.setAmount(-0.03);
    secFee.setCurrency(CurrencyType.USD);
    secFee.setDate(day);

    when(accountRepository.findAll()).thenReturn(List.of(account(17959259L, CurrencyType.USD)));
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(commission, secFee));
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
    AccountDaily daily = toList(dailyCaptor.getValue()).getFirst();

    assertEquals(54.37, daily.getFees(), 0.0001);
    assertEquals(-54.37, daily.getCashBalance(), 0.0001);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_usesWarsawReportingDateForLateUtcClose() {
    ZonedDateTime openUtc = ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    ZonedDateTime closeUtc = ZonedDateTime.of(2026, 1, 15, 23, 0, 0, 0, ZoneOffset.UTC);

    ClosedPosition closed = new ClosedPosition();
    closed.setId(91L);
    closed.setAccount(PortfolioTestData.IBKR_USD_ACCOUNT_ID);
    closed.setSymbol("AAPL.US");
    setCurrencies(closed, CurrencyType.USD);
    closed.setType(PositionType.BUY);
    closed.setVolume(1.0);
    closed.setOpenTime(openUtc);
    closed.setCloseTime(closeUtc);
    closed.setPurchaseValue(100.0);
    closed.setSaleValue(110.0);
    closed.setProfit(10.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of(closed));
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> accountDailyCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(accountDailyCaptor.capture());
    List<AccountDaily> rows = toList(accountDailyCaptor.getValue());

    assertEquals(
        LocalDate.of(2026, 1, 16),
        rows.stream()
            .filter(
                row ->
                    row.getRealizedProfit() != null && Math.abs(row.getRealizedProfit()) > 0.0001)
            .findFirst()
            .orElseThrow()
            .getDate());
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_projectsSignedProfitSwapAndCommissionOnCloseDate() {
    ZonedDateTime open = ZonedDateTime.of(2026, 7, 10, 10, 0, 0, 0, ZoneId.of("UTC"));
    ZonedDateTime close = ZonedDateTime.of(2026, 7, 24, 16, 0, 0, 0, ZoneId.of("UTC"));

    ClosedPosition closed = new ClosedPosition();
    closed.setId(101L);
    closed.setAccount(PortfolioTestData.IBKR_USD_ACCOUNT_ID);
    closed.setSymbol("AAPL.US");
    setCurrencies(closed, CurrencyType.USD);
    closed.setType(PositionType.BUY);
    closed.setVolume(2.0);
    closed.setOpenTime(open);
    closed.setCloseTime(close);
    closed.setPurchaseValue(200.0);
    closed.setSaleValue(240.0);
    closed.setProfit(100.0);
    closed.setCommission(-2.0);
    closed.setSwap(-10.0);

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of(closed));
    when(cashOperationRepository.findAll()).thenReturn(List.of());
    when(assetRepository.findAll()).thenReturn(List.of());
    when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recalculateAll();

    ArgumentCaptor<Iterable<AccountDaily>> accountDailyCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(accountDailyRepository).saveAll(accountDailyCaptor.capture());
    List<AccountDaily> rows = toList(accountDailyCaptor.getValue());

    AccountDaily closeDay =
        rows.stream()
            .filter(row -> row.getAccountId().equals(PortfolioTestData.IBKR_USD_ACCOUNT_ID))
            .filter(row -> row.getDate().equals(LocalDate.of(2026, 7, 24)))
            .findFirst()
            .orElseThrow();

    assertEquals(88.0, closeDay.getRealizedProfit(), 0.0001);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_usesEquityBoundaryFormulaForDailyProfit() {
    ZonedDateTime day1 = ZonedDateTime.parse("2026-01-10T12:00:00Z");
    ZonedDateTime day2 = ZonedDateTime.parse("2026-01-11T12:00:00Z");

    CashOperation deposit = new CashOperation();
    deposit.setAccount(17959259L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(100.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(day1);

    CashOperation withdrawal = new CashOperation();
    withdrawal.setAccount(17959259L);
    withdrawal.setType(CashOperationType.WITHDRAWAL);
    withdrawal.setAmount(-20.0);
    withdrawal.setCurrency(CurrencyType.USD);
    withdrawal.setDate(day2);

    when(accountRepository.findAll()).thenReturn(List.of(account(17959259L, CurrencyType.USD)));
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll()).thenReturn(List.of(deposit, withdrawal));
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
    List<AccountDaily> rows = toList(dailyCaptor.getValue());

    AccountDaily firstDay =
        rows.stream()
            .filter(row -> row.getAccountId().equals(17959259L))
            .filter(row -> row.getDate().equals(day1.toLocalDate()))
            .findFirst()
            .orElseThrow();
    AccountDaily secondDay =
        rows.stream()
            .filter(row -> row.getAccountId().equals(17959259L))
            .filter(row -> row.getDate().equals(day2.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(0.0, firstDay.getDailyProfitAmount(), 0.0001);
    assertEquals(0.0, secondDay.getDailyProfitAmount(), 0.0001);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recalculateAll_excludesBookkeepingRebookingFromPerformanceFlow() {
    ZonedDateTime day1 = ZonedDateTime.parse("2026-06-16T12:00:00Z");
    ZonedDateTime day2 = ZonedDateTime.parse("2026-06-17T12:00:00Z");

    CashOperation deposit = new CashOperation();
    deposit.setAccount(17959259L);
    deposit.setType(CashOperationType.DEPOSIT);
    deposit.setAmount(10000.0);
    deposit.setCurrency(CurrencyType.USD);
    deposit.setDate(day1);

    CashOperation bookkeeping = new CashOperation();
    bookkeeping.setAccount(17959259L);
    bookkeeping.setType(CashOperationType.SUBACCOUNT_TRANSFER);
    bookkeeping.setAmount(6044.12);
    bookkeeping.setCurrency(CurrencyType.USD);
    bookkeeping.setComment("Transfer from 51993106 to 17959259");
    bookkeeping.setDate(day2);

    CashOperation rebookedPurchase = new CashOperation();
    rebookedPurchase.setAccount(17959259L);
    rebookedPurchase.setType(CashOperationType.STOCK_PURCHASE);
    rebookedPurchase.setAmount(-6044.12);
    rebookedPurchase.setCurrency(CurrencyType.USD);
    rebookedPurchase.setSymbol("VHYD");
    rebookedPurchase.setDate(day2);

    when(accountRepository.findAll()).thenReturn(List.of(account(17959259L, CurrencyType.USD)));
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(closedPositionRepository.findAll()).thenReturn(List.of());
    when(cashOperationRepository.findAll())
        .thenReturn(List.of(deposit, bookkeeping, rebookedPurchase));
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
    AccountDaily rebookingDay =
        toList(dailyCaptor.getValue()).stream()
            .filter(row -> row.getAccountId().equals(17959259L))
            .filter(row -> row.getDate().equals(day2.toLocalDate()))
            .findFirst()
            .orElseThrow();

    assertEquals(10000.0, rebookingDay.getEquity(), 0.0001);
    assertEquals(0.0, rebookingDay.getDailyProfitAmount(), 0.0001);
  }

  private static AssetPriceHistoryRepository.HistoricalAssetPriceRow historicalPrice(
      String symbol,
      LocalDate priceDate,
      Double closePrice,
      String currency,
      Integer qualityScore) {
    return historicalPrice(
        symbol,
        priceDate,
        closePrice,
        currency,
        qualityScore,
        1.0,
        "STOOQ",
        "EXACT_LISTING_MARKET_CLOSE");
  }

  private static AssetPriceHistoryRepository.HistoricalAssetPriceRow historicalPrice(
      String symbol,
      LocalDate priceDate,
      Double closePrice,
      String currency,
      Integer qualityScore,
      Double scaleFactor,
      String priceOrigin,
      String qualityClass) {
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
      public LocalDate getSourceDate() {
        return priceDate;
      }

      @Override
      public String getSource() {
        return priceOrigin;
      }

      @Override
      public String getSourceSymbol() {
        return symbol;
      }

      @Override
      public String getOriginalSourceSymbol() {
        return symbol;
      }

      @Override
      public BigDecimal getClosePrice() {
        return closePrice == null ? null : BigDecimal.valueOf(closePrice);
      }

      @Override
      public String getPriceCurrency() {
        return currency;
      }

      @Override
      public BigDecimal getPriceScaleFactor() {
        return scaleFactor == null ? null : BigDecimal.valueOf(scaleFactor);
      }

      @Override
      public Integer getQualityScore() {
        return qualityScore;
      }

      @Override
      public String getQualityClass() {
        return qualityClass;
      }

      @Override
      public String getPriceOrigin() {
        return priceOrigin;
      }

      @Override
      public Boolean getEstimated() {
        return false;
      }

      @Override
      public LocalDate getInterpolationLeftDate() {
        return null;
      }

      @Override
      public LocalDate getInterpolationRightDate() {
        return null;
      }
    };
  }

  private static Account account(Long id, CurrencyType currency) {
    Account account = new Account();
    account.setId(id);
    account.setCurrency(currency);
    account.setName(String.valueOf(id));
    account.setPortfolioId(id);
    return account;
  }

  private static List<Account> defaultAccounts() {
    return List.of(
        account(PortfolioTestData.IBKR_USD_ACCOUNT_ID, CurrencyType.USD),
        account(50290466L, CurrencyType.PLN),
        account(51499241L, CurrencyType.USD),
        account(51548444L, CurrencyType.EUR),
        account(PortfolioTestData.POLISH_BONDS_PLN_ACCOUNT_ID, CurrencyType.PLN),
        account(51707603L, CurrencyType.PLN),
        account(51822121L, CurrencyType.USD),
        account(PortfolioTestData.CRYPTO_USD_ACCOUNT_ID, CurrencyType.USD));
  }

  private static CurrencyType defaultPortfolioBaseCurrency(Account account) {
    return CurrencyType.USD;
  }

  private static CurrencyType accountCurrency(CashOperation operation) {
    if (operation == null) {
      return CurrencyType.USD;
    }
    return defaultAccounts().stream()
        .filter(
            account -> account.getId() != null && account.getId().equals(operation.getAccount()))
        .map(Account::getCurrency)
        .findFirst()
        .orElseGet(
            () -> operation.getCurrency() != null ? operation.getCurrency() : CurrencyType.USD);
  }

  private static CurrencyType defaultPortfolioBaseCurrency(CashOperation operation) {
    if (operation == null) {
      return CurrencyType.USD;
    }
    return defaultAccounts().stream()
        .filter(
            account -> account.getId() != null && account.getId().equals(operation.getAccount()))
        .findFirst()
        .map(PortfolioProjectionServiceTest::defaultPortfolioBaseCurrency)
        .orElse(CurrencyType.USD);
  }

  private static <T> List<T> toList(Iterable<T> iterable) {
    List<T> list = new java.util.ArrayList<>();
    for (T item : iterable) {
      list.add(item);
    }
    return list;
  }

  private static void assertEquivalentProjectionRows(
      List<AccountDaily> expected, List<AccountDaily> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      AccountDaily left = expected.get(i);
      AccountDaily right = actual.get(i);
      assertEquals(left.getAccountId(), right.getAccountId());
      assertEquals(left.getDate(), right.getDate());
      assertEquals(left.getValuationCurrency(), right.getValuationCurrency());
      assertEquals(left.getCashBalanceValue(), right.getCashBalanceValue());
      assertEquals(left.getMarketValueValue(), right.getMarketValueValue());
      assertEquals(left.getEquityValue(), right.getEquityValue());
      assertEquals(left.getUnrealizedProfitValue(), right.getUnrealizedProfitValue());
      assertEquals(left.getCostBaseValue(), right.getCostBaseValue());
      assertEquals(left.getRealizedProfitValue(), right.getRealizedProfitValue());
      assertEquals(left.getDividendsValue(), right.getDividendsValue());
      assertEquals(left.getInterestValue(), right.getInterestValue());
      assertEquals(left.getFeesValue(), right.getFeesValue());
      assertEquals(left.getTaxesValue(), right.getTaxesValue());
      assertEquals(left.getDepositsValue(), right.getDepositsValue());
      assertEquals(left.getWithdrawalsValue(), right.getWithdrawalsValue());
      assertEquals(left.getDailyProfitAmountValue(), right.getDailyProfitAmountValue());
      assertEquals(left.getDailyReturnValue(), right.getDailyReturnValue());
      assertEquals(left.getPortfolioWeightValue(), right.getPortfolioWeightValue());
    }
  }

  private static NormalizedCashOperationRepository.NormalizedCashOperationRow normalizedRow(
      Long operationId,
      Long accountId,
      String accountCurrency,
      String currency,
      String baseCurrency,
      String rawOperation,
      String normalizedCategory,
      String symbol,
      Double amount,
      Double amountInBaseCurrency,
      Double amountInAccountCurrency,
      String comment,
      LocalDate date) {
    return new NormalizedCashOperationRepository.NormalizedCashOperationRow() {
      @Override
      public Long getOperationId() {
        return operationId;
      }

      @Override
      public Long getAccountId() {
        return accountId;
      }

      @Override
      public String getAccountCurrency() {
        return accountCurrency;
      }

      @Override
      public String getCurrency() {
        return currency;
      }

      @Override
      public String getBaseCurrency() {
        return baseCurrency;
      }

      @Override
      public String getRawOperation() {
        return rawOperation;
      }

      @Override
      public String getNormalizedCategory() {
        return normalizedCategory;
      }

      @Override
      public String getSymbol() {
        return symbol;
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
        return amountInAccountCurrency;
      }

      @Override
      public String getAccountConversionStatus() {
        return "OK";
      }

      @Override
      public String getComment() {
        return comment;
      }

      @Override
      public LocalDate getDate() {
        return date;
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

  private static void setCurrencies(OpenedPosition position, CurrencyType currency) {
    position.setPriceCurrency(currency);
    position.setCostCurrency(currency);
    position.setProfitCurrency(currency);
    position.setCommissionCurrency(currency);
  }

  private static void setCurrencies(ClosedPosition position, CurrencyType currency) {
    position.setPriceCurrency(currency);
    position.setCostCurrency(currency);
    position.setProfitCurrency(currency);
    position.setCommissionCurrency(currency);
  }

  private Double amountInBaseCurrency(CashOperation operation) {
    if (operation == null || operation.getAmount() == null) {
      return 0.0;
    }
    String comment = operation.getComment();
    if (comment != null) {
      java.util.regex.Matcher ibkrMatcher =
          java.util.regex.Pattern.compile(
                  "net amount in base from forex trade:\\s*([-+]?[0-9]+(?:[\\.,][0-9]+)?)\\s+[A-Z]{3}\\.[A-Z]{3}",
                  java.util.regex.Pattern.CASE_INSENSITIVE)
              .matcher(comment);
      if (ibkrMatcher.find()) {
        return Double.parseDouble(ibkrMatcher.group(1).replace(',', '.'));
      }
      java.util.regex.Matcher xtbMatcher =
          java.util.regex.Pattern.compile(
                  "currency conversion,\\s*([A-Z]{3})\\s+to\\s+([A-Z]{3}).*?exchange rate:\\s*([0-9]+(?:[\\.,][0-9]+)?)",
                  java.util.regex.Pattern.CASE_INSENSITIVE)
              .matcher(comment);
      if (xtbMatcher.find() && operation.getCurrency() != null) {
        CurrencyType source = CurrencyType.valueOf(xtbMatcher.group(1).toUpperCase());
        CurrencyType target = CurrencyType.valueOf(xtbMatcher.group(2).toUpperCase());
        double rate = Double.parseDouble(xtbMatcher.group(3).replace(',', '.'));
        if (target == CurrencyType.USD && operation.getCurrency() == source) {
          return operation.getAmount() * rate;
        }
        if (target == CurrencyType.USD && operation.getCurrency() == target) {
          return operation.getAmount();
        }
        if (source == CurrencyType.USD && operation.getCurrency() == target) {
          return operation.getAmount() / rate;
        }
        if (source == CurrencyType.USD && operation.getCurrency() == source) {
          return operation.getAmount();
        }
      }
    }
    return operation.getAmount();
  }
}
