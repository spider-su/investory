package com.example.demo.services;

import com.example.demo.clients.market.TwelveDataService;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.services.models.StockQuote;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@Transactional
public class MarketService {

  public static final Set<String> NOT_SUPPORTED_SYMBOLS = Set.of("CSPX");
  private static final String IBKR_PROVIDER = "IBKR";
  private static final String XTB_PROVIDER = "XTB";
  private static final String XTB_RECONSTRUCTED_COMMENT = "Reconstructed from Cash Operations";

  /**
   * TwelveData free tier allows 8 calls per minute. Group the symbol fetches into chunks of that
   * size and pause between them so we don't trip the rate limit.
   */
  static final int CHUNK_SIZE = 8;

  /** Default inter-chunk pause matching the free-tier rate window. */
  static final long DEFAULT_CHUNK_PAUSE_MS = 120_000L;

  private static final String REMX_UK_SYMBOL = "REMX.UK";
  // TwelveData has only the NYSE REMX quote. Calibrate that proxy to the London
  // REMX.UK share price using the 2026-07-31 closes: 65.97 USD / 12.93 USD.
  private static final double REMX_UK_TWELVE_DATA_DIVISOR = 65.97 / 12.93;
  private static final String VHYD_UK_SYMBOL = "VHYD.UK";
  private static final Map<String, String> TWELVE_DATA_SYMBOL_OVERRIDES =
      Map.of(
          REMX_UK_SYMBOL, "REMX",
          VHYD_UK_SYMBOL, "VHYDl:CBOE");

  private final TwelveDataService twelveDataService;
  private final OpenedPositionRepository openedPositionRepository;
  private final AccountRepository accountRepository;
  private final ClosedPositionRepository closedPositionRepository;
  private final AssetRepository assetRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final AssetPriceHistoryGapFillService assetPriceHistoryGapFillService;
  private final StatisticsRefreshService statisticsRefreshService;
  private final boolean skipNonUsListings;
  private final Set<String> excludedAssetSymbols;
  private final Duration chunkPause;
  private final TransactionTemplate chunkTransactionTemplate;

  public MarketService(
      TwelveDataService twelveDataService,
      OpenedPositionRepository openedPositionRepository,
      AccountRepository accountRepository,
      ClosedPositionRepository closedPositionRepository,
      AssetRepository assetRepository,
      AssetPriceHistoryRepository assetPriceHistoryRepository,
      AssetPriceHistoryGapFillService assetPriceHistoryGapFillService,
      StatisticsRefreshService statisticsRefreshService,
      PlatformTransactionManager transactionManager,
      @Value("${app.market.chunk-pause-ms:" + DEFAULT_CHUNK_PAUSE_MS + "}") long chunkPauseMs,
      @Value("${app.market.skip-non-us-listings:true}") boolean skipNonUsListings,
      @Value("${app.market.excluded-symbols:}") String excludedSymbolsCsv) {
    this.twelveDataService = twelveDataService;
    this.openedPositionRepository = openedPositionRepository;
    this.accountRepository = accountRepository;
    this.closedPositionRepository = closedPositionRepository;
    this.assetRepository = assetRepository;
    this.assetPriceHistoryRepository = assetPriceHistoryRepository;
    this.assetPriceHistoryGapFillService = assetPriceHistoryGapFillService;
    this.statisticsRefreshService = statisticsRefreshService;
    this.skipNonUsListings = skipNonUsListings;
    this.excludedAssetSymbols = parseSymbolSet(excludedSymbolsCsv);
    this.chunkPause = Duration.ofMillis(Math.max(0L, chunkPauseMs));
    this.chunkTransactionTemplate = new TransactionTemplate(transactionManager);
    this.chunkTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void updateStocks() {
    updateStocks(true);
  }

  private void updateStocks(boolean refreshAfterUpdate) {
    log.info("Updating asset prices started");

    refreshAssetActivityFromOpenPositions();

    // Source tickers from assets table; skip unsupported symbols.
    // TwelveData recognizes the app's US-style tickers, not symbols like NVDA.US.
    Map<String, List<Asset>> assetsByTicker =
        assetRepository.findAll().stream()
            .filter(a -> Boolean.TRUE.equals(a.getActive()))
            .filter(a -> !Boolean.TRUE.equals(a.getExcludeFromImport()))
            .filter(this::isSupportedForPriceUpdate)
            .collect(
                Collectors.groupingBy(
                    this::twelveDataSymbol, LinkedHashMap::new, Collectors.toList()));

    // For inactive assets (fully closed positions), keep a meaningful last-known price
    // from the latest closed deal so portfolio views do not show null/zero prices.
    backfillInactiveAssetPricesFromLatestClosedDeals();

    List<Map<String, List<Asset>>> chunks = splitIntoChunks(assetsByTicker, CHUNK_SIZE);
    log.info(
        "Found {} unique tickers across {} assets, divided into {} chunks",
        assetsByTicker.size(),
        assetRepository.count(),
        chunks.size());

    AtomicInteger i = new AtomicInteger(1);
    for (Map<String, List<Asset>> chunk : chunks) {
      int idx = i.getAndIncrement();
      log.info("Updating chunk {} out of {}", idx, chunks.size());
      try {
        runChunkInNewTransaction(chunk);
      } catch (Exception e) {
        log.warn("Chunk {} failed ({}). Retrying tickers individually", idx, e.getMessage());
        chunk.forEach(
            (ticker, assets) -> {
              try {
                runChunkInNewTransaction(Map.of(ticker, assets));
              } catch (Exception ex) {
                log.warn(
                    "Skipping ticker {} after fallback retry failure: {}", ticker, ex.getMessage());
              }
            });
      }
      if (idx < chunks.size() && !chunkPause.isZero()) {
        if (!sleep(chunkPause)) {
          log.warn("Asset price sync interrupted after chunk {}; stopping cleanly", idx);
          refreshStatisticsIfNeeded(refreshAfterUpdate);
          return;
        }
      }
    }
    refreshStatisticsIfNeeded(refreshAfterUpdate);
    log.info("Updating asset prices finished");
  }

  private void runChunkInNewTransaction(Map<String, List<Asset>> chunkByTicker) {
    chunkTransactionTemplate.executeWithoutResult(status -> fetchAndUpsertPrices(chunkByTicker));
  }

  private void backfillInactiveAssetPricesFromLatestClosedDeals() {
    List<Asset> inactiveAssets =
        assetRepository.findAll().stream()
            .filter(asset -> !Boolean.TRUE.equals(asset.getActive()))
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .toList();
    if (inactiveAssets.isEmpty()) {
      return;
    }
    Map<String, ClosedPosition> latestClosedBySymbol =
        closedPositionRepository.findAll().stream()
            .filter(position -> StringUtils.hasText(position.getSymbol()))
            .filter(position -> position.getClosePrice() != null)
            .collect(
                Collectors.toMap(
                    ClosedPosition::getSymbol,
                    position -> position,
                    (left, right) -> {
                      ZonedDateTime leftTime = left.getCloseTime();
                      ZonedDateTime rightTime = right.getCloseTime();
                      if (leftTime == null) {
                        return right;
                      }
                      if (rightTime == null) {
                        return left;
                      }
                      return rightTime.isAfter(leftTime) ? right : left;
                    }));

    List<Asset> backfilled = new ArrayList<>();
    for (Asset asset : inactiveAssets) {
      ClosedPosition latest = latestClosedBySymbol.get(asset.getSymbol());
      if (latest == null) {
        continue;
      }
      asset.setMarketPrice(latest.getClosePrice());
      asset.setMarketPriceUsd(latest.getClosePrice());
      asset.setPriceSource("ClosedPosition");
      asset.setPriceUpdatedAt(
          latest.getCloseTime() != null ? latest.getCloseTime() : ZonedDateTime.now());
      backfilled.add(asset);
    }

    if (!backfilled.isEmpty()) {
      assetRepository.saveAll(backfilled);
      log.info("Backfilled {} inactive asset prices from latest closed deals", backfilled.size());
    }
  }

  private void refreshAssetActivityFromOpenPositions() {
    Set<String> openSymbols =
        openedPositionRepository.findAll().stream()
            .map(OpenedPosition::getSymbol)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

    List<Asset> changed = new ArrayList<>();
    for (Asset asset : assetRepository.findAll()) {
      boolean shouldBeActive = openSymbols.contains(asset.getSymbol());
      if (!Objects.equals(asset.getActive(), shouldBeActive)) {
        asset.setActive(shouldBeActive);
        changed.add(asset);
      }
    }
    if (!changed.isEmpty()) {
      assetRepository.saveAll(changed);
      log.info(
          "Asset activity refresh: {} assets updated ({} open symbols)",
          changed.size(),
          openSymbols.size());
    }
  }

  private void fetchAndUpsertPrices(Map<String, List<Asset>> chunkByTicker) {
    String tickers = String.join(",", chunkByTicker.keySet());
    if (tickers.isEmpty()) {
      return;
    }
    log.info("Fetching quotes for: {}", tickers);
    Map<String, StockQuote> quotes = twelveDataService.fetchStockQuotes(tickers);
    ZonedDateTime now = ZonedDateTime.now();

    List<Asset> toSave = new ArrayList<>();
    chunkByTicker.forEach(
        (ticker, assets) -> {
          StockQuote quote = findQuote(quotes, ticker, assets);
          if (quote == null) {
            return;
          }
          for (Asset asset : assets) {
            double marketPrice = normalizeMarketPrice(asset, quote.getClose());
            asset.setMarketPrice(marketPrice);
            // Legacy UI/export cache only. Reporting selects price and currency from
            // v_current_asset_price, then performs the one required FX conversion.
            asset.setMarketPriceUsd(marketPrice);
            asset.setPriceSource("TwelveData");
            asset.setPriceUpdatedAt(now);
            toSave.add(asset);
            assetPriceHistoryRepository.upsertObservedPrice(
                asset.getId(),
                quoteDate(quote),
                "TWELVE_DATA",
                twelveDataSymbol(asset),
                asset.getSymbol(),
                "TWELVE_DATA_MARKET_CLOSE",
                quoteCurrency(asset, quote),
                BigDecimal.valueOf(marketPrice),
                100,
                "EXACT_LISTING_MARKET_CLOSE");
          }
        });
    assetRepository.saveAll(toSave);
  }

  private LocalDate quoteDate(StockQuote quote) {
    String datetime = quote.getDatetime();
    if (StringUtils.hasText(datetime) && datetime.length() >= 10) {
      try {
        return LocalDate.parse(datetime.substring(0, 10));
      } catch (java.time.format.DateTimeParseException ignored) {
        log.warn("Invalid quote datetime '{}'; using reporting date", datetime);
      }
    }
    return ReportingDateHelper.today();
  }

  private String quoteCurrency(Asset asset, StockQuote quote) {
    if (StringUtils.hasText(quote.getCurrency())) {
      return quote.getCurrency().trim().toUpperCase(Locale.ROOT);
    }
    return asset.getCurrency() != null ? asset.getCurrency().name() : "USD";
  }

  private double normalizeMarketPrice(Asset asset, double quoteClose) {
    if (REMX_UK_SYMBOL.equals(asset.getSymbol())) {
      return quoteClose / REMX_UK_TWELVE_DATA_DIVISOR;
    }
    return quoteClose;
  }

  private String twelveDataSymbol(Asset asset) {
    String override = TWELVE_DATA_SYMBOL_OVERRIDES.get(asset.getSymbol());
    if (override != null) {
      return override;
    }
    String ticker = asset.getTicker();
    if (!StringUtils.hasText(ticker) || !StringUtils.hasText(asset.getSymbol())) {
      return ticker;
    }
    if (asset.getSymbol().endsWith(".PL")) {
      return ticker + ":GPW";
    }
    if (asset.getSymbol().endsWith(".DE")) {
      return ticker + ":XETR";
    }
    if (asset.getSymbol().endsWith(".UK")) {
      return ticker + ":LSE";
    }
    return ticker;
  }

  private StockQuote findQuote(
      Map<String, StockQuote> quotes, String requestKey, List<Asset> assets) {
    StockQuote byRequestKey = quotes.get(requestKey);
    if (byRequestKey != null) {
      return byRequestKey;
    }
    String ticker = requestKey;
    int colon = requestKey.indexOf(':');
    if (colon > 0) {
      ticker = requestKey.substring(0, colon);
    }
    StockQuote byTicker = quotes.get(ticker);
    if (byTicker != null) {
      return byTicker;
    }
    for (Asset asset : assets) {
      StockQuote byAssetTicker = quotes.get(asset.getTicker());
      if (byAssetTicker != null) {
        return byAssetTicker;
      }
    }
    return null;
  }

  private boolean isSupportedForPriceUpdate(Asset asset) {
    if (asset == null || !StringUtils.hasText(asset.getTicker())) {
      return false;
    }
    String ticker = asset.getTicker().trim().toUpperCase(Locale.ROOT);
    if (NOT_SUPPORTED_SYMBOLS.contains(ticker)) {
      return false;
    }
    String symbol = asset.getSymbol();
    if (StringUtils.hasText(symbol)
        && excludedAssetSymbols.contains(symbol.trim().toUpperCase(Locale.ROOT))) {
      return false;
    }
    // REMX.UK has an explicit TwelveData mapping/normalization and must refresh even
    // when generic non-US listings are disabled.
    return REMX_UK_SYMBOL.equals(symbol) || !(skipNonUsListings && isNonUsExchangeSymbol(symbol));
  }

  private static boolean isNonUsExchangeSymbol(String symbol) {
    if (!StringUtils.hasText(symbol)) {
      return false;
    }
    int dot = symbol.lastIndexOf('.');
    if (dot < 0 || dot == symbol.length() - 1) {
      return false;
    }
    return !"US".equals(symbol.substring(dot + 1).trim().toUpperCase(Locale.ROOT));
  }

  private static Set<String> parseSymbolSet(String csv) {
    if (!StringUtils.hasText(csv)) {
      return Set.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .map(symbol -> symbol.toUpperCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * @return {@code true} if the full pause elapsed, {@code false} if interrupted.
   */
  private static boolean sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  static <K, V> List<Map<K, V>> splitIntoChunks(Map<K, V> map, int chunkSize) {
    List<Map<K, V>> chunks = new ArrayList<>();
    Map<K, V> currentChunk = new LinkedHashMap<>();
    int count = 0;

    for (Map.Entry<K, V> entry : map.entrySet()) {
      currentChunk.put(entry.getKey(), entry.getValue());
      count++;

      if (count % chunkSize == 0) {
        chunks.add(currentChunk);
        currentChunk = new LinkedHashMap<>();
      }
    }

    if (!currentChunk.isEmpty()) {
      chunks.add(currentChunk);
    }

    return chunks;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void refreshMarketPricesAndPositions() {
    updateStocks(false);
    syncIbkrPositions();
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void fullPortfolioUpdate() {
    refreshMarketPricesAndPositions();
    statisticsRefreshService.refreshAll();
  }

  /**
   * Applies the latest fetched market prices to IBKR open positions. XTB rows remain imported
   * state; reporting derives their live valuation from the asset price and explicit currencies.
   */
  public void syncIbkrPositions() {
    List<Long> ibkrAccounts =
        accountRepository.findAllByProviderIgnoreCase(IBKR_PROVIDER).stream()
            .map(Account::getId)
            .filter(java.util.Objects::nonNull)
            .toList();
    if (ibkrAccounts.isEmpty()) {
      return;
    }
    List<OpenedPosition> positions = openedPositionRepository.findAllByAccountIn(ibkrAccounts);
    if (positions.isEmpty()) {
      return;
    }
    Set<String> symbols =
        positions.stream()
            .map(OpenedPosition::getSymbol)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
    Map<String, Asset> assetsBySymbol =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .collect(Collectors.toMap(Asset::getSymbol, asset -> asset, (a, b) -> b));

    for (OpenedPosition position : positions) {
      Asset asset = assetsBySymbol.get(position.getSymbol());
      if (asset == null || asset.getMarketPrice() == null || asset.getMarketPrice() == 0.0) {
        continue;
      }
      double openPrice = position.getOpenPrice() != null ? position.getOpenPrice() : 0.0;
      position.setMarketPrice(asset.getMarketPrice());
      if (position.getPriceCurrency() == position.getProfitCurrency()) {
        position.setProfit(position.signedQuantity() * (asset.getMarketPrice() - openPrice));
      } else {
        log.warn(
            "Preserving IBKR position {} profit: price currency {} differs from profit currency {}",
            position.getId(),
            position.getPriceCurrency(),
            position.getProfitCurrency());
      }
    }
    openedPositionRepository.saveAll(positions);

    // Account cash/equity is broker-imported state. Quote refresh must not rewrite it:
    // account statistics will combine broker cash with current market value from assets.
  }

  /** Restores reconstructed XTB open-position profit to its canonical imported value. */
  public int repairXtbReconstructedPositionProfits() {
    List<Long> xtbAccounts =
        accountRepository.findAllByProviderIgnoreCase(XTB_PROVIDER).stream()
            .map(Account::getId)
            .filter(java.util.Objects::nonNull)
            .toList();
    if (xtbAccounts.isEmpty()) {
      return 0;
    }
    List<OpenedPosition> repaired =
        openedPositionRepository.findAllByAccountIn(xtbAccounts).stream()
            .filter(position -> XTB_RECONSTRUCTED_COMMENT.equals(position.getComment()))
            .filter(position -> position.getProfit() == null || position.getProfit() != 0.0)
            .peek(position -> position.setProfit(0.0))
            .toList();
    if (!repaired.isEmpty()) {
      openedPositionRepository.saveAll(repaired);
    }
    return repaired.size();
  }

  private void refreshStatisticsIfNeeded(boolean refreshAfterUpdate) {
    if (refreshAfterUpdate) {
      statisticsRefreshService.refreshAll();
    }
  }
}
