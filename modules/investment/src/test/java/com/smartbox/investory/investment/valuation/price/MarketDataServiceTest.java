package com.smartbox.investory.investment.valuation.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.port.market.MarketDataProvider;
import com.smartbox.investory.investment.port.market.MarketDataProvider.LatestQuote;
import com.smartbox.investory.investment.port.market.MarketQuote;
import com.smartbox.investory.investment.projection.StatisticsRefreshService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.time.ClockApplicationTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("Market Service")
class MarketDataServiceTest {

  @Mock private MarketDataProvider marketDataProvider;
  @Mock private PositionRepository positionRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private AssetPriceHistoryGapFillService assetPriceHistoryGapFillService;
  @Mock private CurrencyRateService currencyRateService;
  @Mock private StatisticsRefreshService statisticsRefreshService;
  @Mock private PlatformTransactionManager transactionManager;
  @Captor private ArgumentCaptor<Iterable<AssetEntity>> assetIterableCaptor;

  private MarketDataService marketDataService;

  @BeforeEach
  void setUp() {
    // chunkPauseMs=0 keeps the chunked sync synchronous so we don't need
    // the daemon-thread + Thread.interrupt() dance the old test used.
    marketDataService = marketDataService(true, "");

    org.mockito.Mockito.lenient()
        .when(marketDataProvider.externalSymbol(any(), any()))
        .thenAnswer(
            invocation -> {
              String symbol = invocation.getArgument(0);
              String ticker = invocation.getArgument(1);
              if ("VHYD.UK".equals(symbol)) return "VHYDl:CBOE";
              if (symbol != null && symbol.endsWith(".PL")) return ticker + ":GPW";
              if (symbol != null && symbol.endsWith(".DE")) return ticker + ":XETR";
              if (symbol != null && symbol.endsWith(".UK")) return ticker + ":LSE";
              return ticker;
            });

    org.mockito.Mockito.lenient().when(assetRepository.findAll()).thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllByActiveTrueAndExcludeFromImportFalse())
        .thenAnswer(invocation -> assetRepository.findAll());
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllByActiveFalseAndSymbolIsNotNull())
        .thenAnswer(invocation -> assetRepository.findAll());
    org.mockito.Mockito.lenient().when(positionRepository.findOpen()).thenReturn(List.of());
    org.mockito.Mockito.lenient().when(positionRepository.findClosed()).thenReturn(List.of());
    // updateStocks resolves the portfolio's open positions through the scoped
    // repository methods. Keep the common fixture aligned with that contract;
    // the individual tests still control the source data via findAll/findOpen.
    org.mockito.Mockito.lenient()
        .when(accountRepository.findAllByPortfolioId(any()))
        .thenAnswer(invocation -> accountRepository.findAll());
    org.mockito.Mockito.lenient()
        .when(positionRepository.findOpenByAccountIn(any()))
        .thenAnswer(invocation -> positionRepository.findOpen());
  }

  @DisplayName("split Into Chunks divides Map Evenly")
  @Test
  void splitIntoChunks_dividesMapEvenly() {
    LinkedHashMap<Integer, Integer> input = new LinkedHashMap<>();
    for (int i = 0; i < 10; i++) {
      input.put(i, i);
    }

    List<Map<Integer, Integer>> chunks = MarketDataService.splitIntoChunks(input, 3);

    assertEquals(4, chunks.size());
    assertEquals(3, chunks.get(0).size());
    assertEquals(3, chunks.get(1).size());
    assertEquals(3, chunks.get(2).size());
    assertEquals(1, chunks.get(3).size());
  }

  @DisplayName("split Into Chunks returns Empty List For Empty Map")
  @Test
  void splitIntoChunks_returnsEmptyListForEmptyMap() {
    assertEquals(0, MarketDataService.splitIntoChunks(new LinkedHashMap<>(), 5).size());
  }

  @DisplayName("sync Ibkr Positions applies Market Price Without Changing Account Cash Or Equity")
  @Test
  void syncIbkrPositions_appliesMarketPriceWithoutChangingAccountCashOrEquity() {
    PositionEntity ibkr = new PositionEntity();
    ibkr.setSymbol("AAPL");
    ibkr.setAccount(17959259L);
    ibkr.setPriceCurrency(CurrencyType.USD);
    ibkr.setCostCurrency(CurrencyType.USD);
    ibkr.setProfitCurrency(CurrencyType.USD);
    ibkr.setCommissionCurrency(CurrencyType.USD);
    ibkr.setVolume(java.math.BigDecimal.valueOf(10.0));
    ibkr.setOpenPrice(java.math.BigDecimal.valueOf(150.0));

    AssetEntity price = newAsset("AAPL", "AAPL", true);
    price.setMarketPrice(java.math.BigDecimal.valueOf(180.0));

    when(accountRepository.findAllByProviderIgnoreCase("IBKR"))
        .thenReturn(List.of(account(17959259L, "IBKR")));
    when(positionRepository.findOpenByAccountIn(List.of(17959259L))).thenReturn(List.of(ibkr));
    when(assetRepository.findAllBySymbolIn(java.util.Set.of("AAPL"))).thenReturn(List.of(price));

    marketDataService.syncIbkrPositions();

    assertEquals(180.0, ibkr.getMarketPrice().doubleValue());
    assertEquals(300.0, ibkr.getProfit().doubleValue(), 0.01); // 10 * (180 - 150)
    verify(positionRepository).saveAll(List.of(ibkr));
  }

  @DisplayName("sync Ibkr Positions is Noop When No Positions")
  @Test
  void syncIbkrPositions_isNoopWhenNoPositions() {
    when(accountRepository.findAllByProviderIgnoreCase("IBKR"))
        .thenReturn(List.of(account(17959259L, "IBKR")));
    when(positionRepository.findOpenByAccountIn(List.of(17959259L))).thenReturn(List.of());

    marketDataService.syncIbkrPositions();

    verify(positionRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @DisplayName("repair Xtb Reconstructed Position Profits Resets Only Xtb Reconstructed Rows")
  @Test
  void repairXtbReconstructedPositionProfitsResetsOnlyXtbReconstructedRows() {
    PositionEntity xtb = new PositionEntity();
    xtb.setAccount(51551301L);
    xtb.setComment("Reconstructed from Cash Operations");
    xtb.setProfit(java.math.BigDecimal.valueOf(243.94));
    PositionEntity secondXtb = new PositionEntity();
    secondXtb.setAccount(51499241L);
    secondXtb.setComment("Reconstructed from Cash Operations");
    secondXtb.setProfit(java.math.BigDecimal.valueOf(-12.5));
    PositionEntity ibkr = new PositionEntity();
    ibkr.setAccount(17959259L);
    ibkr.setComment("IBKR position snapshot");
    ibkr.setProfit(java.math.BigDecimal.valueOf(243.94));

    when(accountRepository.findAllByProviderIgnoreCase("XTB"))
        .thenReturn(List.of(account(51551301L, "XTB"), account(51499241L, "XTB")));
    when(positionRepository.findOpenByAccountIn(List.of(51551301L, 51499241L)))
        .thenReturn(List.of(xtb, secondXtb));

    assertEquals(2, marketDataService.repairXtbReconstructedPositionProfits());
    assertEquals(0.0, xtb.getProfit().doubleValue());
    assertEquals(0.0, secondXtb.getProfit().doubleValue());
    assertEquals(243.94, ibkr.getProfit().doubleValue());
    verify(positionRepository).saveAll(List.of(xtb, secondXtb));
  }

  @DisplayName("sync Ibkr Positions Preserves Profit When Currencies Differ")
  @Test
  void syncIbkrPositionsPreservesProfitWhenCurrenciesDiffer() {
    PositionEntity ibkr = new PositionEntity();
    ibkr.setId(42L);
    ibkr.setSymbol("GOOGL.US");
    ibkr.setAccount(17959259L);
    ibkr.setPriceCurrency(CurrencyType.USD);
    ibkr.setProfitCurrency(CurrencyType.PLN);
    ibkr.setVolume(java.math.BigDecimal.valueOf(2.0));
    ibkr.setOpenPrice(java.math.BigDecimal.valueOf(300.0));
    ibkr.setProfit(java.math.BigDecimal.valueOf(17.0));
    AssetEntity price = newAsset("GOOGL.US", "GOOGL", true);
    price.setMarketPrice(java.math.BigDecimal.valueOf(320.0));

    when(accountRepository.findAllByProviderIgnoreCase("IBKR"))
        .thenReturn(List.of(account(17959259L, "IBKR")));
    when(positionRepository.findOpenByAccountIn(List.of(17959259L))).thenReturn(List.of(ibkr));
    when(assetRepository.findAllBySymbolIn(java.util.Set.of("GOOGL.US")))
        .thenReturn(List.of(price));

    marketDataService.syncIbkrPositions();

    assertEquals(320.0, ibkr.getMarketPrice().doubleValue());
    assertEquals(17.0, ibkr.getProfit().doubleValue());
  }

  @DisplayName("update Stocks skips Unsupported Symbols And Persists Quote Data")
  @Test
  void updateStocks_skipsUnsupportedSymbolsAndPersistsQuoteData() {
    AssetEntity supported = newAsset("AAPL.US", "AAPL", true);
    AssetEntity unsupported = newAsset("CSPX.UK", "CSPX", true);
    when(assetRepository.findAll()).thenReturn(List.of(unsupported, supported));
    when(positionRepository.findOpen())
        .thenReturn(List.of(openPosition("AAPL.US"), openPosition("CSPX.UK")));

    MarketQuote quote = new MarketQuote();
    quote.setSymbol("AAPL");
    quote.setClose(110.0);
    quote.setOpen(108.0);
    quote.setCurrency("USD");
    quote.setDatetime("2026-07-31");
    // Single-chunk fetch must contain only the supported ticker.
    when(marketDataProvider.fetchQuotes(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider, times(1)).fetchQuotes(List.of("AAPL"));
    verify(assetRepository, times(1)).saveAll(org.mockito.ArgumentMatchers.any());
    verify(assetPriceHistoryRepository)
        .upsertObservedPrice(
            supported.getId(),
            java.time.LocalDate.of(2026, 7, 31),
            "YAHOO_FINANCE",
            "AAPL",
            "AAPL.US",
            "YAHOO_FINANCE_MARKET_CLOSE",
            "USD",
            BigDecimal.valueOf(110.0),
            100,
            "EXACT_LISTING_MARKET_CLOSE");
    verify(statisticsRefreshService).refreshAll();
  }

  @DisplayName("update Stocks skips Direct Bond Quote")
  @Test
  void updateStocks_skipsDirectBondQuote() {
    AssetEntity bond = newAsset("US91282CKB62", "US91282CKB62", true);
    bond.setAssetType("BOND");
    when(assetRepository.findAll()).thenReturn(List.of(bond));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("US91282CKB62")));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider, never()).fetchQuotes(any());
    verify(marketDataProvider, never()).fetchLatestQuote(anyString());
    verify(assetPriceHistoryRepository, never())
        .upsertObservedPrice(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @DisplayName("update Stocks skips Quotes Updated Within Four Hours")
  @Test
  void updateStocks_skipsQuotesUpdatedWithinFourHours() {
    AssetEntity recent = newAsset("AAPL.US", "AAPL", true);
    recent.setPriceSource("TwelveData");
    recent.setPriceUpdatedAt(java.time.ZonedDateTime.now().minusHours(3));
    when(assetRepository.findAll()).thenReturn(List.of(recent));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("AAPL.US")));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider, never()).fetchQuotes(any());
    verify(assetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    verify(statisticsRefreshService).refreshAll();
    verify(marketDataProvider, never()).fetchLatestQuote(anyString());
  }

  @DisplayName("update Stocks Uses Yahoo Fallback When Twelve Data Has No Quote")
  @Test
  void updateStocksUsesYahooFallbackWhenTwelveDataHasNoQuote() {
    AssetEntity vwra = newAsset("VWRA.UK", "VWRA", true);
    when(assetRepository.findAll()).thenReturn(List.of(vwra));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("VWRA.UK")));
    when(marketDataProvider.fetchLatestQuote("VWRA.L"))
        .thenReturn(
            Optional.of(
                new LatestQuote("VWRA.L", "USD", java.time.LocalDate.of(2026, 8, 12), 194.80)));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider, never()).fetchQuotes(any());
    verify(marketDataProvider).fetchLatestQuote("VWRA.L");
    assertEquals(194.80, vwra.getMarketPrice().doubleValue(), 0.00000001);
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

  @DisplayName("update Stocks Converts Native Quote Into Legacy Usd Cache")
  @Test
  void updateStocksConvertsNativeQuoteIntoLegacyUsdCache() {
    AssetEntity supported = newAsset("CDR.PL", "CDR", true);
    supported.setCurrency(CurrencyType.PLN);
    when(assetRepository.findAll()).thenReturn(List.of(supported));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("CDR.PL")));

    MarketQuote quote = new MarketQuote();
    quote.setSymbol("CDR");
    quote.setClose(160.0);
    quote.setCurrency("PLN");
    quote.setDatetime("2026-08-24");
    when(marketDataProvider.fetchQuotes(List.of("CDR:GPW"))).thenReturn(Map.of("CDR:GPW", quote));
    when(currencyRateService.convertToBaseCurrency(
            BigDecimal.valueOf(160.0),
            CurrencyType.USD,
            CurrencyType.PLN,
            java.time.LocalDate.of(2026, 8, 24)))
        .thenReturn(BigDecimal.valueOf(40.0));

    marketDataService(false, "").updateStocks(1L);

    assertEquals(160.0, supported.getMarketPrice().doubleValue(), 0.00000001);
    assertEquals(40.0, supported.getMarketPriceUsd().doubleValue(), 0.00000001);
  }

  @DisplayName("update Stocks Converts Inactive Close Price Into Legacy Usd Cache")
  @Test
  void updateStocksConvertsInactiveClosePriceIntoLegacyUsdCache() {
    AssetEntity inactive = newAsset("CDR.PL", "CDR", false);
    inactive.setCurrency(CurrencyType.PLN);
    PositionEntity latest = new PositionEntity();
    latest.setSymbol("CDR.PL");
    latest.setClosePrice(java.math.BigDecimal.valueOf(160.0));
    latest.setPriceCurrency(CurrencyType.PLN);
    latest.setCloseTime(java.time.ZonedDateTime.parse("2026-08-24T16:00:00+02:00[Europe/Warsaw]"));
    when(assetRepository.findAll()).thenReturn(List.of(inactive));
    when(positionRepository.findClosed()).thenReturn(List.of(latest));
    when(currencyRateService.convertToBaseCurrency(
            BigDecimal.valueOf(160.0),
            CurrencyType.USD,
            CurrencyType.PLN,
            java.time.LocalDate.of(2026, 8, 24)))
        .thenReturn(BigDecimal.valueOf(40.0));

    marketDataService.updateStocks(1L);

    assertEquals(160.0, inactive.getMarketPrice().doubleValue(), 0.00000001);
    assertEquals(40.0, inactive.getMarketPriceUsd().doubleValue(), 0.00000001);
  }

  @DisplayName("update Stocks Reports Yahoo Failure After Refreshing Persisted Projections")
  @Test
  void updateStocksReportsYahooFailureAfterRefreshingPersistedProjections() {
    AssetEntity vwra = newAsset("VWRA.UK", "VWRA", true);
    when(assetRepository.findAll()).thenReturn(List.of(vwra));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("VWRA.UK")));
    when(marketDataProvider.fetchLatestQuote("VWRA.L"))
        .thenThrow(new IllegalStateException("network unavailable"));

    assertThrows(IllegalStateException.class, () -> marketDataService.updateStocks(1L));

    verify(statisticsRefreshService).refreshAll();
  }

  @DisplayName("update Stocks Derives Yahoo Exchange Suffixes For Fallback")
  @Test
  void updateStocksDerivesYahooExchangeSuffixesForFallback() {
    AssetEntity etfbw20tr = newAsset("ETFBW20TR.PL", "ETFBW20TR", true);
    etfbw20tr.setCurrency(CurrencyType.PLN);
    when(assetRepository.findAll()).thenReturn(List.of(etfbw20tr));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("ETFBW20TR.PL")));
    when(marketDataProvider.fetchLatestQuote("ETFBW20TR.WA")).thenReturn(Optional.empty());

    marketDataService.updateStocks(1L);

    verify(marketDataProvider).fetchLatestQuote("ETFBW20TR.WA");
  }

  @DisplayName("update Stocks Refreshes Recent Imported Price")
  @Test
  void updateStocksRefreshesRecentImportedPrice() {
    AssetEntity recentImport = newAsset("AAPL.US", "AAPL", true);
    recentImport.setPriceSource("XTB");
    recentImport.setPriceUpdatedAt(java.time.ZonedDateTime.now().minusHours(3));
    when(assetRepository.findAll()).thenReturn(List.of(recentImport));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("AAPL.US")));

    MarketQuote quote = new MarketQuote();
    quote.setClose(110.0);
    quote.setCurrency("USD");
    when(marketDataProvider.fetchQuotes(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider).fetchQuotes(List.of("AAPL"));
  }

  @DisplayName("update Stocks Uses Exact Yahoo Listing For Remx Uk")
  @Test
  void updateStocksUsesExactYahooListingForRemxUk() {
    AssetEntity remx = newAsset("REMX.UK", "REMX", true);
    when(assetRepository.findAll()).thenReturn(List.of(remx));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("REMX.UK")));
    when(marketDataProvider.fetchLatestQuote("REMX.L"))
        .thenReturn(
            Optional.of(
                new LatestQuote("REMX.L", "USD", java.time.LocalDate.of(2026, 8, 24), 12.93)));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider, never()).fetchQuotes(any());
    verify(marketDataProvider).fetchLatestQuote("REMX.L");
    verify(assetRepository).saveAll(assetIterableCaptor.capture());
    List<AssetEntity> saved = toList(assetIterableCaptor.getValue());
    assertEquals(1, saved.size());
    assertEquals("REMX.UK", saved.get(0).getSymbol());
    assertEquals(12.93, saved.get(0).getMarketPrice().doubleValue(), 0.00000001);
    verify(assetPriceHistoryRepository)
        .upsertObservedPrice(
            eq(remx.getId()),
            eq(java.time.LocalDate.of(2026, 8, 24)),
            eq("YAHOO_FINANCE"),
            eq("REMX.L"),
            eq("REMX.UK"),
            eq("YAHOO_FINANCE_MARKET_CLOSE"),
            eq("USD"),
            eq(BigDecimal.valueOf(12.93)),
            eq(100),
            eq("EXACT_LISTING_MARKET_CLOSE"));
  }

  @DisplayName("update Stocks Skips Non Us Listings Before Http Call By Default")
  @Test
  void updateStocksSkipsNonUsListingsBeforeHttpCallByDefault() {
    AssetEntity jgpi = newAsset("JGPI.DE", "JGPI", true);
    AssetEntity cdr = newAsset("CDR.PL", "CDR", true);
    AssetEntity sgld = newAsset("SGLD.UK", "SGLD", true);
    AssetEntity vhyd = newAsset("VHYD.UK", "VHYD", true);
    when(assetRepository.findAll()).thenReturn(List.of(jgpi, cdr, sgld, vhyd));
    when(positionRepository.findOpen())
        .thenReturn(
            List.of(
                openPosition("JGPI.DE"),
                openPosition("CDR.PL"),
                openPosition("SGLD.UK"),
                openPosition("VHYD.UK")));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider, never()).fetchQuotes(any());
    verify(statisticsRefreshService).refreshAll();
  }

  @DisplayName("update Stocks Uses Exchange Qualified Symbols For Non Us Listings When Enabled")
  @Test
  void updateStocksUsesExchangeQualifiedSymbolsForNonUsListingsWhenEnabled() {
    marketDataService = marketDataService(false, "");
    AssetEntity jgpi = newAsset("JGPI.DE", "JGPI", true);
    AssetEntity cdr = newAsset("CDR.PL", "CDR", true);
    AssetEntity sgld = newAsset("SGLD.UK", "SGLD", true);
    AssetEntity vhyd = newAsset("VHYD.UK", "VHYD", true);
    when(assetRepository.findAll()).thenReturn(List.of(jgpi, cdr, sgld, vhyd));
    when(positionRepository.findOpen())
        .thenReturn(
            List.of(
                openPosition("JGPI.DE"),
                openPosition("CDR.PL"),
                openPosition("SGLD.UK"),
                openPosition("VHYD.UK")));

    MarketQuote quote = new MarketQuote();
    quote.setClose(100.0);
    quote.setCurrency("USD");
    when(marketDataProvider.fetchQuotes(List.of("JGPI:XETR", "CDR:GPW", "SGLD:LSE", "VHYDl:CBOE")))
        .thenReturn(
            Map.of(
                "JGPI:XETR", quote,
                "CDR:GPW", quote,
                "SGLD:LSE", quote,
                "VHYDl:CBOE", quote));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider)
        .fetchQuotes(List.of("JGPI:XETR", "CDR:GPW", "SGLD:LSE", "VHYDl:CBOE"));
  }

  @DisplayName("update Stocks Skips Configured Excluded Symbols Before Http Call")
  @Test
  void updateStocksSkipsConfiguredExcludedSymbolsBeforeHttpCall() {
    marketDataService = marketDataService(false, "AAPL.US");
    AssetEntity aapl = newAsset("AAPL.US", "AAPL", true);
    when(assetRepository.findAll()).thenReturn(List.of(aapl));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("AAPL.US")));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider, never()).fetchQuotes(any());
    verify(statisticsRefreshService).refreshAll();
  }

  @DisplayName("update Stocks Skips Assets Marked Excluded From Import Before Http Call")
  @Test
  void updateStocksSkipsAssetsMarkedExcludedFromImportBeforeHttpCall() {
    marketDataService = marketDataService(false, "");
    AssetEntity excluded = newAsset("AIGI.UK", "AIGI", true);
    excluded.setExcludeFromImport(true);
    when(assetRepository.findAll()).thenReturn(List.of(excluded));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("AIGI.UK")));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider, never()).fetchQuotes(any());
    verify(statisticsRefreshService).refreshAll();
  }

  @DisplayName("update Stocks continues When Fetch Fails And Logs The Chunk")
  @Test
  void updateStocks_continuesWhenFetchFailsAndLogsTheChunk() {
    AssetEntity a = newAsset("A.US", "A", true);
    AssetEntity b = newAsset("B.US", "B", true);
    when(assetRepository.findAll()).thenReturn(List.of(a, b));
    when(positionRepository.findOpen())
        .thenReturn(List.of(openPosition("A.US"), openPosition("B.US")));
    when(marketDataProvider.fetchQuotes(any())).thenThrow(new RuntimeException("rate limit"));

    assertThrows(IllegalStateException.class, () -> marketDataService.updateStocks(1L));

    // 1 failed chunk request + 2 per-symbol fallback retries.
    verify(marketDataProvider, times(3)).fetchQuotes(any());
    verify(assetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @DisplayName("update Stocks falls Back To Single Ticker Requests When Chunk Fails")
  @Test
  void updateStocks_fallsBackToSingleTickerRequestsWhenChunkFails() {
    AssetEntity a = newAsset("A.US", "A", true);
    AssetEntity b = newAsset("B.US", "B", true);
    when(assetRepository.findAll()).thenReturn(List.of(a, b));
    when(positionRepository.findOpen())
        .thenReturn(List.of(openPosition("A.US"), openPosition("B.US")));
    when(marketDataProvider.fetchQuotes(List.of("A", "B")))
        .thenThrow(new RuntimeException("chunk failed"));

    MarketQuote quoteA = new MarketQuote();
    quoteA.setSymbol("A");
    quoteA.setOpen(101.0);
    quoteA.setClose(105.0);
    quoteA.setCurrency("USD");
    when(marketDataProvider.fetchQuotes(List.of("A"))).thenReturn(Map.of("A", quoteA));
    when(marketDataProvider.fetchQuotes(List.of("B")))
        .thenThrow(new RuntimeException("single failed"));

    assertThrows(IllegalStateException.class, () -> marketDataService.updateStocks(1L));

    verify(assetRepository, times(1)).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @DisplayName("update Stocks Rejects Quote Currency Mismatch And Uses Yahoo Fallback")
  @Test
  void updateStocksRejectsQuoteCurrencyMismatchAndUsesYahooFallback() {
    AssetEntity aapl = newAsset("AAPL.US", "AAPL", true);
    aapl.setMarketPrice(java.math.BigDecimal.valueOf(100.0));
    when(assetRepository.findAll()).thenReturn(List.of(aapl));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("AAPL.US")));

    MarketQuote quote = new MarketQuote();
    quote.setSymbol("AAPL");
    quote.setClose(110.0);
    quote.setCurrency("EUR");
    when(marketDataProvider.fetchQuotes(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote));
    when(marketDataProvider.fetchLatestQuote("AAPL")).thenReturn(Optional.empty());

    marketDataService.updateStocks(1L);

    assertEquals(100.0, aapl.getMarketPrice().doubleValue());
    verify(marketDataProvider).fetchLatestQuote("AAPL");
    verify(assetPriceHistoryRepository, never())
        .upsertObservedPrice(
            any(),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            anyString());
  }

  @DisplayName("update Stocks Propagates Interruption As Failure")
  @Test
  void updateStocksPropagatesInterruptionAsFailure() throws Exception {
    marketDataService = marketDataService(true, "", 10_000L);
    List<AssetEntity> assets = new java.util.ArrayList<>();
    List<PositionEntity> positions = new java.util.ArrayList<>();
    Map<String, MarketQuote> firstChunk = new LinkedHashMap<>();
    for (int index = 0; index < 9; index++) {
      String ticker = "A" + index;
      assets.add(newAsset(ticker + ".US", ticker, true));
      positions.add(openPosition(ticker + ".US"));
      if (index < 8) {
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(ticker);
        quote.setClose(100.0 + index);
        quote.setCurrency("USD");
        firstChunk.put(ticker, quote);
      }
    }
    when(assetRepository.findAll()).thenReturn(assets);
    when(positionRepository.findOpen()).thenReturn(positions);
    CountDownLatch firstChunkFetched = new CountDownLatch(1);
    when(marketDataProvider.fetchQuotes(List.of("A0", "A1", "A2", "A3", "A4", "A5", "A6", "A7")))
        .thenAnswer(
            invocation -> {
              firstChunkFetched.countDown();
              return firstChunk;
            });

    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
    Thread refresh =
        new Thread(
            () -> {
              try {
                marketDataService.updateStocks(1L);
              } catch (Throwable throwable) {
                failure.set(throwable);
                interrupted.set(Thread.currentThread().isInterrupted());
              }
            });
    refresh.start();
    assertTrue(firstChunkFetched.await(2, TimeUnit.SECONDS));
    refresh.interrupt();
    refresh.join(2_000L);

    assertTrue(failure.get() instanceof IllegalStateException);
    assertTrue(failure.get().getMessage().contains("interrupted"));
    assertTrue(interrupted.get());
    verify(statisticsRefreshService).refreshAll();
  }

  @DisplayName("update Stocks skips Http Call When All Symbols Are Unsupported")
  @Test
  void updateStocks_skipsHttpCallWhenAllSymbolsAreUnsupported() {
    AssetEntity only = newAsset("CSPX.UK", "CSPX", true);
    when(assetRepository.findAll()).thenReturn(List.of(only));
    when(positionRepository.findOpen()).thenReturn(List.of(openPosition("CSPX.UK")));

    marketDataService.updateStocks(1L);

    verify(marketDataProvider, never()).fetchQuotes(any());
    verify(statisticsRefreshService).refreshAll();
  }

  @DisplayName("full Portfolio Update refreshes Current Market Views After Sync")
  @Test
  void fullPortfolioUpdate_refreshesStatisticsAfterSync() {
    when(assetRepository.findAll()).thenReturn(List.of());
    when(positionRepository.findOpen()).thenReturn(List.of());
    marketDataService.fullPortfolioUpdate(1L);

    verify(statisticsRefreshService, times(1)).refreshCurrentMarketPrices();
  }

  private static AssetEntity newAsset(String symbol, String ticker, boolean active) {
    AssetEntity asset = new AssetEntity();
    asset.setSymbol(symbol);
    asset.setTicker(ticker);
    asset.setActive(active);
    asset.setCurrency(CurrencyType.USD);
    return asset;
  }

  private MarketDataService marketDataService(
      boolean skipNonUsListings, String excludedSymbolsCsv) {
    return marketDataService(skipNonUsListings, excludedSymbolsCsv, 0L);
  }

  private MarketDataService marketDataService(
      boolean skipNonUsListings, String excludedSymbolsCsv, long chunkPauseMs) {
    return new MarketDataService(
        marketDataProvider,
        positionRepository,
        accountRepository,
        assetRepository,
        assetPriceHistoryRepository,
        assetPriceHistoryGapFillService,
        currencyRateService,
        statisticsRefreshService,
        transactionManager,
        new ClockApplicationTime(
            Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC),
            ZoneId.of("Europe/Warsaw")),
        chunkPauseMs,
        skipNonUsListings,
        excludedSymbolsCsv);
  }

  private static PositionEntity openPosition(String symbol) {
    PositionEntity position = new PositionEntity();
    position.setSymbol(symbol);
    return position;
  }

  private static AccountEntity account(long id, String provider) {
    AccountEntity account = new AccountEntity();
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
