package com.smartbox.investory.investment.valuation.price;

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
import com.smartbox.investory.investment.reporting.ReportingDateHelper;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.fx.FxRateUnavailableException;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
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
public class MarketDataService {

  public static final Set<String> NOT_SUPPORTED_SYMBOLS = Set.of("CSPX");
  private static final BigDecimal PERCENT_OF_PAR_MULTIPLIER = new BigDecimal("0.01");
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

  static final Duration QUOTE_FRESHNESS = Duration.ofHours(4);

  private final MarketDataProvider marketDataProvider;

  private final PositionRepository positionRepository;
  private final AccountRepository accountRepository;
  private final AssetRepository assetRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final AssetPriceHistoryGapFillService assetPriceHistoryGapFillService;
  private final CurrencyRateService currencyRateService;
  private final StatisticsRefreshService statisticsRefreshService;
  private final boolean skipNonUsListings;
  private final Set<String> excludedAssetSymbols;
  private final Duration chunkPause;
  private final TransactionTemplate chunkTransactionTemplate;

  public MarketDataService(
      MarketDataProvider marketDataProvider,
      PositionRepository positionRepository,
      AccountRepository accountRepository,
      AssetRepository assetRepository,
      AssetPriceHistoryRepository assetPriceHistoryRepository,
      AssetPriceHistoryGapFillService assetPriceHistoryGapFillService,
      CurrencyRateService currencyRateService,
      StatisticsRefreshService statisticsRefreshService,
      PlatformTransactionManager transactionManager,
      @Value("${app.market.chunk-pause-ms:" + DEFAULT_CHUNK_PAUSE_MS + "}") long chunkPauseMs,
      @Value("${app.market.skip-non-us-listings:true}") boolean skipNonUsListings,
      @Value("${app.market.excluded-symbols:}") String excludedSymbolsCsv) {
    this.marketDataProvider = marketDataProvider;
    this.positionRepository = positionRepository;
    this.accountRepository = accountRepository;
    this.assetRepository = assetRepository;
    this.assetPriceHistoryRepository = assetPriceHistoryRepository;
    this.assetPriceHistoryGapFillService = assetPriceHistoryGapFillService;
    this.currencyRateService = currencyRateService;
    this.statisticsRefreshService = statisticsRefreshService;
    this.skipNonUsListings = skipNonUsListings;
    this.excludedAssetSymbols = parseSymbolSet(excludedSymbolsCsv);
    this.chunkPause = Duration.ofMillis(Math.max(0L, chunkPauseMs));
    this.chunkTransactionTemplate = new TransactionTemplate(transactionManager);
    this.chunkTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void updateStocks(Long portfolioId) {
    requirePortfolioId(portfolioId);
    updateStocks(portfolioId, true);
  }

  private void updateStocks(Long portfolioId, boolean refreshAfterUpdate) {
    log.info("Updating asset prices started");
    List<String> refreshFailures = new ArrayList<>();

    refreshAssetActivityFromOpenPositions();

    Set<String> portfolioOpenSymbols =
        accountRepository.findAllByPortfolioId(portfolioId).stream()
            .map(AccountEntity::getId)
            .filter(Objects::nonNull)
            .collect(
                Collectors.collectingAndThen(
                    Collectors.toList(), positionRepository::findOpenByAccountIn))
            .stream()
            .map(PositionEntity::getSymbol)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

    List<AssetEntity> activeAssets =
        assetRepository.findAllByActiveTrueAndExcludeFromImportFalse().stream()
            .filter(asset -> portfolioOpenSymbols.contains(asset.getSymbol()))
            .filter(this::isNotConfiguredExcluded)
            .toList();

    // Source tickers from assets table; skip unsupported Twelve Data symbols.
    // TwelveData recognizes the app's US-style tickers, not symbols like NVDA.US.
    Map<String, List<AssetEntity>> assetsByTicker =
        activeAssets.stream()
            .filter(this::isSupportedForPriceUpdate)
            .collect(
                Collectors.groupingBy(
                    this::twelveDataSymbol, LinkedHashMap::new, Collectors.toList()));
    ZonedDateTime quoteFreshnessCutoff = ZonedDateTime.now().minus(QUOTE_FRESHNESS);
    assetsByTicker
        .entrySet()
        .removeIf(
            entry ->
                entry.getValue().stream()
                    .allMatch(asset -> isQuoteFresh(asset, quoteFreshnessCutoff)));

    // For inactive assets (fully closed positions), keep a meaningful last-known price
    // from the latest closed deal so portfolio views do not show null/zero prices.
    backfillInactiveAssetPricesFromLatestClosedDeals();

    List<Map<String, List<AssetEntity>>> chunks = splitIntoChunks(assetsByTicker, CHUNK_SIZE);
    log.info(
        "Found {} unique tickers across {} assets, divided into {} chunks",
        assetsByTicker.size(),
        assetRepository.count(),
        chunks.size());

    AtomicInteger i = new AtomicInteger(1);
    Set<Long> twelveDataUpdatedAssetIds = new java.util.HashSet<>();
    for (Map<String, List<AssetEntity>> chunk : chunks) {
      int idx = i.getAndIncrement();
      log.info("Updating chunk {} out of {}", idx, chunks.size());
      try {
        twelveDataUpdatedAssetIds.addAll(runChunkInNewTransaction(chunk));
      } catch (Exception e) {
        log.warn("Chunk {} failed ({}). Retrying tickers individually", idx, e.getMessage());
        chunk.forEach(
            (ticker, assets) -> {
              try {
                twelveDataUpdatedAssetIds.addAll(runChunkInNewTransaction(Map.of(ticker, assets)));
              } catch (Exception ex) {
                refreshFailures.add(ticker + ": " + ex.getMessage());
                log.warn(
                    "Skipping ticker {} after fallback retry failure: {}", ticker, ex.getMessage());
              }
            });
      }
      if (idx < chunks.size() && !chunkPause.isZero()) {
        if (!sleep(chunkPause)) {
          log.warn("AssetEntity price sync interrupted after chunk {}; reporting incomplete", idx);
          refreshStatisticsIfNeeded(refreshAfterUpdate);
          throw new IllegalStateException(
              "Market refresh interrupted after chunk " + idx + " of " + chunks.size());
        }
      }
    }
    List<AssetEntity> yahooFallbackAssets =
        activeAssets.stream()
            .filter(asset -> !isQuoteFresh(asset, quoteFreshnessCutoff))
            .filter(asset -> !twelveDataUpdatedAssetIds.contains(asset.getId()))
            .toList();
    try {
      runYahooFallbackInNewTransaction(yahooFallbackAssets);
    } catch (RuntimeException exception) {
      refreshFailures.add("Yahoo fallback: " + exception.getMessage());
      log.warn("Yahoo Finance fallback failed: {}", exception.getMessage());
    }
    refreshStatisticsIfNeeded(refreshAfterUpdate);
    if (!refreshFailures.isEmpty()) {
      throw new IllegalStateException(
          "Market refresh incomplete: " + String.join("; ", refreshFailures));
    }
    log.info("Updating asset prices finished");
  }

  private Set<Long> runChunkInNewTransaction(Map<String, List<AssetEntity>> chunkByTicker) {
    Set<Long> updated =
        chunkTransactionTemplate.execute(status -> fetchAndUpsertPrices(chunkByTicker));
    return com.smartbox.investory.shared.util.CollectionUtils.immutableSetOrEmpty(updated);
  }

  private void runYahooFallbackInNewTransaction(List<AssetEntity> assets) {
    if (!assets.isEmpty()) {
      chunkTransactionTemplate.executeWithoutResult(status -> fetchAndUpsertYahooFallbacks(assets));
    }
  }

  private void backfillInactiveAssetPricesFromLatestClosedDeals() {
    List<AssetEntity> inactiveAssets =
        assetRepository.findAllByActiveFalseAndSymbolIsNotNull().stream().toList();
    if (inactiveAssets.isEmpty()) {
      return;
    }
    Map<String, PositionEntity> latestClosedBySymbol =
        positionRepository.findClosed().stream()
            .filter(position -> StringUtils.hasText(position.getSymbol()))
            .filter(position -> position.getClosePrice() != null)
            .collect(
                Collectors.toMap(
                    PositionEntity::getSymbol,
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

    List<AssetEntity> backfilled = new ArrayList<>();
    for (AssetEntity asset : inactiveAssets) {
      PositionEntity latest = latestClosedBySymbol.get(asset.getSymbol());
      if (latest == null) {
        continue;
      }
      asset.setMarketPrice(latest.getClosePrice());
      updateUsdPriceCache(
          asset,
          latest.getClosePrice().doubleValue(),
          latest.getPriceCurrency(),
          latest.getCloseTime() == null
              ? ReportingDateHelper.today()
              : latest.getCloseTime().toLocalDate());
      asset.setPriceSource("PositionEntity");
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
        positionRepository.findOpen().stream()
            .map(PositionEntity::getSymbol)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

    List<AssetEntity> changed = new ArrayList<>();
    for (AssetEntity asset : assetRepository.findAll()) {
      boolean shouldBeActive = openSymbols.contains(asset.getSymbol());
      if (!Objects.equals(asset.getActive(), shouldBeActive)) {
        asset.setActive(shouldBeActive);
        changed.add(asset);
      }
    }
    if (!changed.isEmpty()) {
      assetRepository.saveAll(changed);
      log.info(
          "AssetEntity activity refresh: {} assets updated ({} open symbols)",
          changed.size(),
          openSymbols.size());
    }
  }

  private Set<Long> fetchAndUpsertPrices(Map<String, List<AssetEntity>> chunkByTicker) {
    List<String> tickers = List.copyOf(chunkByTicker.keySet());
    if (tickers.isEmpty()) {
      return Set.of();
    }
    log.info("Fetching quotes for: {}", tickers);
    Map<String, MarketQuote> quotes = marketDataProvider.fetchQuotes(tickers);
    ZonedDateTime now = ZonedDateTime.now();

    List<AssetEntity> toSave = new ArrayList<>();
    Set<Long> updatedAssetIds = new java.util.HashSet<>();
    chunkByTicker.forEach(
        (ticker, assets) -> {
          MarketQuote quote = findQuote(quotes, ticker, assets);
          if (quote == null) {
            return;
          }
          for (AssetEntity asset : assets) {
            if (!isUsableQuote(asset, quote)) {
              continue;
            }
            double marketPrice = quote.getClose();
            BigDecimal monetaryPrice = monetaryPrice(asset, marketPrice);
            asset.setMarketPrice(monetaryPrice);
            // Legacy UI/export cache only. Reporting selects price and currency from
            // app_v_current_asset_price, then performs the one required FX conversion.
            updateUsdPriceCache(
                asset, monetaryPrice.doubleValue(), asset.getCurrency(), quoteDate(quote));
            asset.setPriceSource("TwelveData");
            asset.setPriceUpdatedAt(now);
            toSave.add(asset);
            if (asset.getId() != null) {
              updatedAssetIds.add(asset.getId());
            }
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
                marketPriceQuality(asset));
          }
        });
    assetRepository.saveAll(toSave);
    return updatedAssetIds;
  }

  private void fetchAndUpsertYahooFallbacks(List<AssetEntity> assets) {
    List<AssetEntity> toSave = new ArrayList<>();
    for (AssetEntity asset : assets) {
      marketDataProvider
          .fetchLatestQuote(YahooSymbolResolver.resolve(asset.getSymbol(), asset.getYahoo()))
          .filter(quote -> quoteCurrencyMatchesAsset(asset, quote))
          .ifPresent(
              quote -> {
                BigDecimal monetaryPrice = monetaryPrice(asset, quote.price());
                asset.setMarketPrice(monetaryPrice);
                updateUsdPriceCache(
                    asset, monetaryPrice.doubleValue(), asset.getCurrency(), quote.date());
                asset.setPriceSource("YahooFinance");
                asset.setPriceUpdatedAt(ZonedDateTime.now());
                toSave.add(asset);
                assetPriceHistoryRepository.upsertObservedPrice(
                    asset.getId(),
                    quote.date(),
                    "YAHOO_FINANCE",
                    quote.symbol(),
                    asset.getSymbol(),
                    "YAHOO_FINANCE_MARKET_CLOSE",
                    quote.currency(),
                    BigDecimal.valueOf(quote.price()),
                    100,
                    marketPriceQuality(asset));
              });
    }
    if (!toSave.isEmpty()) {
      assetRepository.saveAll(toSave);
      log.info("Yahoo Finance fallback updated {} active asset prices", toSave.size());
    }
  }

  private boolean quoteCurrencyMatchesAsset(AssetEntity asset, LatestQuote quote) {
    if (asset.getCurrency() == null
        || !StringUtils.hasText(quote.currency())
        || asset.getCurrency().name().equalsIgnoreCase(quote.currency())) {
      return true;
    }
    log.warn(
        "Yahoo Finance fallback skipped for {}: quote currency {} differs from asset currency {}",
        asset.getSymbol(),
        quote.currency(),
        asset.getCurrency());
    return false;
  }

  private boolean isQuoteFresh(AssetEntity asset, ZonedDateTime cutoff) {
    ZonedDateTime updatedAt = asset.getPriceUpdatedAt();
    return "TwelveData".equalsIgnoreCase(asset.getPriceSource())
        && updatedAt != null
        && !updatedAt.isBefore(cutoff);
  }

  private LocalDate quoteDate(MarketQuote quote) {
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

  private String quoteCurrency(AssetEntity asset, MarketQuote quote) {
    if (StringUtils.hasText(quote.getCurrency())) {
      return quote.getCurrency().trim().toUpperCase(Locale.ROOT);
    }
    return asset.getCurrency() != null ? asset.getCurrency().name() : "USD";
  }

  private boolean isUsableQuote(AssetEntity asset, MarketQuote quote) {
    if (!Double.isFinite(quote.getClose()) || quote.getClose() <= 0.0) {
      log.warn("TwelveData quote skipped for {}: no positive close price", asset.getSymbol());
      return false;
    }
    if (asset.getCurrency() == null
        || !StringUtils.hasText(quote.getCurrency())
        || !asset.getCurrency().name().equalsIgnoreCase(quote.getCurrency().trim())) {
      log.warn(
          "TwelveData quote skipped for {}: quote currency {} differs from asset currency {}",
          asset.getSymbol(),
          quote.getCurrency(),
          asset.getCurrency());
      return false;
    }
    return true;
  }

  private void updateUsdPriceCache(
      AssetEntity asset, double nativePrice, CurrencyType nativeCurrency, LocalDate valuationDate) {
    if (nativeCurrency == null || valuationDate == null) {
      asset.setMarketPriceUsd((BigDecimal) null);
      log.warn(
          "USD price cache unavailable for {}: native currency or valuation date is missing",
          asset.getSymbol());
      return;
    }
    if (nativeCurrency == CurrencyType.USD) {
      asset.setMarketPriceUsd(BigDecimal.valueOf(nativePrice));
      return;
    }
    try {
      asset.setMarketPriceUsd(
          currencyRateService.convertToBaseCurrency(
              BigDecimal.valueOf(nativePrice), CurrencyType.USD, nativeCurrency, valuationDate));
    } catch (FxRateUnavailableException exception) {
      asset.setMarketPriceUsd((BigDecimal) null);
      log.warn(
          "USD price cache unavailable for {} at {}: {}",
          asset.getSymbol(),
          valuationDate,
          exception.getMessage());
    }
  }

  private String twelveDataSymbol(AssetEntity asset) {
    return marketDataProvider.externalSymbol(asset.getSymbol(), asset.getTicker());
  }

  private MarketQuote findQuote(
      Map<String, MarketQuote> quotes, String requestKey, List<AssetEntity> assets) {
    MarketQuote byRequestKey = quotes.get(requestKey);
    if (byRequestKey != null) {
      return byRequestKey;
    }
    String ticker = requestKey;
    int colon = requestKey.indexOf(':');
    if (colon > 0) {
      ticker = requestKey.substring(0, colon);
    }
    MarketQuote byTicker = quotes.get(ticker);
    if (byTicker != null) {
      return byTicker;
    }
    for (AssetEntity asset : assets) {
      MarketQuote byAssetTicker = quotes.get(asset.getTicker());
      if (byAssetTicker != null) {
        return byAssetTicker;
      }
    }
    return null;
  }

  private boolean isSupportedForPriceUpdate(AssetEntity asset) {
    if (asset == null
        || Boolean.TRUE.equals(asset.getExcludeFromImport())
        || !StringUtils.hasText(asset.getTicker())) {
      return false;
    }
    String ticker = asset.getTicker().trim().toUpperCase(Locale.ROOT);
    if (NOT_SUPPORTED_SYMBOLS.contains(ticker)) {
      return false;
    }
    String symbol = asset.getSymbol();
    if (!isNotConfiguredExcluded(asset)) {
      return false;
    }
    return !(skipNonUsListings && isNonUsExchangeSymbol(symbol));
  }

  private static BigDecimal monetaryPrice(AssetEntity asset, double quotedPrice) {
    BigDecimal price = BigDecimal.valueOf(quotedPrice);
    return isBond(asset) ? price.multiply(PERCENT_OF_PAR_MULTIPLIER) : price;
  }

  private static String marketPriceQuality(AssetEntity asset) {
    return isBond(asset)
        ? "EXACT_LISTING_MARKET_CLOSE_PERCENT_OF_PAR"
        : "EXACT_LISTING_MARKET_CLOSE";
  }

  private static boolean isBond(AssetEntity asset) {
    return asset != null && "BOND".equalsIgnoreCase(asset.getAssetType());
  }

  private boolean isNotConfiguredExcluded(AssetEntity asset) {
    return asset != null
        && (!StringUtils.hasText(asset.getSymbol())
            || !excludedAssetSymbols.contains(asset.getSymbol().trim().toUpperCase(Locale.ROOT)));
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
    for (Long portfolioId : accountRepository.findDistinctPortfolioIds()) {
      refreshMarketPricesAndPositions(portfolioId);
    }
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void refreshMarketPricesAndPositions(Long portfolioId) {
    requirePortfolioId(portfolioId);
    updateStocks(portfolioId, false);
    syncIbkrPositions(portfolioId);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void fullPortfolioUpdate(Long portfolioId) {
    refreshMarketPricesAndPositions(portfolioId);
    statisticsRefreshService.refreshCurrentMarketPrices();
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void fullPortfolioUpdate() {
    for (Long portfolioId : accountRepository.findDistinctPortfolioIds()) {
      fullPortfolioUpdate(portfolioId);
    }
  }

  /**
   * Applies the latest fetched market prices to IBKR open positions. XTB rows remain imported
   * state; reporting derives their live valuation from the asset price and explicit currencies.
   */
  public void syncIbkrPositions() {
    List<Long> ibkrAccounts =
        accountRepository.findAllByProviderIgnoreCase(IBKR_PROVIDER).stream()
            .map(AccountEntity::getId)
            .filter(java.util.Objects::nonNull)
            .toList();
    syncIbkrPositionsForAccounts(ibkrAccounts);
  }

  private void syncIbkrPositionsForAccounts(List<Long> ibkrAccounts) {
    if (ibkrAccounts.isEmpty()) return;
    List<PositionEntity> positions = positionRepository.findOpenByAccountIn(ibkrAccounts);
    if (positions.isEmpty()) {
      return;
    }
    Set<String> symbols =
        positions.stream()
            .map(PositionEntity::getSymbol)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
    Map<String, AssetEntity> assetsBySymbol =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .collect(Collectors.toMap(AssetEntity::getSymbol, asset -> asset, (a, b) -> b));

    for (PositionEntity position : positions) {
      AssetEntity asset = assetsBySymbol.get(position.getSymbol());
      if (asset == null || asset.getMarketPrice() == null || asset.getMarketPrice().signum() == 0) {
        continue;
      }
      double openPrice =
          position.getOpenPrice() != null ? position.getOpenPrice().doubleValue() : 0.0;
      position.setMarketPrice(asset.getMarketPrice());
      if (position.getPriceCurrency() == position.getProfitCurrency()) {
        position.setProfit(
            BigDecimal.valueOf(
                position.signedQuantity() * (asset.getMarketPrice().doubleValue() - openPrice)));
      } else {
        log.warn(
            "Preserving IBKR position {} profit: price currency {} differs from profit currency {}",
            position.getId(),
            position.getPriceCurrency(),
            position.getProfitCurrency());
      }
    }
    positionRepository.saveAll(positions);

    // AccountEntity cash/equity is broker-imported state. Quote refresh must not rewrite it:
    // account statistics will combine broker cash with current market value from assets.
  }

  public void syncIbkrPositions(Long portfolioId) {
    requirePortfolioId(portfolioId);
    List<Long> ibkrAccounts =
        accountRepository.findAllByPortfolioId(portfolioId).stream()
            .filter(account -> IBKR_PROVIDER.equalsIgnoreCase(account.getProvider()))
            .map(AccountEntity::getId)
            .filter(Objects::nonNull)
            .toList();
    syncIbkrPositionsForAccounts(ibkrAccounts);
  }

  /** Restores reconstructed XTB open-position profit to its canonical imported value. */
  public int repairXtbReconstructedPositionProfits() {
    List<Long> xtbAccounts =
        accountRepository.findAllByProviderIgnoreCase(XTB_PROVIDER).stream()
            .map(AccountEntity::getId)
            .filter(java.util.Objects::nonNull)
            .toList();
    if (xtbAccounts.isEmpty()) {
      return 0;
    }
    List<PositionEntity> repaired =
        positionRepository.findOpenByAccountIn(xtbAccounts).stream()
            .filter(position -> XTB_RECONSTRUCTED_COMMENT.equals(position.getComment()))
            .filter(position -> position.getProfit() == null || position.getProfit().signum() != 0)
            .peek(position -> position.setProfit(BigDecimal.ZERO))
            .toList();
    if (!repaired.isEmpty()) {
      positionRepository.saveAll(repaired);
    }
    return repaired.size();
  }

  private void refreshStatisticsIfNeeded(boolean refreshAfterUpdate) {
    if (refreshAfterUpdate) {
      statisticsRefreshService.refreshAll();
    }
  }

  private static void requirePortfolioId(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
  }
}
