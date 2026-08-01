package com.example.demo.services.imports.xtb;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionSettlementModel;
import com.example.demo.infrastructure.PositionType;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.services.AssetCatalogService;
import com.example.demo.services.PositionSettlementModelService;
import com.example.demo.services.imports.ImportExecutionResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@Transactional
@RequiredArgsConstructor
public class XtbImportV2Service {

  private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.ROOT);
  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final double LOT_EPSILON = 1.0e-9;
  private static final Pattern LOT_PATTERN =
      Pattern.compile(
          "^(OPEN|CLOSE)\\s+(BUY|SELL)\\s+([0-9]+(?:[\\.,][0-9]+)?)(?:/[0-9]+(?:[\\.,][0-9]+)?)?\\s+@\\s+([0-9]+(?:[\\.,][0-9]+)?)$",
          Pattern.CASE_INSENSITIVE);
  private static final List<DateTimeFormatter> DATE_FORMATTERS =
      List.of(
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

  private final ClosedPositionRepository closedPositionRepository;
  private final OpenedPositionRepository openedPositionRepository;
  private final CashOperationRepository cashOperationRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final AssetRepository assetRepository;
  private final AssetCatalogService assetCatalogService;
  private final PositionSettlementModelService positionSettlementModelService;
  private final XtbPositionCurrencyResolver positionCurrencyResolver;

  public boolean isZipReport(String fileName) {
    return fileName != null && fileName.trim().toLowerCase(Locale.ROOT).endsWith(".zip");
  }

  public boolean supports(InputStream inputStream) {
    try (Workbook workbook = new XSSFWorkbook(inputStream)) {
      return supports(workbook);
    } catch (Exception ignored) {
      return false;
    }
  }

  public ImportExecutionResult importZip(InputStream zipInputStream, String sourceName) throws Exception {
    int total = 0;
    int files = 0;
    List<String> importedAccounts = new ArrayList<>();

    try (ZipInputStream zip = new ZipInputStream(zipInputStream)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
          continue;
        }
        byte[] workbookBytes = readAllBytes(zip);
        ImportExecutionResult partial =
            importWorkbook(new ByteArrayInputStream(workbookBytes), entry.getName());
        total += partial.rowsApplied();
        files++;
        importedAccounts.add(partial.details());
      }
    }

    if (files == 0) {
      throw new IllegalArgumentException("XTB v2 zip has no .xlsx files: " + sourceName);
    }

    String details =
        String.format(
            "XTB v2 %s: imported %d workbook(s), rows=%d [%s]",
            sourceName != null ? sourceName : "zip", files, total, String.join(", ", importedAccounts));
    return new ImportExecutionResult(total, total, 0, details);
  }

  public ImportExecutionResult importWorkbook(InputStream xlsxInputStream, String sourceName)
      throws Exception {
    try (Workbook workbook = new XSSFWorkbook(xlsxInputStream)) {
      if (!supports(workbook)) {
        throw new IllegalArgumentException("Not an XTB v2 workbook: " + sourceName);
      }

      Sheet cashSheet = workbook.getSheet("Cash Operations");
      Sheet closedSheet = workbook.getSheet("Closed Positions");

      String accountRaw = firstNonBlank(readHeaderValue(cashSheet, "Account number"), readHeaderValue(closedSheet, "Account"));
      if (!StringUtils.hasText(accountRaw)) {
        throw new IllegalStateException("XTB v2 workbook has no account number: " + sourceName);
      }
      Long account = parseAccountId(accountRaw);

      Map<String, Integer> cashColumns = findHeader(cashSheet, "Type", "Time", "Amount");
      Map<String, Integer> closedColumns = findHeader(closedSheet, "Ticker", "Type", "Volume");

      List<CashOperation> cashOperations = parseCashOperations(cashSheet, cashColumns, account, sourceName);
      List<ClosedPosition> closedPositions = parseClosedPositions(closedSheet, closedColumns, account, sourceName);
      normalizeImportedSymbols(cashOperations, closedPositions);

      CurrencyType currency = inferCurrency(sourceName, cashOperations, closedPositions);
      cashOperations.forEach(op -> op.setCurrency(currency));

      List<OpenedPosition> openedPositions =
          reconstructOpenedPositions(operationsForOpenReconstruction(account, cashOperations), account, currency);
      openedPositions = deduplicateOpenedPositions(openedPositions);

      applyPositionCurrencies(closedPositions, openedPositions, currency);

      ensureAssetsExist(cashOperations, closedPositions, openedPositions, currency);
      applyAssetIdentities(cashOperations, closedPositions, openedPositions);
      persistTradePriceHistory(closedPositions, openedPositions, currency);

      cashOperationRepository.saveAll(cashOperations);
      closedPositionRepository.saveAll(closedPositions);
      if (openedPositions.isEmpty()) {
        openedPositionRepository.deleteByAccount(account);
      } else {
        openedPositionRepository.removeAllByAccountNotIn(account, openedPositions);
        openedPositionRepository.saveAll(openedPositions);
      }
      int total = cashOperations.size() + closedPositions.size() + openedPositions.size();
      String details =
          String.format(
              "acc=%s cash=%d closed=%d open=%d",
              account, cashOperations.size(), closedPositions.size(), openedPositions.size());
      return new ImportExecutionResult(total, total, 0, details);
    }
  }

  private boolean supports(Workbook workbook) {
    return workbook.getSheet("Cash Operations") != null && workbook.getSheet("Closed Positions") != null;
  }

  private List<CashOperation> parseCashOperations(
      Sheet sheet, Map<String, Integer> columns, Long account, String sourceName) {
    if (sheet == null || CollectionUtils.isEmpty(columns)) {
      return List.of();
    }

    List<CashOperation> operations = new ArrayList<>();
    for (Row row : dataRows(sheet, columns)) {
      ZonedDateTime operationDate = parseDate(cell(row, columns.get("Time")));
      // Skip rows with no date (typically footer/total rows)
      if (operationDate == null) {
        continue;
      }

      CashOperation operation = new CashOperation();
      operation.setId(
          parseLong(cellValue(row, columns.get("ID")))
              .orElseGet(() -> syntheticId(account + "|cash|" + sourceName + "|" + row.getRowNum())));
      operation.setAccount(account);
      String comment = cellValue(row, columns.get("Comment"));
      CashOperationType type = CashOperationType.fromString(cellValue(row, columns.get("Type")));
      if (type == CashOperationType.UNKNOWN && comment != null) {
        type = CashOperationType.fromString(comment);
      }
      operation.setType(type);
      operation.setSymbol(cellValue(row, columns.get("Ticker")));
      operation.setAmount(parseDouble(cellValue(row, columns.get("Amount"))).orElse(null));
      operation.setComment(comment);
      operation.setDate(operationDate);
      operations.add(operation);
    }
    return operations;
  }

  private List<ClosedPosition> parseClosedPositions(
      Sheet sheet, Map<String, Integer> columns, Long account, String sourceName) {
    if (sheet == null || CollectionUtils.isEmpty(columns)) {
      return List.of();
    }

    List<ClosedPosition> positions = new ArrayList<>();
    Map<String, Integer> sourceRowOccurrences = new HashMap<>();
    for (Row row : dataRows(sheet, columns)) {
      String symbol = cellValue(row, columns.get("Ticker"));
      // XTB sheets can end with a totals/footer row. It contains numeric values in
      // known columns but is not a position and has no ticker.
      if (!StringUtils.hasText(symbol)) {
        continue;
      }
      String typeText = cellValue(row, columns.get("Type"));
      Double volume = parseDouble(cellValue(row, columns.get("Volume"))).orElse(null);
      ZonedDateTime openTime = parseDate(cell(row, columns.get("Open Time (UTC)")));
      ZonedDateTime closeTime = parseDate(cell(row, columns.get("Close Time (UTC)")));
      Double openPrice = parseDouble(cellValue(row, columns.get("Open Price"))).orElse(null);
      Double closePrice = parseDouble(cellValue(row, columns.get("Close Price"))).orElse(null);
      Double purchaseValue = parseDouble(cellValue(row, columns.get("Purchase Value"))).orElse(null);
      Double saleValue = parseDouble(cellValue(row, columns.get("Sale Value"))).orElse(null);
      Double commission = parseDouble(cellValue(row, columns.get("Commission"))).orElse(null);
      Double margin = parseDouble(cellValue(row, columns.get("Margin"))).orElse(null);
      Double swap = parseDouble(cellValue(row, columns.get("Swap"))).orElse(null);
      Double profit =
          parseDouble(cellValue(row, columns.get("Profit/Loss")))
              .or(() -> parseDouble(cellValue(row, columns.get("Gross Profit"))))
              .orElse(null);
      String product = cellValue(row, columns.get("Product"));
      String sourcePositionId = normalizeSourcePositionId(cellValue(row, columns.get("Position ID")));
      Double openConversionRate =
          parseDouble(cellValue(row, columns.get("Open Conversion Rate"))).orElse(null);
      Double closeConversionRate =
          parseDouble(cellValue(row, columns.get("Close Conversion Rate"))).orElse(null);

      String businessKey =
          closedPositionBusinessKey(
              account,
              symbol,
              typeText,
              volume,
              openTime,
              openPrice,
              closeTime,
              closePrice,
              purchaseValue,
              saleValue,
              commission,
              margin,
              swap,
              profit,
              product);
      String sourceIdentity =
          account + "|" + firstNonBlank(sourcePositionId, "NO_POSITION_ID") + "|" + businessKey;
      int sourceRowOccurrence = sourceRowOccurrences.merge(sourceIdentity, 1, Integer::sum);

      ClosedPosition position = new ClosedPosition();
      position.setId(syntheticId(sourceIdentity + "|occurrence=" + sourceRowOccurrence));
      position.setAccount(account);
      position.setSourcePositionId(sourcePositionId);
      position.setSourceRowOccurrence(sourceRowOccurrence);
      position.setSymbol(symbol);
      position.setType(PositionType.fromBrokerSideOrBuy(typeText));
      position.setVolume(volume);
      position.setOpenTime(openTime);
      position.setOpenPrice(openPrice);
      position.setSourceOpenPrice(openPrice);
      position.setOpenConversionRate(openConversionRate);
      position.setCloseTime(closeTime);
      position.setClosePrice(closePrice);
      position.setSourceClosePrice(closePrice);
      position.setCloseConversionRate(closeConversionRate);
      position.setPurchaseValue(purchaseValue);
      position.setSaleValue(saleValue);
      position.setCommission(commission);
      position.setMargin(margin);
      position.setSwap(swap);
      position.setProfit(profit);
      position.setSettlementModel(
          positionSettlementModelService.classifyXtb(purchaseValue, saleValue, margin, product));
      position.setComment(product);
      positions.add(position);
    }
    return positions;
  }

  private List<OpenedPosition> deduplicateOpenedPositions(List<OpenedPosition> positions) {
    if (CollectionUtils.isEmpty(positions)) {
      return List.of();
    }
    Map<Long, OpenedPosition> unique = new LinkedHashMap<>();
    for (OpenedPosition position : positions) {
      if (position == null || position.getId() == null) {
        continue;
      }
      unique.putIfAbsent(position.getId(), position);
    }
    return new ArrayList<>(unique.values());
  }

  private String closedPositionBusinessKey(
      Long account,
      String symbol,
      String typeText,
      Double volume,
      ZonedDateTime openTime,
      Double openPrice,
      ZonedDateTime closeTime,
      Double closePrice,
      Double purchaseValue,
      Double saleValue,
      Double commission,
      Double margin,
      Double swap,
      Double profit,
      String product) {
    return String.format(
        Locale.ROOT,
        "%s|closed|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
        account,
        normalizeKeyPart(symbol),
        normalizeKeyPart(typeText),
        normalizeKeyPart(volume),
        normalizeKeyPart(openTime),
        normalizeKeyPart(openPrice),
        normalizeKeyPart(closeTime),
        normalizeKeyPart(closePrice),
        normalizeKeyPart(purchaseValue),
        normalizeKeyPart(saleValue),
        normalizeKeyPart(commission),
        normalizeKeyPart(margin),
        normalizeKeyPart(swap),
        normalizeKeyPart(profit),
        normalizeKeyPart(product));
  }

  private String normalizeKeyPart(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  List<OpenedPosition> reconstructOpenedPositions(
      List<CashOperation> cashOperations, Long account, CurrencyType currency) {
    Map<String, Deque<Lot>> buyLots = new HashMap<>();
    Map<String, Deque<Lot>> sellLots = new HashMap<>();

    cashOperations.stream()
        .filter(op -> op.getType() == CashOperationType.STOCK_PURCHASE || op.getType() == CashOperationType.STOCK_SELL)
        .filter(op -> StringUtils.hasText(op.getSymbol()))
        .sorted(
            Comparator.comparing(CashOperation::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CashOperation::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .forEach(
            op -> {
              LotEvent event = parseLotEvent(op.getComment()).orElse(null);
              if (event == null || event.volume <= LOT_EPSILON) {
                return;
              }

              Map<String, Deque<Lot>> openLots = event.side == PositionType.BUY ? buyLots : sellLots;
              Deque<Lot> lots = openLots.computeIfAbsent(op.getSymbol(), ignored -> new ArrayDeque<>());

              if (event.actionOpen) {
                lots.addLast(new Lot(event.volume, event.price, op.getDate()));
              } else {
                consumeFifo(lots, event.volume);
              }
            });

    List<OpenedPosition> openedPositions = new ArrayList<>();
    appendOpenedPositionsForAccount(openedPositions, account, currency, buyLots, PositionType.BUY);
    appendOpenedPositionsForAccount(openedPositions, account, currency, sellLots, PositionType.SELL);
    return openedPositions;
  }

  List<CashOperation> operationsForOpenReconstruction(
      Long account, List<CashOperation> currentOperations) {
    Map<Long, CashOperation> operationsById = new LinkedHashMap<>();
    List<CashOperation> existingOperations = cashOperationRepository.findAllByAccount(account);
    if (existingOperations != null) {
      existingOperations.stream()
          .filter(operation -> operation.getId() != null)
          .forEach(operation -> operationsById.put(operation.getId(), operation));
    }
    currentOperations.stream()
        .filter(operation -> operation.getId() != null)
        .forEach(operation -> operationsById.put(operation.getId(), operation));
    return new ArrayList<>(operationsById.values());
  }

  private void appendOpenedPositionsForAccount(
      List<OpenedPosition> output,
      Long account,
      CurrencyType currency,
      Map<String, Deque<Lot>> lotsBySymbol,
      PositionType type) {
    Long accountId = account;
    for (Map.Entry<String, Deque<Lot>> entry : lotsBySymbol.entrySet()) {
      String symbol = entry.getKey();
      int lotIndex = 0;
      for (Lot lot : entry.getValue()) {
        if (lot.volume <= LOT_EPSILON) {
          continue;
        }
        String idKey =
            String.format(
                Locale.ROOT,
                "%s|open|%s|%s|%s|%s|%d",
                accountId,
                symbol,
                type,
                lot.openTime,
                lot.openPrice,
                lotIndex++);

        OpenedPosition position = new OpenedPosition();
        position.setId(syntheticId(idKey));
        position.setAccount(accountId);
        position.setSymbol(symbol);
        position.setType(type);
        position.setCurrency(currency);
        position.setVolume(lot.volume);
        position.setOpenTime(lot.openTime);
        position.setOpenPrice(lot.openPrice);
        position.setSourceOpenPrice(lot.openPrice);
        position.setMarketPrice(lot.openPrice);
        position.setPurchaseValue(lot.volume * lot.openPrice);
        position.setProfit(0.0);
        position.setComment("Reconstructed from Cash Operations");
        output.add(position);
      }
    }
  }

  private void consumeFifo(Deque<Lot> lots, double closeVolume) {
    double remaining = closeVolume;
    while (remaining > LOT_EPSILON && !lots.isEmpty()) {
      Lot lot = lots.peekFirst();
      if (lot.volume <= remaining + LOT_EPSILON) {
        remaining -= lot.volume;
        lots.removeFirst();
      } else {
        lot.volume -= remaining;
        remaining = 0.0;
      }
    }
  }

  private Optional<LotEvent> parseLotEvent(String comment) {
    if (!StringUtils.hasText(comment)) {
      return Optional.empty();
    }
    Matcher matcher = LOT_PATTERN.matcher(comment.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }

    boolean open = "OPEN".equalsIgnoreCase(matcher.group(1));
    PositionType side = PositionType.fromBrokerSideOrBuy(matcher.group(2));
    double volume = parseDouble(matcher.group(3)).orElse(0.0);
    double price = parseDouble(matcher.group(4)).orElse(0.0);
    return Optional.of(new LotEvent(open, side, volume, price));
  }

  private void ensureAssetsExist(
      List<CashOperation> cashOperations,
      List<ClosedPosition> closedPositions,
      List<OpenedPosition> openedPositions,
      CurrencyType currency) {
    List<AssetCatalogService.AssetSeed> assets = new ArrayList<>();
    cashOperations.stream()
        .map(CashOperation::getSymbol)
        .map(symbol -> assetCatalogService.seedForSymbol(symbol, currency))
        .filter(java.util.Objects::nonNull)
        .forEach(assets::add);
    closedPositions.stream()
        .map(ClosedPosition::getSymbol)
        .map(symbol -> assetCatalogService.seedForSymbol(symbol, currency))
        .filter(java.util.Objects::nonNull)
        .forEach(assets::add);
    openedPositions.stream()
        .map(OpenedPosition::getSymbol)
        .map(symbol -> assetCatalogService.seedForSymbol(symbol, currency))
        .filter(java.util.Objects::nonNull)
        .forEach(assets::add);
    assetCatalogService.ensureAssetsExist(assets);
  }

  private void applyAssetIdentities(
      List<CashOperation> cashOperations,
      List<ClosedPosition> closedPositions,
      List<OpenedPosition> openedPositions) {
    Set<String> symbols = new HashSet<>();
    cashOperations.stream().map(CashOperation::getSymbol).filter(StringUtils::hasText).forEach(symbols::add);
    closedPositions.stream().map(ClosedPosition::getSymbol).filter(StringUtils::hasText).forEach(symbols::add);
    openedPositions.stream().map(OpenedPosition::getSymbol).filter(StringUtils::hasText).forEach(symbols::add);
    Map<String, Long> ids = assetRepository.findAllBySymbolIn(symbols).stream()
        .collect(java.util.stream.Collectors.toMap(Asset::getSymbol, Asset::getId));
    cashOperations.forEach(operation -> {
      if (StringUtils.hasText(operation.getSymbol())) {
        operation.setAssetId(requiredAssetId(operation.getSymbol(), ids));
      }
    });
    closedPositions.forEach(position -> position.setAssetId(requiredAssetId(position.getSymbol(), ids)));
    openedPositions.forEach(position -> position.setAssetId(requiredAssetId(position.getSymbol(), ids)));
  }

  private Long requiredAssetId(String symbol, Map<String, Long> ids) {
    Long assetId = ids.get(symbol);
    if (assetId == null) {
      throw new IllegalArgumentException("Unknown or ambiguous XTB asset symbol: " + symbol);
    }
    return assetId;
  }

  private void persistTradePriceHistory(
      List<ClosedPosition> closedPositions,
      List<OpenedPosition> openedPositions,
      CurrencyType defaultCurrency) {
    Set<String> symbols = new HashSet<>();
    closedPositions.stream()
        .map(ClosedPosition::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    openedPositions.stream()
        .map(OpenedPosition::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    Map<String, CurrencyType> quoteCurrencyBySymbol =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .filter(asset -> asset.getCurrency() != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    Asset::getSymbol, Asset::getCurrency, (left, right) -> right));

    Map<PriceCheckpointKey, WeightedPrice> aggregated = new LinkedHashMap<>();

    for (ClosedPosition position : closedPositions) {
      CurrencyType quoteCurrency =
          resolveTradeObservationCurrency(
              position.getSymbol(), position.getPriceCurrency(), defaultCurrency, quoteCurrencyBySymbol);
      Double normalizedOpenPrice =
          normalizeTradeObservationPrice(position.getOpenPrice(), position.getPurchaseValue(), position.getVolume());
      Double normalizedClosePrice =
          normalizeTradeObservationPrice(position.getClosePrice(), position.getSaleValue(), position.getVolume());
      addCheckpoint(
          aggregated,
          position.getSymbol(),
          position.getOpenTime(),
          normalizedOpenPrice,
          position.getVolume(),
          quoteCurrency,
          "XTB_TRADE_OPEN",
          "XTB_TRADE_OPEN",
          "XTB_TRADE_OPEN_OBSERVATION");
      addCheckpoint(
          aggregated,
          position.getSymbol(),
          position.getCloseTime(),
          normalizedClosePrice,
          position.getVolume(),
          quoteCurrency,
          "XTB_TRADE_CLOSE",
          "XTB_TRADE_CLOSE",
          "XTB_TRADE_CLOSE_OBSERVATION");
    }

    for (OpenedPosition position : openedPositions) {
      CurrencyType quoteCurrency =
          resolveTradeObservationCurrency(
              position.getSymbol(), position.getPriceCurrency(), defaultCurrency, quoteCurrencyBySymbol);
      Double normalizedOpenPrice =
          normalizeTradeObservationPrice(position.getOpenPrice(), position.getPurchaseValue(), position.getVolume());
      addCheckpoint(
          aggregated,
          position.getSymbol(),
          position.getOpenTime(),
          normalizedOpenPrice,
          position.getVolume(),
          quoteCurrency,
          "XTB_TRADE_OPEN",
          "XTB_TRADE_OPEN",
          "XTB_TRADE_OPEN_OBSERVATION");
    }

    if (aggregated.isEmpty()) {
      return;
    }

    Set<String> aggregatedSymbols = new HashSet<>();
    aggregated.keySet().forEach(key -> aggregatedSymbols.add(key.symbol()));
    Map<String, Long> assetIdsBySymbol =
        assetRepository.findAllBySymbolIn(aggregatedSymbols).stream()
            .collect(java.util.stream.Collectors.toMap(Asset::getSymbol, Asset::getId, (a, b) -> a));

    for (Map.Entry<PriceCheckpointKey, WeightedPrice> entry : aggregated.entrySet()) {
      Long assetId = assetIdsBySymbol.get(entry.getKey().symbol());
      if (assetId == null) {
        continue;
      }
      WeightedPrice weightedPrice = entry.getValue();
      if (weightedPrice.totalWeight <= 0.0) {
        continue;
      }
      assetPriceHistoryRepository.upsertObservedPrice(
          assetId,
          entry.getKey().priceDate(),
          entry.getKey().source(),
          entry.getKey().symbol(),
          entry.getKey().symbol(),
          entry.getKey().priceOrigin(),
          entry.getKey().currency().name(),
          weightedPrice.weightedAverage(),
          90,
          entry.getKey().qualityClass());
    }
  }

  private void addCheckpoint(
      Map<PriceCheckpointKey, WeightedPrice> aggregated,
      String symbol,
      ZonedDateTime timestamp,
      Double price,
      Double volume,
      CurrencyType currency,
      String source,
      String priceOrigin,
      String qualityClass) {
    if (!StringUtils.hasText(symbol) || timestamp == null || price == null || price <= 0.0) {
      return;
    }
    double weight = volume == null ? 0.0 : Math.abs(volume);
    if (weight <= 0.0) {
      weight = 1.0;
    }
    PriceCheckpointKey key =
        new PriceCheckpointKey(
            symbol,
            timestamp.toLocalDate(),
            java.util.Objects.requireNonNull(currency, "XTB trade price currency"),
            source,
            priceOrigin,
            qualityClass);
    aggregated.computeIfAbsent(key, ignored -> new WeightedPrice()).add(price, weight);
  }

  private void normalizeImportedSymbols(
      List<CashOperation> cashOperations, List<ClosedPosition> closedPositions) {
    List<String> rawSymbols = new ArrayList<>();
    cashOperations.stream().map(CashOperation::getSymbol).filter(StringUtils::hasText).forEach(rawSymbols::add);
    closedPositions.stream().map(ClosedPosition::getSymbol).filter(StringUtils::hasText).forEach(rawSymbols::add);
    Map<String, String> normalized = assetCatalogService.normalizeSymbolsForStorage(rawSymbols);
    cashOperations.forEach(operation -> {
      operation.setBrokerSymbol(operation.getSourceAssetSymbol());
      operation.setSymbol(normalizedSymbol(operation.getSymbol(), normalized));
    });
    closedPositions.forEach(position -> {
      position.setBrokerSymbol(position.getSourceAssetSymbol());
      position.setSymbol(normalizedSymbol(position.getSymbol(), normalized));
    });
  }

  private void applyPositionCurrencies(
      List<ClosedPosition> closedPositions,
      List<OpenedPosition> openedPositions,
      CurrencyType accountCurrency) {
    Set<String> symbols = new HashSet<>();
    closedPositions.stream()
        .map(ClosedPosition::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    openedPositions.stream()
        .map(OpenedPosition::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);

    Map<String, CurrencyType> assetCurrencyBySymbol =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .filter(asset -> asset.getCurrency() != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    Asset::getSymbol, Asset::getCurrency, (left, right) -> left));

    closedPositions.forEach(
        position -> {
          CurrencyType configuredCurrency = assetCurrencyBySymbol.get(position.getSymbol());
          CurrencyType inferredCurrency = currencyForSuffix(position.getSymbol());
          CurrencyType quoteCurrency;
          if (position.getSettlementModel() == PositionSettlementModel.CASH_SETTLED) {
            XtbPositionCurrencyResolver.Resolution resolution =
                positionCurrencyResolver.resolve(
                    position, accountCurrency, configuredCurrency, inferredCurrency);
            if (resolution.normalizePricesToAccountCurrency()) {
              normalizePositionPricesToAccountCurrency(position);
            }
            quoteCurrency = resolution.priceCurrency();
          } else {
            quoteCurrency = firstNonNull(configuredCurrency, inferredCurrency);
          }
          // Supported XTB quote currencies remain instrument quotes. Unsupported source
          // currencies are normalized into account currency while source prices and broker
          // conversion rates remain available for audit. Purchase/sale values, P/L, swap,
          // and commission are account-currency amounts.
          position.setPriceCurrency(quoteCurrency);
          position.setCostCurrency(accountCurrency);
          position.setProfitCurrency(accountCurrency);
          position.setCommissionCurrency(accountCurrency);
        });
    openedPositions.forEach(
        position -> {
          CurrencyType quoteCurrency =
              resolvePositionCurrency(position.getSymbol(), assetCurrencyBySymbol);
          // Reconstructed lots come from "OPEN ... @ price" comments. Their generated
          // purchase value is quantity * quote price, so its cost currency is the quote
          // currency even when the cash account is PLN or EUR.
          position.setPriceCurrency(quoteCurrency);
          position.setCostCurrency(quoteCurrency);
          position.setProfitCurrency(accountCurrency);
          position.setCommissionCurrency(accountCurrency);
        });
  }

  private String normalizedSymbol(String rawSymbol, Map<String, String> normalized) {
    if (!StringUtils.hasText(rawSymbol)) {
      return null;
    }
    return normalized.getOrDefault(rawSymbol, null);
  }

  private CurrencyType resolveTradeObservationCurrency(
      String symbol,
      CurrencyType positionCurrency,
      CurrencyType defaultCurrency,
      Map<String, CurrencyType> quoteCurrencyBySymbol) {
    if (positionCurrency != null) {
      return positionCurrency;
    }
    if (StringUtils.hasText(symbol)) {
      CurrencyType quoteCurrency = quoteCurrencyBySymbol.get(symbol);
      if (quoteCurrency != null) {
        return quoteCurrency;
      }
      AssetCatalogService.AssetSeed inferredSeed = assetCatalogService.seedForSymbol(symbol, null);
      if (inferredSeed != null && inferredSeed.currency() != null) {
        return inferredSeed.currency();
      }
    }
    return firstNonNull(positionCurrency, defaultCurrency);
  }

  private CurrencyType resolvePositionCurrency(
      String symbol, Map<String, CurrencyType> assetCurrencyBySymbol) {
    if (StringUtils.hasText(symbol)) {
      CurrencyType assetCurrency = assetCurrencyBySymbol.get(symbol);
      if (assetCurrency != null) {
        return assetCurrency;
      }
      AssetCatalogService.AssetSeed inferredSeed = assetCatalogService.seedForSymbol(symbol, null);
      if (inferredSeed != null && inferredSeed.currency() != null) {
        return inferredSeed.currency();
      }
    }
    return null;
  }

  private Double normalizeTradeObservationPrice(Double rawPrice, Double grossValue, Double volume) {
    if (rawPrice == null || rawPrice <= 0.0) {
      return rawPrice;
    }
    if (grossValue == null || volume == null || Math.abs(volume) <= LOT_EPSILON) {
      return rawPrice;
    }

    double effectivePrice = Math.abs(grossValue / volume);
    if (effectivePrice <= 0.0) {
      return rawPrice;
    }

    double ratio = rawPrice / effectivePrice;
    if (approximately(ratio, 10.0) || approximately(ratio, 100.0) || approximately(ratio, 1000.0)) {
      return effectivePrice;
    }
    if (approximately(ratio, 0.1) || approximately(ratio, 0.01) || approximately(ratio, 0.001)) {
      return effectivePrice;
    }
    return rawPrice;
  }

  private boolean approximately(double value, double target) {
    return Math.abs(value - target) <= target * 0.05;
  }

  private CurrencyType firstNonNull(CurrencyType first, CurrencyType second) {
    return first != null ? first : second;
  }

  private CurrencyType inferCurrency(
      String sourceName, List<CashOperation> cashOperations, List<ClosedPosition> closedPositions) {
    CurrencyType fromName = inferCurrencyFromSourceName(sourceName);
    if (fromName != null) {
      return fromName;
    }

    Map<CurrencyType, Integer> votes = new EnumMap<>(CurrencyType.class);
    symbols(cashOperations, closedPositions)
        .forEach(
            symbol -> {
              CurrencyType guessed = currencyForSuffix(symbol);
              if (guessed != null) {
                votes.merge(guessed, 1, Integer::sum);
              }
            });
    return votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
  }

  private Set<String> symbols(List<CashOperation> cashOperations, List<ClosedPosition> closedPositions) {
    Set<String> symbols = new HashSet<>();
    cashOperations.stream().map(CashOperation::getSymbol).filter(StringUtils::hasText).forEach(symbols::add);
    closedPositions.stream().map(ClosedPosition::getSymbol).filter(StringUtils::hasText).forEach(symbols::add);
    return symbols;
  }

  private CurrencyType inferCurrencyFromSourceName(String sourceName) {
    if (!StringUtils.hasText(sourceName)) {
      return null;
    }
    String normalized = sourceName.replace('\\', '/');
    String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
    int underscore = baseName.indexOf('_');
    if (underscore <= 0) {
      return null;
    }
    String prefix = baseName.substring(0, underscore).toUpperCase(Locale.ROOT);
    return switch (prefix) {
      case "USD" -> CurrencyType.USD;
      case "EUR" -> CurrencyType.EUR;
      case "PLN" -> CurrencyType.PLN;
      default -> null;
    };
  }

  private CurrencyType currencyForSuffix(String symbol) {
    if (!StringUtils.hasText(symbol)) {
      return null;
    }
    int dot = symbol.lastIndexOf('.');
    if (dot < 0 || dot == symbol.length() - 1) {
      return null;
    }

    String suffix = symbol.substring(dot + 1).trim().toUpperCase(Locale.ROOT);
    return switch (suffix) {
      case "US", "UK" -> CurrencyType.USD;
      case "PL" -> CurrencyType.PLN;
      case "DE", "FR", "NL", "IT", "ES", "FI", "PT", "IE", "AT", "BE" -> CurrencyType.EUR;
      default -> null;
    };
  }

  private String readHeaderValue(Sheet sheet, String key) {
    if (sheet == null) {
      return null;
    }
    for (Row row : sheet) {
      Cell keyCell = row.getCell(0);
      String text = value(keyCell);
      if (key.equalsIgnoreCase(text)) {
        return value(row.getCell(1));
      }
    }
    return null;
  }

  private Map<String, Integer> findHeader(Sheet sheet, String... requiredColumns) {
    if (sheet == null) {
      return Map.of();
    }

    Set<String> required = Set.of(requiredColumns);
    for (Row row : sheet) {
      Map<String, Integer> columns = new HashMap<>();
      for (Cell cell : row) {
        String label = value(cell);
        if (!StringUtils.hasText(label)) {
          continue;
        }
        columns.put(label, cell.getColumnIndex());
      }
      if (columns.keySet().containsAll(required)) {
        columns.put("__row", row.getRowNum());
        return columns;
      }
    }
    return Map.of();
  }

  private List<Row> dataRows(Sheet sheet, Map<String, Integer> columns) {
    Integer headerRow = columns.get("__row");
    if (headerRow == null) {
      return List.of();
    }

    List<Integer> dataIndexes =
        columns.entrySet().stream()
            .filter(entry -> !"__row".equals(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();

    List<Row> rows = new ArrayList<>();
    for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
      Row row = sheet.getRow(i);
      if (row == null) {
        continue;
      }
      if (isDataRowEmpty(row, dataIndexes)) {
        continue;
      }
      rows.add(row);
    }
    return rows;
  }

  private boolean isDataRowEmpty(Row row, Collection<Integer> indexes) {
    for (Integer index : indexes) {
      if (index == null || index < 0) {
        continue;
      }
      if (StringUtils.hasText(value(row.getCell(index)))) {
        return false;
      }
    }
    return true;
  }

  private ZonedDateTime parseDate(Cell cell) {
    if (cell == null) {
      return null;
    }
    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
      return cell.getDateCellValue().toInstant().atZone(UTC);
    }

    String text = value(cell);
    if (!StringUtils.hasText(text)) {
      return null;
    }

    for (DateTimeFormatter formatter : DATE_FORMATTERS) {
      try {
        LocalDateTime localDateTime = LocalDateTime.parse(text, formatter);
        return localDateTime.atZone(UTC);
      } catch (DateTimeParseException ignored) {
        // Try next known date format.
      }
    }
    return null;
  }

  private Optional<Long> parseLong(String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    try {
      return Optional.of((long) Double.parseDouble(value.replace(',', '.')));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  private Long parseAccountId(String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("Missing broker account id");
    }
    return parseLong(value.replaceAll("\\D+", ""))
        .orElseThrow(() -> new IllegalStateException("Invalid broker account id: " + value));
  }

  private Optional<Double> parseDouble(String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Double.parseDouble(value.replace(',', '.')));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  private String cellValue(Row row, Integer index) {
    return index == null ? null : value(row.getCell(index));
  }

  private Cell cell(Row row, Integer index) {
    return index == null ? null : row.getCell(index);
  }

  private String value(Cell cell) {
    if (cell == null || cell.getCellType() == CellType.BLANK) {
      return null;
    }
    String rendered = DATA_FORMATTER.formatCellValue(cell);
    if (!StringUtils.hasText(rendered)) {
      return null;
    }
    return rendered.trim();
  }

  private byte[] readAllBytes(InputStream inputStream) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = inputStream.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private long syntheticId(String key) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
      long value = 0L;
      for (int index = 0; index < Long.BYTES; index++) {
        value = (value << 8) | (hash[index] & 0xffL);
      }
      value &= Long.MAX_VALUE;
      return value == 0L ? -1L : -value;
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Cannot hash XTB row id", exception);
    }
  }

  private void normalizePositionPricesToAccountCurrency(ClosedPosition position) {
    position.setOpenPrice(
        normalizedBrokerPrice(
            position.getOpenPrice(),
            position.getOpenConversionRate(),
            position.getPurchaseValue(),
            position.getVolume(),
            "open",
            position));
    if (position.getClosePrice() != null) {
      position.setClosePrice(
          normalizedBrokerPrice(
              position.getClosePrice(),
              position.getCloseConversionRate(),
              position.getSaleValue(),
              position.getVolume(),
              "close",
              position));
    }
  }

  private Double normalizedBrokerPrice(
      Double price,
      Double conversionRate,
      Double grossValue,
      Double volume,
      String leg,
      ClosedPosition position) {
    if (price == null) {
      return null;
    }
    if (conversionRate != null && conversionRate > 0.0) {
      return price * conversionRate;
    }
    if (grossValue != null && volume != null && Math.abs(volume) > LOT_EPSILON) {
      return Math.abs(grossValue / volume);
    }
    throw new IllegalArgumentException(
        "Cannot normalize XTB "
            + leg
            + " price for account "
            + position.getAccount()
            + ", symbol "
            + position.getSymbol()
            + ", position "
            + position.getSourcePositionId());
  }

  private String normalizeSourcePositionId(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return parseLong(value).map(String::valueOf).orElse(value.trim());
  }

  private String firstNonBlank(String first, String second) {
    if (StringUtils.hasText(first)) {
      return first;
    }
    return StringUtils.hasText(second) ? second : null;
  }

  private static class Lot {
    private double volume;
    private final double openPrice;
    private final ZonedDateTime openTime;

    private Lot(double volume, double openPrice, ZonedDateTime openTime) {
      this.volume = volume;
      this.openPrice = openPrice;
      this.openTime = openTime;
    }
  }

  private record LotEvent(boolean actionOpen, PositionType side, double volume, double price) {}

  private record PriceCheckpointKey(
      String symbol,
      java.time.LocalDate priceDate,
      CurrencyType currency,
      String source,
      String priceOrigin,
      String qualityClass) {}

  private static class WeightedPrice {
    private double weightedPriceSum;
    private double totalWeight;

    private void add(double price, double weight) {
      weightedPriceSum += price * weight;
      totalWeight += weight;
    }

    private double weightedAverage() {
      return totalWeight <= 0.0 ? 0.0 : weightedPriceSum / totalWeight;
    }
  }
}



