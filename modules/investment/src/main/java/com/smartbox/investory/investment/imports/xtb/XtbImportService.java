package com.smartbox.investory.investment.imports.xtb;

import com.smartbox.investory.investment.imports.BrokerSourceRowIdentity;
import com.smartbox.investory.investment.imports.ImportEvidenceContext;
import com.smartbox.investory.investment.imports.ImportExecutionResult;
import com.smartbox.investory.investment.imports.ImportPortfolioContext;
import com.smartbox.investory.investment.imports.ImportSourceEvidenceService;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.ledger.asset.AssetCatalogService;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationRepository;
import com.smartbox.investory.investment.ledger.position.PositionSettlementModel;
import com.smartbox.investory.investment.ledger.position.PositionSettlementModelService;
import com.smartbox.investory.investment.ledger.position.PositionType;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
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
public class XtbImportService {

  private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.ROOT);
  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final Pattern LOT_PATTERN =
      Pattern.compile(
          "^(OPEN|CLOSE)\\s+(BUY|SELL)\\s+([0-9]+(?:[\\.,][0-9]+)?)(?:/[0-9]+(?:[\\.,][0-9]+)?)?\\s+@\\s+([0-9]+(?:[\\.,][0-9]+)?)$",
          Pattern.CASE_INSENSITIVE);
  private static final List<DateTimeFormatter> DATE_FORMATTERS =
      List.of(
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

  private final PositionRepository closedPositionRepository;
  private final PositionRepository openedPositionRepository;
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
  public XtbImportService(
      PositionRepository closedPositionRepository,
      PositionRepository openedPositionRepository,
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

    Path temporaryZip = Files.createTempFile("investory-xtb-", ".zip");
    try {
      Files.copy(zipInputStream, temporaryZip, StandardCopyOption.REPLACE_EXISTING);
      try (ZipFile zip = new ZipFile(temporaryZip.toFile())) {
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            continue;
          }
          ImportEvidenceContext.archiveMember(entry.getName());
          ImportExecutionResult partial =
              importWorkbook(zip.getInputStream(entry), entry.getName());
          total += partial.rowsTotal();
          applied += partial.rowsApplied();
          failed += partial.rowsFailed();
          files++;
          importedAccounts.add(partial.details());
        }
      }
    } finally {
      Files.deleteIfExists(temporaryZip);
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
              firstNonBlank(
                  XtbWorkbookReader.readHeaderValue(cashSheet, "AccountEntity number"),
                  XtbWorkbookReader.readHeaderValue(cashSheet, "Account number")),
              firstNonBlank(
                  XtbWorkbookReader.readHeaderValue(closedSheet, "Account number"),
                  XtbWorkbookReader.readHeaderValue(closedSheet, "AccountEntity")));
      if (!StringUtils.hasText(accountRaw)) {
        throw new IllegalStateException("XTB v2 workbook has no account number: " + sourceName);
      }
      Long externalAccountId = parseAccountId(accountRaw);
      AccountEntity accountConfiguration = accountConfiguration(externalAccountId, sourceName);
      Long account = accountConfiguration.getId();
      boolean cashOnly = accountConfiguration.isCashOnly();

      Map<String, Integer> cashColumns =
          XtbWorkbookReader.findHeader(cashSheet, "Type", "Time", "Amount");
      Map<String, Integer> closedColumns =
          XtbWorkbookReader.findHeader(closedSheet, "Ticker", "Type", "Volume");

      List<CashOperationEntity> cashOperations =
          parseCashOperations(cashSheet, cashColumns, account, sourceName, cashOnly);
      List<PositionEntity> closedPositions =
          cashOnly
              ? List.of()
              : parsePositionEntities(closedSheet, closedColumns, account, sourceName);
      normalizeImportedSymbols(cashOperations, closedPositions);
      CurrencyType currency = accountConfiguration.getCurrency();

      List<PositionEntity> openedPositions =
          cashOnly
              ? List.of()
              : reconstructPositionEntities(
                  operationsForOpenReconstruction(account, cashOperations), account, currency);
      openedPositions = deduplicatePositionEntities(openedPositions);

      applyPositionCurrencies(closedPositions, openedPositions, currency);

      ensureAssetsExist(cashOperations, closedPositions, openedPositions, currency);
      applyAssetIdentities(cashOperations, closedPositions, openedPositions);
      persistTradePriceHistory(closedPositions, openedPositions, currency);

      cashOperationRepository.saveAll(cashOperations);
      currencyRateService.harvestXtbExecutionRates(cashOperations);
      closedPositionRepository.saveAll(closedPositions);
      if (openedPositions.isEmpty()) {
        openedPositionRepository.deleteOpenByAccount(account);
      } else {
        openedPositionRepository.removeOpenByAccountNotIn(account, openedPositions);
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

  private List<CashOperationEntity> parseCashOperations(
      Sheet sheet,
      Map<String, Integer> columns,
      Long account,
      String sourceName,
      boolean cashOnly) {
    if (sheet == null || CollectionUtils.isEmpty(columns)) {
      return List.of();
    }

    List<CashOperationEntity> operations = new ArrayList<>();
    Map<String, Integer> sourceRowOccurrences = new HashMap<>();
    for (Row row : XtbWorkbookReader.dataRows(sheet, columns)) {
      String symbol = XtbWorkbookReader.sourceTicker(row, columns);
      if (cashOnly && StringUtils.hasText(symbol)) {
        continue;
      }
      ZonedDateTime operationDate =
          XtbWorkbookReader.parseDate(XtbWorkbookReader.cell(row, columns.get("Time")));
      if (operationDate == null) {
        if (XtbWorkbookReader.hasCashRowData(row, columns)) {
          throw new IllegalArgumentException(
              "XTB cash row " + row.getRowNum() + " has no valid timestamp");
        }
        continue;
      }

      CashOperationEntity operation = new CashOperationEntity();
      String brokerId = XtbWorkbookReader.cellValue(row, columns.get("ID"));
      operation.setAccount(account);
      String comment = XtbWorkbookReader.cellValue(row, columns.get("Comment"));
      CashOperationType type =
          CashOperationType.fromString(XtbWorkbookReader.cellValue(row, columns.get("Type")));
      if (type == CashOperationType.UNKNOWN && comment != null) {
        type = CashOperationType.fromString(comment);
      }
      if (type == CashOperationType.UNKNOWN) {
        throw new IllegalArgumentException(
            "Unknown XTB cash operation type at row " + row.getRowNum());
      }
      operation.setType(type);
      operation.setSymbol(symbol);
      BigDecimal amount =
          XtbWorkbookReader.parseDecimal(XtbWorkbookReader.cellValue(row, columns.get("Amount")))
              .orElse(null);
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
          XtbWorkbookReader.parseLong(brokerId)
              .orElseGet(
                  () ->
                      BrokerSourceRowIdentity.id(
                          sourceFingerprint,
                          sourceRowOccurrences.merge(sourceFingerprint, 1, Integer::sum))));
      int occurrence = sourceRowOccurrences.getOrDefault(sourceFingerprint, 1);
      Long sourceRowId =
          sourceEvidenceService.recordRow(
              "Cash Operations",
              sheet.getSheetName(),
              row.getRowNum() + 1,
              StringUtils.hasText(brokerId) ? brokerId : null,
              occurrence,
              XtbWorkbookReader.rawRowText(row, columns),
              XtbWorkbookReader.rawRowValues(row, columns));
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
      BigDecimal amount,
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
      BigDecimal amount,
      int rowNumber) {
    CurrencyType rowCurrency =
        parseCurrency(
            firstNonBlank(
                XtbWorkbookReader.cellValue(row, columns.get("Currency")),
                firstNonBlank(
                    XtbWorkbookReader.cellValue(row, columns.get("Amount Currency")),
                    XtbWorkbookReader.cellValue(row, columns.get("Currency of amount")))));
    if (rowCurrency != null) {
      return rowCurrency;
    }
    Matcher conversion =
        Pattern.compile("(?i)currency conversion,?\\s*([A-Z]{3})\\s+to\\s+([A-Z]{3})")
            .matcher(comment == null ? "" : comment);
    if (conversion.find()) {
      CurrencyType source = parseCurrency(conversion.group(1));
      CurrencyType target = parseCurrency(conversion.group(2));
      if (source != null && target != null && amount != null && amount.signum() != 0) {
        return amount.signum() < 0 ? source : target;
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

  private List<PositionEntity> parsePositionEntities(
      Sheet sheet, Map<String, Integer> columns, Long account, String sourceName) {
    if (sheet == null || CollectionUtils.isEmpty(columns)) {
      return List.of();
    }

    List<PositionEntity> positions = new ArrayList<>();
    Map<String, Integer> sourceRowOccurrences = new HashMap<>();
    for (Row row : XtbWorkbookReader.dataRows(sheet, columns)) {
      String symbol = XtbWorkbookReader.sourceTicker(row, columns);
      // XTB sheets can end with a totals/footer row. It contains numeric values in
      // known columns but is not a position and has no ticker.
      if (!StringUtils.hasText(symbol)) {
        continue;
      }
      String typeText = XtbWorkbookReader.cellValue(row, columns.get("Type"));
      BigDecimal volume =
          XtbWorkbookReader.parseDecimal(XtbWorkbookReader.cellValue(row, columns.get("Volume")))
              .orElse(null);
      ZonedDateTime openTime =
          XtbWorkbookReader.parseDate(XtbWorkbookReader.cell(row, columns.get("Open Time (UTC)")));
      ZonedDateTime closeTime =
          XtbWorkbookReader.parseDate(XtbWorkbookReader.cell(row, columns.get("Close Time (UTC)")));
      BigDecimal openPrice =
          XtbWorkbookReader.parseDecimal(
                  XtbWorkbookReader.cellValue(row, columns.get("Open Price")))
              .orElse(null);
      BigDecimal closePrice =
          XtbWorkbookReader.parseDecimal(
                  XtbWorkbookReader.cellValue(row, columns.get("Close Price")))
              .orElse(null);
      BigDecimal purchaseValue =
          XtbWorkbookReader.parseDecimal(
                  XtbWorkbookReader.cellValue(row, columns.get("Purchase Value")))
              .orElse(null);
      BigDecimal saleValue =
          XtbWorkbookReader.parseDecimal(
                  XtbWorkbookReader.cellValue(row, columns.get("Sale Value")))
              .orElse(null);
      BigDecimal commission =
          XtbWorkbookReader.parseDecimal(
                  XtbWorkbookReader.cellValue(row, columns.get("Commission")))
              .orElse(null);
      BigDecimal margin =
          XtbWorkbookReader.parseDecimal(XtbWorkbookReader.cellValue(row, columns.get("Margin")))
              .orElse(null);
      BigDecimal swap =
          XtbWorkbookReader.parseDecimal(XtbWorkbookReader.cellValue(row, columns.get("Swap")))
              .orElse(null);
      BigDecimal profit =
          XtbWorkbookReader.parseDecimal(
                  XtbWorkbookReader.cellValue(row, columns.get("Profit/Loss")))
              .or(
                  () ->
                      XtbWorkbookReader.parseDecimal(
                          XtbWorkbookReader.cellValue(row, columns.get("Gross Profit"))))
              .orElse(null);
      String product =
          firstNonBlank(
              XtbWorkbookReader.cellValue(row, columns.get("Category")),
              XtbWorkbookReader.cellValue(row, columns.get("Product")));
      String sourcePositionId =
          normalizeSourcePositionId(
              firstNonBlank(
                  XtbWorkbookReader.cellValue(row, columns.get("PositionEntity ID")),
                  XtbWorkbookReader.cellValue(row, columns.get("Position ID"))));
      BigDecimal openConversionRate =
          XtbWorkbookReader.parseDecimal(
                  XtbWorkbookReader.cellValue(row, columns.get("Open Conversion Rate")))
              .orElse(null);
      BigDecimal closeConversionRate =
          XtbWorkbookReader.parseDecimal(
                  XtbWorkbookReader.cellValue(row, columns.get("Close Conversion Rate")))
              .orElse(null);

      if (openTime == null || closeTime == null) {
        throw new IllegalArgumentException(
            "XTB closed-position row " + row.getRowNum() + " has an invalid timestamp");
      }

      String sourceIdentity =
          closedPositionSourceIdentity(
              account, sourcePositionId, symbol, typeText, volume, openTime, closeTime, product);
      int sourceRowOccurrence = sourceRowOccurrences.merge(sourceIdentity, 1, Integer::sum);

      PositionEntity position = new PositionEntity();
      position.setId(BrokerSourceRowIdentity.id(sourceIdentity, sourceRowOccurrence));
      Long sourceRowId =
          sourceEvidenceService.recordRow(
              "Closed Positions",
              sheet.getSheetName(),
              row.getRowNum() + 1,
              sourcePositionId,
              sourceRowOccurrence,
              XtbWorkbookReader.rawRowText(row, columns),
              XtbWorkbookReader.rawRowValues(row, columns));
      ImportEvidenceContext context = ImportEvidenceContext.current();
      position.setImportSourceRowId(sourceRowId);
      position.setImportHistoryId(context == null ? null : context.importHistoryId());
      position.setAccount(account);
      position.setSourcePositionId(sourcePositionId);
      position.setSourceRowOccurrence(sourceRowOccurrence);
      position.setSymbol(symbol);
      position.setType(XtbWorkbookReader.parsePositionType(typeText, row.getRowNum()));
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

  private List<PositionEntity> deduplicatePositionEntities(List<PositionEntity> positions) {
    if (CollectionUtils.isEmpty(positions)) {
      return List.of();
    }
    Map<Long, PositionEntity> unique = new LinkedHashMap<>();
    for (PositionEntity position : positions) {
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
      BigDecimal volume,
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

  List<PositionEntity> reconstructPositionEntities(
      List<CashOperationEntity> cashOperations, Long account, CurrencyType currency) {
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
                    CashOperationEntity::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    CashOperationEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .forEach(
            op -> {
              LotEvent event = parseLotEvent(op.getComment()).orElse(null);
              if (event == null || event.volume.signum() <= 0) {
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

    List<PositionEntity> openedPositions = new ArrayList<>();
    appendPositionEntitiesForAccount(openedPositions, account, currency, buyLots, PositionType.BUY);
    appendPositionEntitiesForAccount(
        openedPositions, account, currency, sellLots, PositionType.SELL);
    return openedPositions;
  }

  List<CashOperationEntity> operationsForOpenReconstruction(
      Long account, List<CashOperationEntity> currentOperations) {
    Map<Long, CashOperationEntity> operationsById = new LinkedHashMap<>();
    List<CashOperationEntity> existingOperations =
        cashOperationRepository.findAllByAccount(account);
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

  private void appendPositionEntitiesForAccount(
      List<PositionEntity> output,
      Long account,
      CurrencyType currency,
      Map<String, Deque<Lot>> lotsBySymbol,
      PositionType type) {
    Long accountId = account;
    for (Map.Entry<String, Deque<Lot>> entry : lotsBySymbol.entrySet()) {
      String symbol = entry.getKey();
      int lotIndex = 0;
      for (Lot lot : entry.getValue()) {
        if (lot.volume.signum() <= 0) {
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

        PositionEntity position = new PositionEntity();
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
        position.setPurchaseValue(lot.volume.multiply(lot.openPrice));
        position.setProfit(BigDecimal.ZERO);
        position.setComment("Reconstructed from Cash Operations");
        output.add(position);
      }
    }
  }

  private void consumeFifo(Deque<Lot> lots, BigDecimal closeVolume) {
    BigDecimal remaining = closeVolume;
    while (remaining.signum() > 0 && !lots.isEmpty()) {
      Lot lot = lots.peekFirst();
      if (lot.volume.compareTo(remaining) <= 0) {
        remaining = remaining.subtract(lot.volume);
        lots.removeFirst();
      } else {
        lot.volume = lot.volume.subtract(remaining);
        remaining = BigDecimal.ZERO;
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
    PositionType side = XtbWorkbookReader.parsePositionType(matcher.group(2), -1);
    BigDecimal volume = XtbWorkbookReader.parseDecimal(matcher.group(3)).orElseThrow();
    BigDecimal price = XtbWorkbookReader.parseDecimal(matcher.group(4)).orElseThrow();
    return Optional.of(new LotEvent(open, side, volume, price));
  }

  private void ensureAssetsExist(
      List<CashOperationEntity> cashOperations,
      List<PositionEntity> closedPositions,
      List<PositionEntity> openedPositions,
      CurrencyType currency) {
    List<AssetCatalogService.AssetSeed> assets = new ArrayList<>();
    cashOperations.stream()
        .map(CashOperationEntity::getSymbol)
        .map(symbol -> assetCatalogService.seedForSymbol(symbol, currency))
        .filter(java.util.Objects::nonNull)
        .forEach(assets::add);
    closedPositions.stream()
        .map(PositionEntity::getSymbol)
        .map(symbol -> assetCatalogService.seedForSymbol(symbol, currency))
        .filter(java.util.Objects::nonNull)
        .forEach(assets::add);
    openedPositions.stream()
        .map(PositionEntity::getSymbol)
        .map(symbol -> assetCatalogService.seedForSymbol(symbol, currency))
        .filter(java.util.Objects::nonNull)
        .forEach(assets::add);
    assetCatalogService.ensureAssetsExist(assets);
  }

  private void applyAssetIdentities(
      List<CashOperationEntity> cashOperations,
      List<PositionEntity> closedPositions,
      List<PositionEntity> openedPositions) {
    Set<String> symbols = new HashSet<>();
    cashOperations.stream()
        .map(CashOperationEntity::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    closedPositions.stream()
        .map(PositionEntity::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    openedPositions.stream()
        .map(PositionEntity::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    Map<String, Long> ids =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .collect(java.util.stream.Collectors.toMap(AssetEntity::getSymbol, AssetEntity::getId));
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
      List<PositionEntity> closedPositions,
      List<PositionEntity> openedPositions,
      CurrencyType defaultCurrency) {
    Set<String> symbols = new HashSet<>();
    closedPositions.stream()
        .map(PositionEntity::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    openedPositions.stream()
        .map(PositionEntity::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    Map<String, CurrencyType> quoteCurrencyBySymbol =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .filter(asset -> asset.getCurrency() != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    AssetEntity::getSymbol, AssetEntity::getCurrency, (left, right) -> right));

    Map<PriceCheckpointKey, WeightedPrice> aggregated = new LinkedHashMap<>();

    for (PositionEntity position : closedPositions) {
      CurrencyType quoteCurrency =
          resolveTradeObservationCurrency(
              position.getSymbol(),
              position.getPriceCurrency(),
              defaultCurrency,
              quoteCurrencyBySymbol);
      BigDecimal normalizedOpenPrice =
          normalizeTradeObservationPrice(
              position.getOpenPrice(), position.getPurchaseValue(), position.getVolume());
      BigDecimal normalizedClosePrice =
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

    for (PositionEntity position : openedPositions) {
      CurrencyType quoteCurrency =
          resolveTradeObservationCurrency(
              position.getSymbol(),
              position.getPriceCurrency(),
              defaultCurrency,
              quoteCurrencyBySymbol);
      BigDecimal normalizedOpenPrice =
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
                java.util.stream.Collectors.toMap(
                    AssetEntity::getSymbol, AssetEntity::getId, (a, b) -> a));

    for (Map.Entry<PriceCheckpointKey, WeightedPrice> entry : aggregated.entrySet()) {
      Long assetId = assetIdsBySymbol.get(entry.getKey().symbol());
      if (assetId == null) {
        continue;
      }
      WeightedPrice weightedPrice = entry.getValue();
      if (weightedPrice.totalWeight.signum() <= 0) {
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
      BigDecimal price,
      BigDecimal volume,
      CurrencyType currency,
      String source,
      String priceOrigin,
      String qualityClass) {
    if (!StringUtils.hasText(symbol) || timestamp == null || price == null || price.signum() <= 0) {
      return;
    }
    BigDecimal weight = volume == null ? BigDecimal.ZERO : volume.abs();
    if (weight.signum() <= 0) {
      weight = BigDecimal.ONE;
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
      List<CashOperationEntity> cashOperations, List<PositionEntity> closedPositions) {
    List<String> rawSymbols = new ArrayList<>();
    cashOperations.stream()
        .map(CashOperationEntity::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(rawSymbols::add);
    closedPositions.stream()
        .map(PositionEntity::getSymbol)
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
      List<PositionEntity> closedPositions,
      List<PositionEntity> openedPositions,
      CurrencyType accountCurrency) {
    Set<String> symbols = new HashSet<>();
    closedPositions.stream()
        .map(PositionEntity::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);
    openedPositions.stream()
        .map(PositionEntity::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(symbols::add);

    Map<String, CurrencyType> assetCurrencyBySymbol =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .filter(asset -> asset.getCurrency() != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    AssetEntity::getSymbol, AssetEntity::getCurrency, (left, right) -> left));

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

  private BigDecimal normalizeTradeObservationPrice(
      BigDecimal rawPrice, BigDecimal grossValue, BigDecimal volume) {
    if (rawPrice == null || rawPrice.signum() <= 0) {
      return rawPrice;
    }
    if (grossValue == null || volume == null || volume.abs().signum() == 0) {
      return rawPrice;
    }

    BigDecimal effectivePrice = grossValue.divide(volume, 16, java.math.RoundingMode.HALF_UP).abs();
    if (effectivePrice.signum() <= 0) {
      return rawPrice;
    }

    BigDecimal ratio = rawPrice.divide(effectivePrice, 16, java.math.RoundingMode.HALF_UP);
    if (approximately(ratio, BigDecimal.TEN)
        || approximately(ratio, BigDecimal.valueOf(100))
        || approximately(ratio, BigDecimal.valueOf(1000))) {
      return effectivePrice;
    }
    if (approximately(ratio, new BigDecimal("0.1"))
        || approximately(ratio, new BigDecimal("0.01"))
        || approximately(ratio, new BigDecimal("0.001"))) {
      return effectivePrice;
    }
    return rawPrice;
  }

  private boolean approximately(BigDecimal value, BigDecimal target) {
    return value.subtract(target).abs().compareTo(target.abs().multiply(new BigDecimal("0.05")))
        <= 0;
  }

  private CurrencyType firstNonNull(CurrencyType first, CurrencyType second) {
    return first != null ? first : second;
  }

  private AccountEntity accountConfiguration(Long externalAccountId, String sourceName) {
    var lookup =
        ImportPortfolioContext.current() == null
            ? accountRepository.findByProviderIgnoreCaseAndExternalAccountId(
                "XTB", String.valueOf(externalAccountId))
            : accountRepository.findByPortfolioIdAndProviderIgnoreCaseAndExternalAccountId(
                ImportPortfolioContext.current(), "XTB", String.valueOf(externalAccountId));
    AccountEntity account =
        lookup.orElseThrow(
            () ->
                new IllegalStateException(
                    "XTB account " + externalAccountId + " is not configured: " + sourceName));
    if (!"XTB".equalsIgnoreCase(account.getProvider())) {
      throw new IllegalStateException(
          "AccountEntity "
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

  private Long parseAccountId(String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("Missing broker account id");
    }
    return XtbWorkbookReader.parseLong(value.replaceAll("\\D+", ""))
        .orElseThrow(() -> new IllegalStateException("Invalid broker account id: " + value));
  }

  private void normalizePositionPricesToAccountCurrency(PositionEntity position) {
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

  private BigDecimal normalizedBrokerPrice(
      BigDecimal price,
      BigDecimal conversionRate,
      BigDecimal grossValue,
      BigDecimal volume,
      String leg,
      PositionEntity position) {
    if (price == null) {
      return null;
    }
    if (conversionRate != null && conversionRate.signum() > 0) {
      return price.multiply(conversionRate);
    }
    if (grossValue != null && volume != null && volume.abs().signum() > 0) {
      return grossValue.divide(volume, 16, java.math.RoundingMode.HALF_UP).abs();
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
    return XtbWorkbookReader.parseLong(value).map(String::valueOf).orElse(value.trim());
  }

  private String firstNonBlank(String first, String second) {
    if (StringUtils.hasText(first)) {
      return first;
    }
    return StringUtils.hasText(second) ? second : null;
  }

  private static class Lot {
    private BigDecimal volume;
    private final BigDecimal openPrice;
    private final ZonedDateTime openTime;
    private final Long importHistoryId;
    private final Long sourceRowId;

    private Lot(
        BigDecimal volume,
        BigDecimal openPrice,
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

  private record LotEvent(
      boolean actionOpen, PositionType side, BigDecimal volume, BigDecimal price) {}

  private record PriceCheckpointKey(
      String symbol,
      java.time.LocalDate priceDate,
      CurrencyType currency,
      String source,
      String priceOrigin,
      String qualityClass) {}

  private static class WeightedPrice {
    private BigDecimal weightedPriceSum = BigDecimal.ZERO;
    private BigDecimal totalWeight = BigDecimal.ZERO;

    private void add(BigDecimal price, BigDecimal weight) {
      weightedPriceSum = weightedPriceSum.add(price.multiply(weight));
      totalWeight = totalWeight.add(weight);
    }

    private BigDecimal weightedAverage() {
      return totalWeight.signum() <= 0
          ? BigDecimal.ZERO
          : weightedPriceSum.divide(totalWeight, 16, java.math.RoundingMode.HALF_UP);
    }
  }
}
