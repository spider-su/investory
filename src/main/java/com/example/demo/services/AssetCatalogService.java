package com.example.demo.services;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetCatalogService {

  private static final int SYMBOL_LIMIT = 15;
  private static final Pattern FOREX_PAIR_PATTERN = Pattern.compile("^[A-Z]{3}\\.[A-Z]{3}$");

  private final AssetRepository assetRepository;

  public void ensureAssetsExist(Collection<AssetSeed> requestedAssets) {
    if (CollectionUtils.isEmpty(requestedAssets)) {
      return;
    }

    Map<String, AssetSeed> normalizedBySymbol = new HashMap<>();
    for (AssetSeed asset : requestedAssets) {
      if (asset == null || !StringUtils.hasText(asset.symbol())) {
        continue;
      }
      normalizedBySymbol.putIfAbsent(asset.symbol().trim(), asset.normalized());
    }
    if (normalizedBySymbol.isEmpty()) {
      return;
    }

    // Skip symbols that already exist
    Set<String> symbols = new HashSet<>(normalizedBySymbol.keySet());
    assetRepository
        .findAllBySymbolIn(symbols)
        .forEach(existing -> symbols.remove(existing.getSymbol()));
    if (symbols.isEmpty()) {
      return;
    }

    Set<String> tickers =
        symbols.stream()
            .map(symbol -> normalizedBySymbol.get(symbol).ticker())
            .collect(java.util.stream.Collectors.toSet());
    Set<String> existingTickers =
        assetRepository.findAllByTickerIn(tickers).stream()
            .map(Asset::getTicker)
            .collect(java.util.stream.Collectors.toSet());
    symbols.removeIf(symbol -> existingTickers.contains(normalizedBySymbol.get(symbol).ticker()));
    if (symbols.isEmpty()) {
      return;
    }

    assetRepository.saveAll(
        symbols.stream().map(symbol -> toAsset(normalizedBySymbol.get(symbol))).toList());
  }

  public AssetSeed seedForSymbol(String rawSymbol, CurrencyType currencyHint) {
    String symbol = normalizeSymbol(rawSymbol);
    if (!StringUtils.hasText(symbol)) {
      return null;
    }

    String ticker = deriveTicker(symbol);
    CurrencyType currency = inferCurrency(symbol);
    String yahoo = symbol.contains(".") ? symbol : ticker;
    return new AssetSeed(symbol, ticker, currency, yahoo, deriveCountry(symbol), "EQUITY");
  }

  public String normalizeSymbol(String rawSymbol) {
    if (!StringUtils.hasText(rawSymbol)) {
      return null;
    }

    String trimmed = rawSymbol.trim().toUpperCase(Locale.ROOT);
    if (!StringUtils.hasText(trimmed) || "-".equals(trimmed)) {
      return null;
    }
    if (FOREX_PAIR_PATTERN.matcher(trimmed).matches()) {
      return null;
    }
    if (trimmed.length() <= SYMBOL_LIMIT && trimmed.matches("^[A-Z0-9._-]+$")) {
      return trimmed;
    }

    String compact = trimmed.replaceAll("[^A-Z0-9.]", "");
    if (!StringUtils.hasText(compact)) {
      return null;
    }
    if (FOREX_PAIR_PATTERN.matcher(compact).matches()) {
      return null;
    }
    if (compact.length() <= SYMBOL_LIMIT) {
      return compact;
    }
    return compact.substring(0, SYMBOL_LIMIT);
  }

  public Map<String, String> normalizeSymbolsForStorage(Collection<String> rawSymbols) {
    if (CollectionUtils.isEmpty(rawSymbols)) {
      return Map.of();
    }

    Map<String, String> normalizedByRaw = new HashMap<>();
    rawSymbols.stream()
        .filter(StringUtils::hasText)
        .distinct()
        .forEach(raw -> normalizedByRaw.put(raw, normalizeSymbol(raw)));
    Set<String> tickers =
        normalizedByRaw.values().stream()
            .filter(StringUtils::hasText)
            .map(this::deriveTicker)
            .collect(Collectors.toSet());
    if (tickers.isEmpty()) {
      return Map.of();
    }

    Map<String, List<Asset>> assetsByTicker =
        assetRepository.findAllByTickerIn(tickers).stream()
            .filter(asset -> StringUtils.hasText(asset.getTicker()))
            .collect(
                Collectors.groupingBy(asset -> asset.getTicker().trim().toUpperCase(Locale.ROOT)));

    Map<String, String> result = new HashMap<>();
    normalizedByRaw.forEach(
        (raw, symbol) -> {
          if (!StringUtils.hasText(symbol)) {
            result.put(raw, null);
            return;
          }
          result.put(
              raw,
              selectCanonicalSymbol(
                  symbol, assetsByTicker.getOrDefault(deriveTicker(symbol), List.of())));
        });
    return result;
  }

  private String selectCanonicalSymbol(String symbol, List<Asset> matches) {
    if (CollectionUtils.isEmpty(matches)) {
      return symbol;
    }
    return matches.stream()
        .filter(asset -> symbol.equalsIgnoreCase(asset.getSymbol()))
        .findFirst()
        .or(
            () ->
                matches.stream()
                    .filter(asset -> StringUtils.hasText(asset.getSymbol()))
                    .max(
                        Comparator.comparing((Asset asset) -> preferredBySuffix(asset.getSymbol()))
                            .thenComparing(asset -> asset.getSymbol().length())))
        .map(Asset::getSymbol)
        .orElse(symbol);
  }

  public String mapIbkrSymbolToCanonical(String rawIbkrSymbol) {
    String ibkrSymbol = normalizeSymbol(rawIbkrSymbol);
    if (!StringUtils.hasText(ibkrSymbol)) {
      return null;
    }
    var matches = assetRepository.findAllByIbrkIgnoreCase(ibkrSymbol);
    if (CollectionUtils.isEmpty(matches)) {
      return ibkrSymbol.contains(".") ? ibkrSymbol : ibkrSymbol + ".US";
    }
    return matches.stream()
        .filter(asset -> StringUtils.hasText(asset.getSymbol()))
        .max(
            Comparator.comparing((Asset asset) -> preferredBySuffix(asset.getSymbol()))
                .thenComparing(asset -> asset.getSymbol().length()))
        .map(Asset::getSymbol)
        .orElse(ibkrSymbol);
  }

  private Asset toAsset(AssetSeed seed) {
    return Asset.builder()
        .name(seed.ticker())
        .symbol(seed.symbol())
        .ticker(seed.ticker())
        .ibrk(seed.ticker())
        .yahoo(seed.yahoo())
        .country(seed.country())
        .currency(seed.currency())
        .assetType(seed.assetType())
        .active(Boolean.TRUE)
        .build();
  }

  private int preferredBySuffix(String symbol) {
    String upper = symbol.toUpperCase(Locale.ROOT);
    if (upper.endsWith(".US")) {
      return 3;
    }
    if (upper.contains(".")) {
      return 2;
    }
    return 1;
  }

  private String deriveTicker(String symbol) {
    int dot = symbol.indexOf('.');
    return dot > 0 ? symbol.substring(0, dot) : symbol;
  }

  private String deriveCountry(String symbol) {
    int dot = symbol.lastIndexOf('.');
    if (dot > 0 && dot < symbol.length() - 1) {
      return symbol.substring(dot + 1).toUpperCase(Locale.ROOT);
    }
    return "US";
  }

  private CurrencyType inferCurrency(String symbol) {
    int dot = symbol.lastIndexOf('.');
    if (dot < 0 || dot == symbol.length() - 1) {
      return CurrencyType.USD;
    }
    String suffix = symbol.substring(dot + 1).toUpperCase(Locale.ROOT);
    return switch (suffix) {
      case "PL" -> CurrencyType.PLN;
      case "DE", "FR", "NL", "IT", "ES", "FI", "PT", "IE", "AT", "BE" -> CurrencyType.EUR;
      default -> CurrencyType.USD;
    };
  }

  public record AssetSeed(
      String symbol,
      String ticker,
      CurrencyType currency,
      String yahoo,
      String country,
      String assetType) {

    private AssetSeed normalized() {
      String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
      String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
      String normalizedYahoo = yahoo.trim().toUpperCase(Locale.ROOT);
      String normalizedCountry = country.trim().toUpperCase(Locale.ROOT);
      String normalizedAssetType =
          assetType == null ? null : assetType.trim().toUpperCase(Locale.ROOT);
      return new AssetSeed(
          normalizedSymbol,
          normalizedTicker,
          currency == null ? CurrencyType.USD : currency,
          normalizedYahoo,
          normalizedCountry,
          normalizedAssetType);
    }
  }
}
