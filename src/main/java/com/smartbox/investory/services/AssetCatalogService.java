package com.smartbox.investory.services;

import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.infrastructure.repository.Asset;
import com.smartbox.investory.infrastructure.repository.AssetRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

  /**
   * Verifies that every requested asset resolves to one existing canonical asset.
   *
   * <p>Importers must not create asset identity from an unknown broker/source symbol.
   */
  public void ensureAssetsExist(Collection<AssetSeed> requestedAssets) {
    if (CollectionUtils.isEmpty(requestedAssets)) {
      return;
    }

    Set<String> requiredSymbols =
        requestedAssets.stream()
            .filter(Objects::nonNull)
            .map(AssetSeed::symbol)
            .map(this::normalizeSymbol)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
    Set<String> existingSymbols =
        assetRepository.findAllBySymbolIn(requiredSymbols).stream()
            .map(Asset::getSymbol)
            .filter(StringUtils::hasText)
            .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());

    for (String symbol : requiredSymbols) {
      if (!existingSymbols.contains(symbol)) {
        throw unknownAssetMapping(symbol, symbol);
      }
    }
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

    Set<String> normalizedSymbols =
        normalizedByRaw.values().stream().filter(StringUtils::hasText).collect(Collectors.toSet());
    if (normalizedSymbols.isEmpty()) {
      return Map.of();
    }

    Map<String, Asset> exactBySymbol =
        assetRepository.findAllBySymbolIn(normalizedSymbols).stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .collect(
                Collectors.toMap(
                    asset -> asset.getSymbol().trim().toUpperCase(Locale.ROOT),
                    asset -> asset,
                    (left, right) -> left));

    Set<String> unresolvedTickers =
        normalizedSymbols.stream()
            .filter(symbol -> !exactBySymbol.containsKey(symbol))
            .filter(symbol -> !isQualifiedSymbol(symbol))
            .map(this::deriveTicker)
            .collect(Collectors.toSet());

    Map<String, List<Asset>> assetsByTicker =
        unresolvedTickers.isEmpty()
            ? Map.of()
            : assetRepository.findAllByTickerIn(unresolvedTickers).stream()
                .filter(asset -> StringUtils.hasText(asset.getTicker()))
                .collect(
                    Collectors.groupingBy(
                        asset -> asset.getTicker().trim().toUpperCase(Locale.ROOT)));

    Map<String, String> result = new HashMap<>();
    normalizedByRaw.forEach(
        (raw, symbol) -> {
          if (!StringUtils.hasText(symbol)) {
            result.put(raw, null);
            return;
          }
          result.put(raw, resolveCanonicalSymbol(raw, symbol, exactBySymbol, assetsByTicker));
        });
    return result;
  }

  @Transactional(readOnly = true, noRollbackFor = IllegalArgumentException.class)
  public String mapIbkrSymbolToCanonical(String rawIbkrSymbol) {
    String ibkrSymbol = normalizeSymbol(rawIbkrSymbol);
    if (!StringUtils.hasText(ibkrSymbol)) {
      return null;
    }

    List<Asset> brokerMatches =
        assetRepository.findAllByIbkrIgnoreCase(ibkrSymbol).stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .toList();
    if (brokerMatches.size() == 1) {
      return brokerMatches.getFirst().getSymbol();
    }
    if (brokerMatches.size() > 1) {
      throw ambiguousAssetMapping(rawIbkrSymbol, ibkrSymbol, brokerMatches);
    }

    var exact = assetRepository.findBySymbol(ibkrSymbol);
    if (exact.isPresent() && StringUtils.hasText(exact.get().getSymbol())) {
      return exact.get().getSymbol();
    }
    if (isQualifiedSymbol(ibkrSymbol)) {
      throw unknownAssetMapping(rawIbkrSymbol, ibkrSymbol);
    }

    List<Asset> tickerMatches =
        assetRepository.findAllByTickerIn(Set.of(deriveTicker(ibkrSymbol))).stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .toList();
    if (tickerMatches.size() == 1) {
      return tickerMatches.getFirst().getSymbol();
    }
    if (tickerMatches.size() > 1) {
      throw ambiguousAssetMapping(rawIbkrSymbol, ibkrSymbol, tickerMatches);
    }
    throw unknownAssetMapping(rawIbkrSymbol, ibkrSymbol);
  }

  private String resolveCanonicalSymbol(
      String rawSymbol,
      String normalizedSymbol,
      Map<String, Asset> exactBySymbol,
      Map<String, List<Asset>> assetsByTicker) {
    Asset exact = exactBySymbol.get(normalizedSymbol);
    if (exact != null && StringUtils.hasText(exact.getSymbol())) {
      return exact.getSymbol();
    }
    if (isQualifiedSymbol(normalizedSymbol)) {
      throw unknownAssetMapping(rawSymbol, normalizedSymbol);
    }

    List<Asset> tickerMatches =
        assetsByTicker.getOrDefault(deriveTicker(normalizedSymbol), List.of()).stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .toList();
    if (tickerMatches.size() == 1) {
      return tickerMatches.getFirst().getSymbol();
    }
    if (tickerMatches.size() > 1) {
      throw ambiguousAssetMapping(rawSymbol, normalizedSymbol, tickerMatches);
    }
    throw unknownAssetMapping(rawSymbol, normalizedSymbol);
  }

  private IllegalArgumentException unknownAssetMapping(String rawSymbol, String normalizedSymbol) {
    return new IllegalArgumentException(
        "Unknown asset mapping for source symbol '"
            + rawSymbol
            + "' (normalized '"
            + normalizedSymbol
            + "'). Add the canonical asset mapping before importing.");
  }

  private IllegalArgumentException ambiguousAssetMapping(
      String rawSymbol, String normalizedSymbol, List<Asset> matches) {
    String candidates =
        matches.stream()
            .map(Asset::getSymbol)
            .filter(StringUtils::hasText)
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));
    return new IllegalArgumentException(
        "Ambiguous asset mapping for source symbol '"
            + rawSymbol
            + "' (normalized '"
            + normalizedSymbol
            + "'): "
            + candidates);
  }

  private boolean isQualifiedSymbol(String symbol) {
    return symbol.contains(".");
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
      String assetType) {}
}
