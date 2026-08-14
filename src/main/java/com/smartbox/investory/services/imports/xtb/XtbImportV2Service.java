package com.smartbox.investory.services.imports.xtb;

import com.smartbox.investory.infrastructure.CashOperationType;
import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.infrastructure.PositionSettlementModel;
import com.smartbox.investory.infrastructure.PositionType;
import com.smartbox.investory.infrastructure.repository.Asset;
import com.smartbox.investory.infrastructure.repository.AssetPriceHistoryRepository;
import com.smartbox.investory.infrastructure.repository.AssetRepository;
import com.smartbox.investory.infrastructure.repository.CashOperation;
import com.smartbox.investory.infrastructure.repository.CashOperationRepository;
import com.smartbox.investory.infrastructure.repository.ClosedPosition;
import com.smartbox.investory.infrastructure.repository.ClosedPositionRepository;
import com.smartbox.investory.infrastructure.repository.OpenedPosition;
import com.smartbox.investory.infrastructure.repository.OpenedPositionRepository;
import com.smartbox.investory.infrastructure.repository.account.Account;
import com.smartbox.investory.infrastructure.repository.account.AccountRepository;
import com.smartbox.investory.services.AssetCatalogService;
import com.smartbox.investory.services.PositionSettlementModelService;
import com.smartbox.investory.services.currency.CurrencyRateService;
import com.smartbox.investory.services.imports.BrokerSourceRowIdentity;
import com.smartbox.investory.services.imports.ImportEvidenceContext;
import com.smartbox.investory.services.imports.ImportExecutionResult;
import com.smartbox.investory.services.imports.ImportSourceEvidenceService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@Transactional
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
  private final AccountRepository accountRepository;
  private final AssetCatalogService assetCatalogService;
  private final PositionSettlementModelService positionSettlementModelService;
  private final XtbPositionCurrencyResolver positionCurrencyResolver;
  private final CurrencyRateService currencyRateService;
  private final ImportSourceEvidenceService sourceEvidenceService;

  @Autowired
  public XtbImportV2Service(
      ClosedPositionRepository closedPositionRepository,
      OpenedPositionRepository openedPositionRepository,
      CashOperationRepository cashOperationRepository,
      AssetPriceHistoryRepository assetPriceHistoryRepository,
      AssetRepository assetRepository,
      AccountRepository accountRepository,
      AssetCatalogService assetCatalogService,
      PositionSettlementModelService positionSettlementModelService,
      XtbPositionCurrencyResolver positionCurrencyResolver,
      CurrencyRateService currencyRateService,
      ImportSourceEvidenceService sourceEvidenceService) {
    this.closedPositionRepository = closedPositionRepository;
    this.openedPositionRepository = openedPositionRepository;
    this.cashOperationRepository = cashOperationRepository;
    this.assetPriceHistoryRepository = assetPriceHistoryRepository;
    this.assetRepository = assetRepository;
    this.accountRepository = accountRepository;
    this.assetCatalogService = assetCatalogService;
    this.positionSettlementModelService = positionSettlementModelService;
    this.positionCurrencyResolver = positionCurrencyResolver;
    this.currencyRateService = currencyRateService;
    this.sourceEvidenceService = sourceEvidenceService;
  }

  /** Source-compatible constructor for unit tests that exercise parsing without orchestration. */
  public XtbImportV2Service(
      ClosedPositionRepository closedPositionRepository,
      OpenedPositionRepository openedPositionRepository,
      CashOperationRepository cashOperationRepository,
      AssetPriceHistoryRepository assetPriceHistoryRepository,
      AssetRepository assetRepository,
      AccountRepository accountRepository,
      AssetCatalogService assetCatalogService,
      PositionSettlementModelService positionSettlementModelService,
      XtbPositionCurrencyResolver positionCurrencyResolver,
      CurrencyRateService currencyRateService) {
    this(
        closedPositionRepository,
        openedPositionRepository,
        cashOperationRepository,
        assetPriceHistoryRepository,
        assetRepository,
        accountRepository,
        assetCatalogService,
        positionSettlementModelService,
        positionCurrencyResolver,
        currencyRateService,
        null);
  }

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

  /** Imports the whole ZIP as one transaction; one workbook failure rolls back the ZIP. */
  public ImportExecutionResult importZip(InputStream zipInputStream, String sourceName)
      throws Exception {
    int total = 0;
    int applied = 0;
    int failed = 0;
    int files = 0;
    List<String> importedAccounts = new ArrayList<>();

    try (ZipInputStream zip = new ZipInputStream(zipInputStream)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
          continue;
        }
        byte[] workbookBytes = readAllBytes(zip);
        ImportEvidenceContext.archiveMember(entry.getName());
        ImportExecutionResult partial =
            importWorkbook(new ByteArrayInputStream(workbookBytes), entry.getName());
        total += partial.rowsTotal();
        applied += partial.rowsApplied();
        failed += partial.rowsFailed();
        files++;
        importedAccounts.add(partial.details());
      }
    }

    if (files == 0) {
      throw new IllegalArgumentException("XTB v2 zip has no .xlsx files: " + sourceName);
    }

    String details =
        String.format(
            "XTB v2 %s: imported %d workbook(s), rows=%d applied=%d failed=%d [%s]",
            sourceName != null ? sourceName : "zip",
            files,
            total,
            applied,
            failed,
            String.join(", ", importedAccounts));
    return new ImportExecutionResult(total, applied, failed, details);
  }

  public ImportExecutionResult importWorkbook(InputStream xlsxInputStream, String sourceName)
      throws Exception {
    long started = System.nanoTime();
    try {
      return importWorkbookBody(xlsxInputStream, sourceName);
    } finally {
      // This is inside the XTB transaction; the orchestrator logs transaction completion too.
      log.info(
          "IMPORT PERF parser-body={}ms source={}",
          (System.nanoTime() - started) / 1_000_000L,
          sourceName);
    }
  }

  private ImportExecutionResult importWorkbookBody(InputStream xlsxInputStream, String sourceName)
      throws Exception {
    try (Workbook workbook = new XSSFWorkbook(xlsxInputStream)) {
      if (!supports(workbook)) {
        throw new IllegalArgumentException("Not an XTB v2 workbook: " + sourceName);
      }

      Sheet cashSheet = workbook.getSheet("Cash Operations");
      Sheet closedSheet = workbook.getSheet("Closed Positions");

      String accountRaw =
          firstNonBlank(
              readHeaderValue(cashSheet, "Account number"),
              readHeaderValue(closedSheet, "Account"));
      if (!StringUtils.hasText(accountRaw)) {
        throw new IllegalStateException("XTB v2 workbook has no account number: " + sourceName);
      }
      Long externalAccountId = parseAccountId(accountRaw);
      Account accountConfiguration = accountConfiguration(externalAccountId, sourceName);
      Long account = accountConfiguration.getId();
      boolean cashOnly = accountConfiguration.isCashOnly();

      Map<String, Integer> cashColumns = findHeader(cashSheet, "Type", "Time", "Amount");
      Map<String, Integer> closedColumns = findHeader(closedSheet, "Ticker", "Type", "Volume");

      List<CashOperation> cashOperations =
          parseCashOperations(cashSheet, cashColumns, account, sourceName, cashOnly);
      List<ClosedPosition> closedPositions =
          cashOnly
              ? List.of()
              : parseClosedPositions(closedSheet, closedColumns, account, sourceName);
      normalizeImportedSymbols(cashOperations, closedPositions);
      CurrencyType currency = accountConfiguration.getCurrency();

      List<OpenedPosition> openedPositions =
          cashOnly
              ? List.of()
              : reconstructOpenedPositions(
                  operationsForOpenReconstruction(account, cashOperations), account, currency);
      openedPositions = deduplicateOpenedPositions(openedPositions);

      applyPositionCurrencies(closedPositions, openedPositions, currency);

      ensureAssetsExist(cashOperations, closedPositions, openedPositions, currency);
      applyAssetIdentities(cashOperations, closedPositions, openedPositions);
      persistTradePriceHistory(closedPositions, openedPositions, currency);

      cashOperationRepository.saveAll(cashOperations);
      currencyRateService.harvestXtbExecutionRates(cashOperations);
      closedPositionRepository.saveAll(closedPositions);
      if (openedPositions.isEmpty()) {
        openedPositionRepository.deleteByAccount(account);
      } else {
        openedPositionRepository.removeAllByAccountNotIn(account, openedPositions);
        openedPositionRepository.saveAll(openedPositions);
      }
      int total = cashOperations.size() + closedPositions.size();
      String details =
          String.format(
              "acc=%s cash=%d closed=%d open=%d",
              account, cashOperations.size(), closedPositions.size(), openedPositions.size());
      return new ImportExecutionResult(total, total, 0, details);
    }
  }

  private boolean supports(Workbook workbook) {
    return workbook.getSheet("Cash Operations") != null
        && workbook.getSheet("Closed Positions") != null;
  }

  private List<CashOperation> parseCashOperations(
      Sheet sheet,
      Map<String, Integer> columns,
      Long account,
      String sourceName,
      boolean cashOnly) {
    if (sheet == null || CollectionUtils.isEmpty(columns)) {
      return List.of();
    }

    List<CashOperation> operations = new ArrayList<>();
    Map<String, Integer> sourceRowOccurrences = new HashMap<>();
    for (Row row : dataRows(sheet, columns)) {
      String symbol = sourceTicker(row, columns);
      if (cashOnly && StringUtils.hasText(symbol)) {
        continue;
      }
      ZonedDateTime operationDate = parseDate(cell(row, columns.get("Time")));
      if (operationDate == null) {
        if (hasCashRowData(row, columns)) {
          throw new IllegalArgumentException(
              "XTB cash row " + row.getRowNum() + " has no valid timestamp");
        }
        continue;
      }

      CashOperation operation = new CashOperation();
      String brokerId = cellValue(row, columns.get("ID"));
      operation.setAccount(account);
      String comment = cellValue(row, columns.get("Comment"));
      CashOperationType type = CashOperationType.fromString(cellValue(row, columns.get("Type")));
      if (type == CashOperationType.UNKNOWN && comment != null) {
        type = CashOperationType.fromString(comment);
      }
      if (type == CashOperationType.UNKNOWN) {
        throw new IllegalArgumentException(
            "Unknown XTB cash operation type at row " + row.getRowNum());
      }
      operation.setType(type);
      operation.setSymbol(symbol);
      Double amount = parseDouble(cellValue(row, columns.get("Amount"))).orElse(null);
      operation.setAmount(amount);
      operation.setComment(comment);
      operation.setCurrency(
          resolveCashOperationCurrency(row, columns, comment, sourceName, amount, row.getRowNum()));
      operation.setDate(operationDate);
      String sourceFingerprint =
          cashSourceFingerprint(
              account,
              type,
              symbol,
              operationDate,
              amount,
              operation.getCurrency(),
              comment,
              brokerId);
      operation.setId(
          parseLong(brokerId)
              .orElseGet(
                  () ->
                      BrokerSourceRowIdentity.id(
                          sourceFingerprint,
                          sourceRowOccurrences.merge(sourceFingerprint, 1, Integer::sum))));
      int occurrence = sourceRowOccurrences.getOrDefault(sourceFingerprint, 1);
      Long sourceRowId =
          sourceEvidenceService == null
              ? null
              : sourceEvidenceService.recordRow(
                  "Cash Operations",
                  sheet.getSheetName(),
                  row.getRowNum() + 1,
                  StringUtils.hasText(brokerId) ? brokerId : null,
                  occurrence,
                  rawRowText(row, columns),
                  rawRowValues(row, columns));
      ImportEvidenceContext context = ImportEvidenceContext.current();
      operation.setImportSourceRowId(sourceRowId);
      operation.setImportHistoryId(context == null ? null : context.importHistoryId());
      operations.add(operation);
    }
    return operations;
  }

  private String cashSourceFingerprint(
      Long account,
      CashOperationType type,
      String symbol,
      ZonedDateTime timestamp,
      Double amount,
      CurrencyType currency,
      String comment,
      String brokerId) {
    return String.join(
        "|",
        "XTB",
        BrokerSourceRowIdentity.part(account),
        BrokerSourceRowIdentity.part(type),
        BrokerSourceRowIdentity.part(symbol),
        BrokerSourceRowIdentity.part(timestamp),
        BrokerSourceRowIdentity.part(amount),
        BrokerSourceRowIdentity.part(currency),
        BrokerSourceRowIdentity.part(comment),
        BrokerSourceRowIdentity.part(brokerId));
  }

  private CurrencyType resolveCashOperationCurrency(
      Row row,
      Map<String, Integer> columns,
      String comment,
      String sourceName,
      Double amount,
      int rowNumber) {
    CurrencyType rowCurrency =
        parseCurrency(
            firstNonBlank(
                cellValue(row, columns.get("Currency")),
                firstNonBlank(
                    cellValue(row, columns.get("Amount Currency")),
                    cellValue(row, columns.get("Currency of amount")))));
    if (rowCurrency != null) {
      return rowCurrency;
    }
    Matcher conversion =
        Pattern.compile("(?i)currency conversion,?\\s*([A-Z]{3})\\s+to\\s+([A-Z]{3})")
            .matcher(comment == null ? "" : comment);
    if (conversion.find()) {
      CurrencyType source = parseCurrency(conversion.group(1));
      CurrencyType target = parseCurrency(conversion.group(2));
      if (source != null && target != null && amount != null && amount != 0.0) {
        return amount < 0.0 ? source : target;
      }
    }
    CurrencyType reportCurrency = inferCurrencyFromSourceName(sourceName);
    if (reportCurrency != null) {
      return reportCurrency;
    }
    throw new IllegalArgumentException(
        "XTB cash row " + rowNumber + " has no source monetary currency");
  }

  private CurrencyType parseCurrency(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return CurrencyType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private List<ClosedPosition> parseClosedPositions(
      Sheet sheet, Map<String, Integer> columns, Long account, String sourceName) {
    if (sheet == null || CollectionUtils.isEmpty(columns)) {
      return List.of();
    }

    List<ClosedPosition> positions = new ArrayList<>();
    Map<String, Integer> sourceRowOccurrences = new HashMap<>();
    for (Row row : dataRows(sheet, columns)) {
      String symbol = sourceTicker(row, columns);
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
      Double purchaseValue =
          parseDouble(cellValue(row, columns.get("Purchase Value"))).orElse(null);
      Double saleValue = parseDouble(cellValue(row, columns.get("Sale Value"))).orElse(null);
      Double commission = parseDouble(cellValue(row, columns.get("Commission"))).orElse(null);
      Double margin = parseDouble(cellValue(row, columns.get("Margin"))).orElse(null);
      Double swap = parseDouble(cellValue(row, columns.get("Swap"))).orElse(null);
      Double profit =
          parseDouble(cellValue(row, columns.get("Profit/Loss")))
              .or(() -> parseDouble(cellValue(row, columns.get("Gross Profit"))))
              .orElse(null);
      String product =
          firstNonBlank(
              cellValue(row, columns.get("Category")), cellValue(row, columns.get("Product")));
      String sourcePositionId =
          normalizeSourcePositionId(cellValue(row, columns.get("Position ID")));
      Double openConversionRate =
          parseDouble(cellValue(row, columns.get("Open Conversion Rate"))).orElse(null);
      Double closeConversionRate =
          parseDouble(cellValue(row, columns.get("Close Conversion Rate"))).orElse(null);

      if (openTime == null || closeTime == null) {
        throw new IllegalArgumentException(
            "XTB closed-position row " + row.getRowNum() + " has an invalid timestamp");
      }

      String sourceIdentity =
          closedPositionSourceIdentity(
              account, sourcePositionId, symbol, typeText, volume, openTime, closeTime, product);
      int sourceRowOccurrence = sourceRowOccurrences.merge(sourceIdentity, 1, Integer::sum);

      ClosedPosition position = new ClosedPosition();
      position.setId(BrokerSourceRowIdentity.id(sourceIdentity, sourceRowOccurrence));
      Long sourceRowId =
          sourceEvidenceService == null
              ? null
              : sourceEvidenceService.recordRow(
                  "Closed Positions",
                  sheet.getSheetName(),
                  row.getRowNum() + 1,
                  sourcePositionId,
                  sourceRowOccurrence,
                  rawRowText(row, columns),
                  rawRowValues(row, columns));
      ImportEvidenceContext context = ImportEvidenceContext.current();
      position.setImportSourceRowId(sourceRowId);
      position.setImportHistoryId(context == null ? null : context.importHistoryId());
      position.setAccount(account);
      position.setSourcePositionId(sourcePositionId);
      position.setSourceRowOccurrence(sourceRowOccurrence);
      position.setSymbol(symbol);
      position.setType(parsePositionType(typeText, row.getRowNum()));
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

  private String closedPositionSourceIdentity(
      Long account,
      String sourcePositionId,
      String symbol,
      String typeText,
      Double volume,
      ZonedDateTime openTime,
      ZonedDateTime closeTime,
      String product) {
    return String.join(
        "|",
        "XTB",
        "CLOSED",
        BrokerSourceRowIdentity.part(account),
        BrokerSourceRowIdentity.part(sourcePositionId),
        BrokerSourceRowIdentity.part(symbol),
        BrokerSourceRowIdentity.part(typeText),
        BrokerSourceRowIdentity.part(volume),
        BrokerSourceRowIdentity.part(openTime),
        BrokerSourceRowIdentity.part(closeTime),
        BrokerSourceRowIdentity.part(product));
  }

  List<OpenedPosition> reconstructOpenedPositions(
      List<CashOperation> cashOperations, Long account, CurrencyType currency) {
    Map<String, Deque<Lot>> buyLots = new HashMap<>();
    Map<String, Deque<Lot>> sellLots = new HashMap<>();

    cashOperations.stream()
        .filter(
            op ->
                op.getType() == CashOperationType.STOCK_PURCHASE
                    || op.getType() == CashOperationType.STOCK_SELL)
        .filter(op -> StringUtils.hasText(op.getSymbol()))
        .sorted(
            Comparator.comparing(
                    CashOperation::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    CashOperation::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .forEach(
            op -> {
              LotEvent event = parseLotEvent(op.getComment()).orElse(null);
              if (event == null || event.volume <= LOT_EPSILON) {
                return;
              }

              Map<String, Deque<Lot>> openLots =
                  event.side == PositionType.BUY ? buyLots : sellLots;
              Deque<Lot> lots =
                  openLots.computeIfAbsent(op.getSymbol(), ignored -> new ArrayDeque<>());

              if (event.actionOpen) {
                lots.addLast(
                    new Lot(
                        event.volume,
                        event.price,
                        op.getDate(),
                        op.getImportHistoryId(),
                        op.getImportSourceRowId()));
              } else {
                consumeFifo(lots, event.volume);
              }
            });

    List<OpenedPosition> openedPositions = new ArrayList<>();
    appendOpenedPositionsForAccount(openedPositions, account, currency, buyLots, PositionType.BUY);
    appendOpenedPositionsForAccount(
        openedPositions, account, currency, sellLots, PositionType.SELL);
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
        position.setId(BrokerSourceRowIdentity.id(idKey, 1));
        position.setAccount(accountId);
        position.setSymbol(symbol);
        position.setType(type);
        position.setPriceCurrency(currency);
        position.setCostCurrency(currency);
        position.setProfitCurrency(currency);
        position.setCommissionCurrency(currency);
        position.setVolume(lot.volume);
        position.setOpenTime(lot.openTime);
        position.setOpenPrice(lot.openPrice);
        position.setSourceOpenPrice(lot.openPrice);
        position.setImportHistoryId(lot.importHistoryId);
        position.setImportSourceRowId(lot.sourceRowId);
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
    PositionType side = parsePositionType(matcher.group(2), -1);
    double volume = parseDouble(matcher.group(3)).orElseThrow();
    double price = parseDouble(matcher.group(4)).orElseThrow();
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
    cashOperations.stream()
        .map(CashOperation::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    closedPositions.stream()
        .map(ClosedPosition::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    openedPositions.stream()
        .map(OpenedPosition::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    Map<String, Long> ids =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .collect(java.util.stream.Collectors.toMap(Asset::getSymbol, Asset::getId));
    cashOperations.forEach(
        operation -> {
          if (StringUtils.hasText(operation.getSymbol())) {
            operation.setAssetId(requiredAssetId(operation.getSymbol(), ids));
          }
        });
    closedPositions.forEach(
        position -> position.setAssetId(requiredAssetId(position.getSymbol(), ids)));
    openedPositions.forEach(
        position -> position.setAssetId(requiredAssetId(position.getSymbol(), ids)));
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
              position.getSymbol(),
              position.getPriceCurrency(),
              defaultCurrency,
              quoteCurrencyBySymbol);
      Double normalizedOpenPrice =
          normalizeTradeObservationPrice(
              position.getOpenPrice(), position.getPurchaseValue(), position.getVolume());
      Double normalizedClosePrice =
          normalizeTradeObservationPrice(
              position.getClosePrice(), position.getSaleValue(), position.getVolume());
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
              position.getSymbol(),
              position.getPriceCurrency(),
              defaultCurrency,
              quoteCurrencyBySymbol);
      Double normalizedOpenPrice =
          normalizeTradeObservationPrice(
              position.getOpenPrice(), position.getPurchaseValue(), position.getVolume());
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
            .filter(asset -> !Boolean.TRUE.equals(asset.getExcludeFromImport()))
            .collect(
                java.util.stream.Collectors.toMap(Asset::getSymbol, Asset::getId, (a, b) -> a));

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
          BigDecimal.valueOf(weightedPrice.weightedAverage()),
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
    cashOperations.stream()
        .map(CashOperation::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(rawSymbols::add);
    closedPositions.stream()
        .map(ClosedPosition::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(rawSymbols::add);
    Map<String, String> normalized = assetCatalogService.normalizeSymbolsForStorage(rawSymbols);
    cashOperations.forEach(
        operation -> {
          operation.setBrokerSymbol(operation.getSourceAssetSymbol());
          operation.setSymbol(normalizedSymbol(operation.getSymbol(), normalized));
        });
    closedPositions.forEach(
        position -> {
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

  private Account accountConfiguration(Long externalAccountId, String sourceName) {
    Account account =
        accountRepository
            .findByProviderIgnoreCaseAndExternalAccountId("XTB", String.valueOf(externalAccountId))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "XTB account " + externalAccountId + " is not configured: " + sourceName));
    if (!"XTB".equalsIgnoreCase(account.getProvider())) {
      throw new IllegalStateException(
          "Account "
              + externalAccountId
              + " is configured for provider "
              + account.getProvider()
              + ", not XTB");
    }
    CurrencyType configuredCurrency = account.getCurrency();
    if (configuredCurrency == null) {
      throw new IllegalStateException(
          "XTB account " + externalAccountId + " has no configured currency");
    }
    return account;
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
      case "IKE" -> CurrencyType.PLN;
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
      return cell.getLocalDateTimeCellValue().atZone(UTC);
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
    throw new IllegalArgumentException("Malformed XTB date: " + text);
  }

  private Optional<Long> parseLong(String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    try {
      return Optional.of((long) Double.parseDouble(value.replace(',', '.')));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Malformed XTB number: " + value, exception);
    }
  }

  private PositionType parsePositionType(String value, int rowNumber) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("Missing XTB position side at row " + rowNumber);
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (normalized.contains("SELL") || normalized.contains("SHORT")) {
      return PositionType.SELL;
    }
    if (normalized.contains("BUY") || normalized.contains("LONG")) {
      return PositionType.BUY;
    }
    throw new IllegalArgumentException("Unknown XTB position side: " + value);
  }

  private boolean hasCashRowData(Row row, Map<String, Integer> columns) {
    String type = cellValue(row, columns.get("Type"));
    String id = cellValue(row, columns.get("ID"));
    if (!StringUtils.hasText(id)
        && StringUtils.hasText(type)
        && Set.of("TOTAL", "SUMMARY").contains(type.trim().toUpperCase(Locale.ROOT))) {
      return false;
    }
    return List.of("ID", "Type", "Ticker", "Comment").stream()
        .map(columns::get)
        .map(index -> index == null ? null : value(row.getCell(index)))
        .anyMatch(StringUtils::hasText);
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
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Malformed XTB number: " + value, exception);
    }
  }

  /**
   * Reads an optional XTB security ticker while preserving the broker's real export semantics.
   *
   * <p>Current XTB v2 workbooks use the literal value {@code "3"} as an N/A placeholder on
   * non-instrument cash rows. In those rows Instrument, Ticker, Category and Position ID are all
   * {@code "3"}. Treating that placeholder as a real ticker makes fresh imports try to resolve an
   * asset named {@code "3"}; on cash-only accounts it also incorrectly drops deposits, transfers,
   * interest and taxes because the account filter thinks the row references a security.
   *
   * <p>Only collapse {@code "3"} when the workbook exposes the accompanying XTB metadata columns
   * and all of them also look like N/A placeholders. A genuine ticker named {@code "3"} in a
   * different source shape is therefore left untouched.
   */
  private String sourceTicker(Row row, Map<String, Integer> columns) {
    String ticker = cellValue(row, columns.get("Ticker"));
    if (!StringUtils.hasText(ticker) || !"3".equals(ticker.trim())) {
      return ticker;
    }

    boolean hasXtbPlaceholderShape =
        columns.containsKey("Instrument")
            || columns.containsKey("Category")
            || columns.containsKey("Position ID");
    if (!hasXtbPlaceholderShape) {
      return ticker;
    }

    return isXtbBlankSentinel(cellValue(row, columns.get("Instrument")))
            && isXtbBlankSentinel(cellValue(row, columns.get("Category")))
            && isXtbBlankSentinel(cellValue(row, columns.get("Position ID")))
        ? null
        : ticker;
  }

  private boolean isXtbBlankSentinel(String value) {
    return !StringUtils.hasText(value) || "3".equals(value.trim());
  }

  private String cellValue(Row row, Integer index) {
    return index == null ? null : value(row.getCell(index));
  }

  private Map<String, String> rawRowValues(Row row, Map<String, Integer> columns) {
    Map<String, String> values = new LinkedHashMap<>();
    columns.forEach((name, index) -> values.put(name, cellValue(row, index)));
    return values;
  }

  private String rawRowText(Row row, Map<String, Integer> columns) {
    return String.join(
        "\t", columns.keySet().stream().map(name -> cellValue(row, columns.get(name))).toList());
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
    private final Long importHistoryId;
    private final Long sourceRowId;

    private Lot(
        double volume,
        double openPrice,
        ZonedDateTime openTime,
        Long importHistoryId,
        Long sourceRowId) {
      this.volume = volume;
      this.openPrice = openPrice;
      this.openTime = openTime;
      this.importHistoryId = importHistoryId;
      this.sourceRowId = sourceRowId;
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
