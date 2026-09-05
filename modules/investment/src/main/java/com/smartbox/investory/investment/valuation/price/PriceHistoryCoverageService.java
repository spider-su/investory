package com.smartbox.investory.investment.valuation.price;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.performance.InvestmentCalculationCache;
import com.smartbox.investory.investment.port.market.MarketDataProvider;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class PriceHistoryCoverageService {

  private final PositionRepository openedPositionRepository;
  private final AccountRepository accountRepository;
  private final AssetRepository assetRepository;
  private final AssetPriceHistoryRepository historyRepository;
  private final MarketDataProvider marketDataProvider;
  private final InvestmentCalculationCache calculationCache;
  private final Clock clock;
  private final LocalDate historyStart;
  private final String benchmarkSymbol;

  public PriceHistoryCoverageService(
      PositionRepository openedPositionRepository,
      AssetRepository assetRepository,
      AssetPriceHistoryRepository historyRepository,
      MarketDataProvider marketDataProvider,
      InvestmentCalculationCache calculationCache,
      Clock clock,
      String historyStart,
      String benchmarkSymbol) {
    this(
        openedPositionRepository,
        null,
        assetRepository,
        historyRepository,
        marketDataProvider,
        calculationCache,
        clock,
        historyStart,
        benchmarkSymbol);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public PriceHistoryCoverageService(
      PositionRepository openedPositionRepository,
      AccountRepository accountRepository,
      AssetRepository assetRepository,
      AssetPriceHistoryRepository historyRepository,
      MarketDataProvider marketDataProvider,
      InvestmentCalculationCache calculationCache,
      Clock clock,
      @Value("${app.history-start:" + FinancialPolicyDefaults.HISTORY_START_TEXT + "}")
          String historyStart,
      @Value("${app.benchmark.symbol:SPY}") String benchmarkSymbol) {
    this.openedPositionRepository = openedPositionRepository;
    this.accountRepository = accountRepository;
    this.assetRepository = assetRepository;
    this.historyRepository = historyRepository;
    this.marketDataProvider = marketDataProvider;
    this.calculationCache = calculationCache;
    this.clock = clock;
    this.historyStart = LocalDate.parse(historyStart);
    this.benchmarkSymbol = benchmarkSymbol;
  }

  @Transactional
  public CoverageResult ensureAssetCoverage(Long assetId, LocalDate from, LocalDate to) {
    if (assetId == null || from == null || to == null || from.isAfter(to)) {
      return new CoverageResult(assetId, from, to, 0, 0, 0, CoverageStatus.NO_SOURCE, List.of());
    }
    AssetEntity asset = assetRepository.findById(assetId).orElse(null);
    if (asset == null || !StringUtils.hasText(asset.getTicker())) {
      return new CoverageResult(
          assetId, from, to, 0, 0, 0, CoverageStatus.NO_SOURCE, List.of("No source mapping"));
    }
    List<AssetPriceHistoryRepository.HistoricalAssetPriceRow> existingRows =
        historyRepository.findHistoricalPricesBySymbolInBefore(List.of(asset.getSymbol()), to);
    Map<LocalDate, AssetPriceHistoryRepository.HistoricalAssetPriceRow> existing =
        existingRows.stream()
            .filter(row -> row.getPriceDate() != null && !row.getPriceDate().isBefore(from))
            .collect(
                Collectors.toMap(
                    AssetPriceHistoryRepository.HistoricalAssetPriceRow::getPriceDate,
                    row -> row,
                    (first, ignored) -> first,
                    TreeMap::new));
    boolean bounded =
        !existing.isEmpty()
            && !existing.keySet().stream().min(LocalDate::compareTo).orElse(from).isAfter(from)
            && !existing.keySet().stream().max(LocalDate::compareTo).orElse(from).isBefore(to);
    if (bounded) {
      return result(assetId, from, to, existing.size(), 0, CoverageStatus.COMPLETE, List.of());
    }

    String providerSymbol = marketDataProvider.externalSymbol(asset.getSymbol(), asset.getTicker());
    NavigableMap<LocalDate, Double> fetched =
        marketDataProvider.fetchDailyCloses(providerSymbol, from, to);
    int written = 0;
    for (Map.Entry<LocalDate, Double> entry : fetched.entrySet()) {
      if (!existing.containsKey(entry.getKey())) {
        historyRepository.upsertObservedPrice(
            asset.getId(),
            entry.getKey(),
            "TWELVE_DATA",
            providerSymbol,
            asset.getSymbol(),
            "TWELVE_DATA_MARKET_CLOSE",
            asset.getCurrency() == null ? null : asset.getCurrency().name(),
            BigDecimal.valueOf(entry.getValue()),
            100,
            marketPriceQuality(asset));
        written++;
      }
    }
    if (written > 0) {
      calculationCache.invalidate();
    }
    CoverageStatus status =
        fetched.isEmpty() ? CoverageStatus.PROVIDER_ERROR : CoverageStatus.COMPLETE;
    return result(assetId, from, to, existing.size(), written, status, List.of());
  }

  @Transactional
  public PortfolioCoverageResult ensurePortfolioCoverage(Long portfolioId) {
    LocalDate to = LocalDate.now(clock);
    Map<Long, LocalDate> requiredFrom = new LinkedHashMap<>();
    var positions =
        portfolioId == null || accountRepository == null
            ? openedPositionRepository.findOpen()
            : openedPositionRepository.findOpenByAccountIn(
                accountRepository.findAllByPortfolioId(portfolioId).stream()
                    .map(account -> account.getId())
                    .toList());
    for (PositionEntity position : positions) {
      if (position.getAssetId() == null || position.getOpenTime() == null) continue;
      requiredFrom.merge(
          position.getAssetId(),
          position.getOpenTime().withZoneSameInstant(clock.getZone()).toLocalDate(),
          (left, right) -> left.isBefore(right) ? left : right);
    }
    List<CoverageResult> results = new ArrayList<>();
    requiredFrom.forEach((assetId, from) -> results.add(ensureAssetCoverage(assetId, from, to)));
    assetRepository
        .findBySymbol(benchmarkSymbol)
        .ifPresent(asset -> results.add(ensureAssetCoverage(asset.getId(), historyStart, to)));
    return new PortfolioCoverageResult(portfolioId, results);
  }

  private CoverageResult result(
      Long id,
      LocalDate from,
      LocalDate to,
      int existing,
      int fetched,
      CoverageStatus status,
      List<String> warnings) {
    return new CoverageResult(id, from, to, existing, fetched, 0, status, warnings);
  }

  private static String marketPriceQuality(AssetEntity asset) {
    return "BOND".equalsIgnoreCase(asset.getAssetType())
        ? "EXACT_LISTING_MARKET_CLOSE_PERCENT_OF_PAR"
        : "EXACT_LISTING_MARKET_CLOSE";
  }

  public enum CoverageStatus {
    COMPLETE,
    PARTIAL,
    NO_SOURCE,
    PROVIDER_ERROR
  }

  public record CoverageResult(
      Long assetId,
      LocalDate requestedFrom,
      LocalDate requestedTo,
      int existingObservations,
      int fetchedObservations,
      int remainingGaps,
      CoverageStatus status,
      List<String> warnings) {}

  public record PortfolioCoverageResult(Long portfolioId, List<CoverageResult> results) {
    public PortfolioCoverageResult {
      results = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(results);
    }
  }
}
