package com.smartbox.investory.investment.market.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.accounting.model.StockQuote;
import com.smartbox.investory.investment.infrastructure.market.client.TwelveDataService;
import com.smartbox.investory.investment.infrastructure.market.client.YahooFinanceService;
import com.smartbox.investory.investment.infrastructure.market.client.YahooFinanceService.YahooQuote;
import com.smartbox.investory.investment.infrastructure.persistence.*;
import com.smartbox.investory.investment.infrastructure.persistence.account.Account;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.reporting.StatisticsRefreshService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

  @Mock private TwelveDataService twelveDataService;
  @Mock private YahooFinanceService yahooFinanceService;
  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private ClosedPositionRepository closedPositionRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private AssetPriceHistoryGapFillService assetPriceHistoryGapFillService;
  @Mock private StatisticsRefreshService statisticsRefreshService;
  @Mock private PlatformTransactionManager transactionManager;
  @Captor private ArgumentCaptor<Iterable<Asset>> assetIterableCaptor;

  private MarketService marketService;

  @BeforeEach
  void setUp() {
    // chunkPauseMs=0 keeps the chunked sync synchronous so we don't need
    // the daemon-thread + Thread.interrupt() dance the old test used.
    marketService = marketService(true, "");

    org.mockito.Mockito.lenient().when(assetRepository.findAll()).thenReturn(List.of());
    org.mockito.Mockito.lenient().when(openedPositionRepository.findAll()).thenReturn(List.of());
    org.mockito.Mockito.lenient().when(closedPositionRepository.findAll()).thenReturn(List.of());
  }

  @Test
  void splitIntoChunks_dividesMapEvenly() {
    LinkedHashMap<Integer, Integer> input = new LinkedHashMap<>();
    for (int i = 0; i < 10; i++) {
      input.put(i, i);
    }

    List<Map<Integer, Integer>> chunks = MarketService.splitIntoChunks(input, 3);

    assertEquals(4, chunks.size());
    assertEquals(3, chunks.get(0).size());
    assertEquals(3, chunks.get(1).size());
    assertEquals(3, chunks.get(2).size());
    assertEquals(1, chunks.get(3).size());
  }

  @Test
  void splitIntoChunks_returnsEmptyListForEmptyMap() {
    assertEquals(0, MarketService.splitIntoChunks(new LinkedHashMap<>(), 5).size());
  }

  @Test
  void syncIbkrPositions_appliesMarketPriceWithoutChangingAccountCashOrEquity() {
    OpenedPosition ibkr = new OpenedPosition();
    ibkr.setSymbol("AAPL");
    ibkr.setAccount(17959259L);
    ibkr.setPriceCurrency(CurrencyType.USD);
    ibkr.setCostCurrency(CurrencyType.USD);
    ibkr.setProfitCurrency(CurrencyType.USD);
    ibkr.setCommissionCurrency(CurrencyType.USD);
    ibkr.setVolume(10.0);
    ibkr.setOpenPrice(150.0);

    Asset price = newAsset("AAPL", "AAPL", true);
    price.setMarketPrice(180.0);

    when(accountRepository.findAllByProviderIgnoreCase("IBKR"))
        .thenReturn(List.of(account(17959259L, "IBKR")));
    when(openedPositionRepository.findAllByAccountIn(List.of(17959259L))).thenReturn(List.of(ibkr));
    when(assetRepository.findAllBySymbolIn(java.util.Set.of("AAPL"))).thenReturn(List.of(price));

    marketService.syncIbkrPositions();

    assertEquals(180.0, ibkr.getMarketPrice());
    assertEquals(300.0, ibkr.getProfit(), 0.01); // 10 * (180 - 150)
    verify(openedPositionRepository).saveAll(List.of(ibkr));
  }

  @Test
  void syncIbkrPositions_isNoopWhenNoPositions() {
    when(accountRepository.findAllByProviderIgnoreCase("IBKR"))
        .thenReturn(List.of(account(17959259L, "IBKR")));
    when(openedPositionRepository.findAllByAccountIn(List.of(17959259L))).thenReturn(List.of());

    marketService.syncIbkrPositions();

    verify(openedPositionRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void repairXtbReconstructedPositionProfitsResetsOnlyXtbReconstructedRows() {
    OpenedPosition xtb = new OpenedPosition();
    xtb.setAccount(51551301L);
    xtb.setComment("Reconstructed from Cash Operations");
    xtb.setProfit(243.94);
    OpenedPosition secondXtb = new OpenedPosition();
    secondXtb.setAccount(51499241L);
    secondXtb.setComment("Reconstructed from Cash Operations");
    secondXtb.setProfit(-12.5);
    OpenedPosition ibkr = new OpenedPosition();
    ibkr.setAccount(17959259L);
    ibkr.setComment("IBKR position snapshot");
    ibkr.setProfit(243.94);

    when(accountRepository.findAllByProviderIgnoreCase("XTB"))
        .thenReturn(List.of(account(51551301L, "XTB"), account(51499241L, "XTB")));
    when(openedPositionRepository.findAllByAccountIn(List.of(51551301L, 51499241L)))
        .thenReturn(List.of(xtb, secondXtb));

    assertEquals(2, marketService.repairXtbReconstructedPositionProfits());
    assertEquals(0.0, xtb.getProfit());
    assertEquals(0.0, secondXtb.getProfit());
    assertEquals(243.94, ibkr.getProfit());
    verify(openedPositionRepository).saveAll(List.of(xtb, secondXtb));
  }

  @Test
  void syncIbkrPositionsPreservesProfitWhenCurrenciesDiffer() {
    OpenedPosition ibkr = new OpenedPosition();
    ibkr.setId(42L);
    ibkr.setSymbol("GOOGL.US");
    ibkr.setAccount(17959259L);
    ibkr.setPriceCurrency(CurrencyType.USD);
    ibkr.setProfitCurrency(CurrencyType.PLN);
    ibkr.setVolume(2.0);
    ibkr.setOpenPrice(300.0);
    ibkr.setProfit(17.0);
    Asset price = newAsset("GOOGL.US", "GOOGL", true);
    price.setMarketPrice(320.0);

    when(accountRepository.findAllByProviderIgnoreCase("IBKR"))
        .thenReturn(List.of(account(17959259L, "IBKR")));
    when(openedPositionRepository.findAllByAccountIn(List.of(17959259L))).thenReturn(List.of(ibkr));
    when(assetRepository.findAllBySymbolIn(java.util.Set.of("GOOGL.US")))
        .thenReturn(List.of(price));

    marketService.syncIbkrPositions();

    assertEquals(320.0, ibkr.getMarketPrice());
    assertEquals(17.0, ibkr.getProfit());
  }

  @Test
  void updateStocks_skipsUnsupportedSymbolsAndPersistsQuoteData() {
    Asset supported = newAsset("AAPL.US", "AAPL", true);
    Asset unsupported = newAsset("CSPX.UK", "CSPX", true);
    when(assetRepository.findAll()).thenReturn(List.of(unsupported, supported));
    when(openedPositionRepository.findAll())
        .thenReturn(List.of(openPosition("AAPL.US"), openPosition("CSPX.UK")));

    StockQuote quote = new StockQuote();
    quote.setSymbol("AAPL");
    quote.setClose(110.0);
    quote.setOpen(108.0);
    quote.setCurrency("USD");
    quote.setDatetime("2026-07-31");
    // Single-chunk fetch must contain only the supported ticker.
    when(twelveDataService.fetchStockQuotes("AAPL")).thenReturn(Map.of("AAPL", quote));

    marketService.updateStocks();

    verify(twelveDataService, times(1)).fetchStockQuotes("AAPL");
    verify(assetRepository, times(1)).saveAll(org.mockito.ArgumentMatchers.any());
    verify(assetPriceHistoryRepository)
        .upsertObservedPrice(
            supported.getId(),
            java.time.LocalDate.of(2026, 7, 31),
            "TWELVE_DATA",
            "AAPL",
            "AAPL.US",
            "TWELVE_DATA_MARKET_CLOSE",
            "USD",
            BigDecimal.valueOf(110.0),
            100,
            "EXACT_LISTING_MARKET_CLOSE");
    verify(statisticsRefreshService).refreshAll();
  }

  @Test
  void updateStocks_skipsQuotesUpdatedWithinFourHours() {
    Asset recent = newAsset("AAPL.US", "AAPL", true);
    recent.setPriceSource("TwelveData");
    recent.setPriceUpdatedAt(java.time.ZonedDateTime.now().minusHours(3));
    when(assetRepository.findAll()).thenReturn(List.of(recent));
    when(openedPositionRepository.findAll()).thenReturn(List.of(openPosition("AAPL.US")));

    marketService.updateStocks();

    verify(twelveDataService, never()).fetchStockQuotes(anyString());
    verify(assetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    verify(statisticsRefreshService).refreshAll();
    verify(yahooFinanceService, never()).fetchLatestQuote(anyString());
  }

  @Test
  void updateStocksUsesYahooFallbackWhenTwelveDataHasNoQuote() {
    Asset vwra = newAsset("VWRA.UK", "VWRA", true);
    when(assetRepository.findAll()).thenReturn(List.of(vwra));
    when(openedPositionRepository.findAll()).thenReturn(List.of(openPosition("VWRA.UK")));
    when(yahooFinanceService.fetchLatestQuote("VWRA.L"))
        .thenReturn(
            Optional.of(
                new YahooQuote("VWRA.L", "USD", java.time.LocalDate.of(2026, 8, 12), 194.80)));

    marketService.updateStocks();

    verify(twelveDataService, never()).fetchStockQuotes(anyString());
    verify(yahooFinanceService).fetchLatestQuote("VWRA.L");
    assertEquals(194.80, vwra.getMarketPrice(), 0.00000001);
    assertEquals("YahooFinance", vwra.getPriceSource());
    verify(assetPriceHistoryRepository)
        .upsertObservedPrice(
            vwra.getId(),
            java.time.LocalDate.of(2026, 8, 12),
            "YAHOO_FINANCE",
            "VWRA.L",
            "VWRA.UK",
            "YAHOO_FINANCE_MARKET_CLOSE",
            "USD",
            BigDecimal.valueOf(194.80),
            100,
            "EXACT_LISTING_MARKET_CLOSE");
  }

  @Test
  void updateStocksDerivesYahooExchangeSuffixesForFallback() {
    Asset etfbw20tr = newAsset("ETFBW20TR.PL", "ETFBW20TR", true);
    etfbw20tr.setCurrency(CurrencyType.PLN);
    when(assetRepository.findAll()).thenReturn(List.of(etfbw20tr));
    when(openedPositionRepository.findAll()).thenReturn(List.of(openPosition("ETFBW20TR.PL")));
    when(yahooFinanceService.fetchLatestQuote("ETFBW20TR.WA")).thenReturn(Optional.empty());

    marketService.updateStocks();

    verify(yahooFinanceService).fetchLatestQuote("ETFBW20TR.WA");
  }

  @Test
  void updateStocksRefreshesRecentImportedPrice() {
    Asset recentImport = newAsset("AAPL.US", "AAPL", true);
    recentImport.setPriceSource("XTB");
    recentImport.setPriceUpdatedAt(java.time.ZonedDateTime.now().minusHours(3));
    when(assetRepository.findAll()).thenReturn(List.of(recentImport));
    when(openedPositionRepository.findAll()).thenReturn(List.of(openPosition("AAPL.US")));

    StockQuote quote = new StockQuote();
    quote.setClose(110.0);
    when(twelveDataService.fetchStockQuotes("AAPL")).thenReturn(Map.of("AAPL", quote));

    marketService.updateStocks();

    verify(twelveDataService).fetchStockQuotes("AAPL");
  }

  @Test
  void updateStocksAppliesManualRemxUkPriceAdjustment() {
    marketService = marketService(false, "");
    Asset remx = newAsset("REMX.UK", "REMX", true);
    when(assetRepository.findAll()).thenReturn(List.of(remx));
    when(openedPositionRepository.findAll()).thenReturn(List.of(openPosition("REMX.UK")));

    StockQuote quote = new StockQuote();
    quote.setSymbol("REMX");
    quote.setClose(65.97);
    quote.setCurrency("USD");
    when(twelveDataService.fetchStockQuotes("REMX")).thenReturn(Map.of("REMX", quote));

    marketService.updateStocks();

    verify(twelveDataService).fetchStockQuotes("REMX");
    verify(assetRepository).saveAll(assetIterableCaptor.capture());
    List<Asset> saved = toList(assetIterableCaptor.getValue());
    assertEquals(1, saved.size());
    assertEquals("REMX.UK", saved.get(0).getSymbol());
    assertEquals(12.93, saved.get(0).getMarketPrice(), 0.00000001);
    verify(assetPriceHistoryRepository)
        .upsertObservedPrice(
            eq(remx.getId()),
            any(java.time.LocalDate.class),
            eq("TWELVE_DATA"),
            eq("REMX"),
            eq("REMX.UK"),
            eq("TWELVE_DATA_MARKET_CLOSE"),
            eq("USD"),
            eq(BigDecimal.valueOf(12.93)),
            eq(100),
            eq("EXACT_LISTING_MARKET_CLOSE"));
  }

  @Test
  void updateStocksSkipsNonUsListingsBeforeHttpCallByDefault() {
    Asset jgpi = newAsset("JGPI.DE", "JGPI", true);
    Asset cdr = newAsset("CDR.PL", "CDR", true);
    Asset sgld = newAsset("SGLD.UK", "SGLD", true);
    Asset vhyd = newAsset("VHYD.UK", "VHYD", true);
    when(assetRepository.findAll()).thenReturn(List.of(jgpi, cdr, sgld, vhyd));
    when(openedPositionRepository.findAll())
        .thenReturn(
            List.of(
                openPosition("JGPI.DE"),
                openPosition("CDR.PL"),
                openPosition("SGLD.UK"),
                openPosition("VHYD.UK")));

    marketService.updateStocks();

    verify(twelveDataService, never()).fetchStockQuotes(anyString());
    verify(statisticsRefreshService).refreshAll();
  }

  @Test
  void updateStocksUsesExchangeQualifiedSymbolsForNonUsListingsWhenEnabled() {
    marketService = marketService(false, "");
    Asset jgpi = newAsset("JGPI.DE", "JGPI", true);
    Asset cdr = newAsset("CDR.PL", "CDR", true);
    Asset sgld = newAsset("SGLD.UK", "SGLD", true);
    Asset vhyd = newAsset("VHYD.UK", "VHYD", true);
    when(assetRepository.findAll()).thenReturn(List.of(jgpi, cdr, sgld, vhyd));
    when(openedPositionRepository.findAll())
        .thenReturn(
            List.of(
                openPosition("JGPI.DE"),
                openPosition("CDR.PL"),
                openPosition("SGLD.UK"),
                openPosition("VHYD.UK")));

    StockQuote quote = new StockQuote();
    quote.setClose(100.0);
    when(twelveDataService.fetchStockQuotes("JGPI:XETR,CDR:GPW,SGLD:LSE,VHYDl:CBOE"))
        .thenReturn(
            Map.of(
                "JGPI:XETR", quote,
                "CDR:GPW", quote,
                "SGLD:LSE", quote,
                "VHYDl:CBOE", quote));

    marketService.updateStocks();

    verify(twelveDataService).fetchStockQuotes("JGPI:XETR,CDR:GPW,SGLD:LSE,VHYDl:CBOE");
  }

  @Test
  void updateStocksSkipsConfiguredExcludedSymbolsBeforeHttpCall() {
    marketService = marketService(false, "AAPL.US");
    Asset aapl = newAsset("AAPL.US", "AAPL", true);
    when(assetRepository.findAll()).thenReturn(List.of(aapl));
    when(openedPositionRepository.findAll()).thenReturn(List.of(openPosition("AAPL.US")));

    marketService.updateStocks();

    verify(twelveDataService, never()).fetchStockQuotes(anyString());
    verify(statisticsRefreshService).refreshAll();
  }

  @Test
  void updateStocksSkipsAssetsMarkedExcludedFromImportBeforeHttpCall() {
    marketService = marketService(false, "");
    Asset excluded = newAsset("AIGI.UK", "AIGI", true);
    excluded.setExcludeFromImport(true);
    when(assetRepository.findAll()).thenReturn(List.of(excluded));
    when(openedPositionRepository.findAll()).thenReturn(List.of(openPosition("AIGI.UK")));

    marketService.updateStocks();

    verify(twelveDataService, never()).fetchStockQuotes(anyString());
    verify(statisticsRefreshService).refreshAll();
  }

  @Test
  void updateStocks_continuesWhenFetchFailsAndLogsTheChunk() {
    Asset a = newAsset("A.US", "A", true);
    Asset b = newAsset("B.US", "B", true);
    when(assetRepository.findAll()).thenReturn(List.of(a, b));
    when(openedPositionRepository.findAll())
        .thenReturn(List.of(openPosition("A.US"), openPosition("B.US")));
    when(twelveDataService.fetchStockQuotes(anyString()))
        .thenThrow(new RuntimeException("rate limit"));

    // Must not throw: a failing chunk is logged and the sync continues to the next one.
    marketService.updateStocks();

    // 1 failed chunk request + 2 per-symbol fallback retries.
    verify(twelveDataService, times(3)).fetchStockQuotes(anyString());
    verify(assetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updateStocks_fallsBackToSingleTickerRequestsWhenChunkFails() {
    Asset a = newAsset("A.US", "A", true);
    Asset b = newAsset("B.US", "B", true);
    when(assetRepository.findAll()).thenReturn(List.of(a, b));
    when(openedPositionRepository.findAll())
        .thenReturn(List.of(openPosition("A.US"), openPosition("B.US")));
    when(twelveDataService.fetchStockQuotes("A,B")).thenThrow(new RuntimeException("chunk failed"));

    StockQuote quoteA = new StockQuote();
    quoteA.setSymbol("A");
    quoteA.setOpen(101.0);
    quoteA.setClose(105.0);
    when(twelveDataService.fetchStockQuotes("A")).thenReturn(Map.of("A", quoteA));
    when(twelveDataService.fetchStockQuotes("B")).thenThrow(new RuntimeException("single failed"));

    marketService.updateStocks();

    verify(assetRepository, times(1)).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updateStocks_skipsHttpCallWhenAllSymbolsAreUnsupported() {
    Asset only = newAsset("CSPX.UK", "CSPX", true);
    when(assetRepository.findAll()).thenReturn(List.of(only));
    when(openedPositionRepository.findAll()).thenReturn(List.of(openPosition("CSPX.UK")));

    marketService.updateStocks();

    verify(twelveDataService, never()).fetchStockQuotes(anyString());
    verify(statisticsRefreshService).refreshAll();
  }

  @Test
  void fullPortfolioUpdate_refreshesStatisticsAfterSync() {
    when(assetRepository.findAll()).thenReturn(List.of());
    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(accountRepository.findAllByProviderIgnoreCase("IBKR"))
        .thenReturn(List.of(account(17959259L, "IBKR")));
    when(openedPositionRepository.findAllByAccountIn(List.of(17959259L))).thenReturn(List.of());

    marketService.fullPortfolioUpdate();

    verify(statisticsRefreshService, times(1)).refreshAll();
  }

  private static Asset newAsset(String symbol, String ticker, boolean active) {
    Asset asset = new Asset();
    asset.setSymbol(symbol);
    asset.setTicker(ticker);
    asset.setActive(active);
    asset.setCurrency(CurrencyType.USD);
    return asset;
  }

  private MarketService marketService(boolean skipNonUsListings, String excludedSymbolsCsv) {
    return new MarketService(
        twelveDataService,
        yahooFinanceService,
        openedPositionRepository,
        accountRepository,
        closedPositionRepository,
        assetRepository,
        assetPriceHistoryRepository,
        assetPriceHistoryGapFillService,
        statisticsRefreshService,
        transactionManager,
        0L,
        skipNonUsListings,
        excludedSymbolsCsv);
  }

  private static OpenedPosition openPosition(String symbol) {
    OpenedPosition position = new OpenedPosition();
    position.setSymbol(symbol);
    return position;
  }

  private static Account account(long id, String provider) {
    Account account = new Account();
    account.setId(id);
    account.setProvider(provider);
    return account;
  }

  private static <T> List<T> toList(Iterable<T> iterable) {
    java.util.ArrayList<T> list = new java.util.ArrayList<>();
    iterable.forEach(list::add);
    return list;
  }
}
