package com.smartbox.investory.services.imports.ibrk;

import com.opencsv.CSVReader;
import com.smartbox.investory.infrastructure.CashOperationType;
import com.smartbox.investory.infrastructure.PositionType;
import com.smartbox.investory.infrastructure.repository.*;
import com.smartbox.investory.infrastructure.repository.Asset;
import com.smartbox.investory.infrastructure.repository.account.Account;
import com.smartbox.investory.infrastructure.repository.account.AccountRepository;
import com.smartbox.investory.services.AssetCatalogService;
import com.smartbox.investory.services.ReportingDateHelper;
import com.smartbox.investory.services.currency.CurrencyRateService;
import com.smartbox.investory.services.imports.BrokerSourceRowIdentity;
import com.smartbox.investory.services.imports.ImportEvidenceContext;
import com.smartbox.investory.services.imports.ImportExecutionResult;
import com.smartbox.investory.services.imports.ImportSourceEvidenceService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Imports an Interactive Brokers (IBKR) "Transaction History" activity CSV.
 *
 * <p>IBKR exports a flat cash ledger rather than XTB's position-based sheets. All rows (Buy, Sell,
 * dividends, interest, withholding, deposits, withdrawals, forex, corporate actions) are stored as
 * {@code cash_operations} in their original ledger form without FIFO matching. IBKR rows have no
 * broker id, so importer-owned source fingerprints provide deterministic, provider-neutral IDs.
 */
@Slf4j
@Service
@Transactional
public class IbkrImportService {

  private static final java.time.ZoneId ZONE = ReportingDateHelper.REPORTING_ZONE;
  private static final String SECTION = "Transaction History";
  private static final Pattern IBKR_FILENAME_ACCOUNT_PATTERN =
      Pattern.compile("(?i)\\bU?(\\d{6,})\\.(?:TRANSACTIONS|ACTIVITY)");
  private static final Pattern INTERNAL_TRANSFER_PATTERN =
      Pattern.compile("(?i)transfer\\s+from\\s+(\\d{6,})\\s+to\\s+(\\d{6,})");

  private final CashOperationRepository cashOperationRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final AssetRepository assetRepository;
  private final AccountRepository accountRepository;
  private final AssetCatalogService assetCatalogService;
  private final IbkrPositionReconstructionService ibkrPositionReconstructionService;
  private final ImportSourceEvidenceService sourceEvidenceService;

  @Autowired
  public IbkrImportService(
      CashOperationRepository cashOperationRepository,
      AssetPriceHistoryRepository assetPriceHistoryRepository,
      AssetRepository assetRepository,
      AccountRepository accountRepository,
      AssetCatalogService assetCatalogService,
      IbkrPositionReconstructionService ibkrPositionReconstructionService,
      ImportSourceEvidenceService sourceEvidenceService) {
    this.cashOperationRepository = cashOperationRepository;
    this.assetPriceHistoryRepository = assetPriceHistoryRepository;
    this.assetRepository = assetRepository;
    this.accountRepository = accountRepository;
    this.assetCatalogService = assetCatalogService;
    this.ibkrPositionReconstructionService = ibkrPositionReconstructionService;
    this.sourceEvidenceService = sourceEvidenceService;
  }

  /** Source-compatible constructor for unit tests that exercise parsing without orchestration. */
  public IbkrImportService(
      CashOperationRepository cashOperationRepository,
      AssetPriceHistoryRepository assetPriceHistoryRepository,
      AssetRepository assetRepository,
      AccountRepository accountRepository,
      AssetCatalogService assetCatalogService,
      IbkrPositionReconstructionService ibkrPositionReconstructionService) {
    this(
        cashOperationRepository,
        assetPriceHistoryRepository,
        assetRepository,
        accountRepository,
        assetCatalogService,
        ibkrPositionReconstructionService,
        null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  private CurrencyRateService currencyRateService;

  /**
   * Imports one statement atomically; valid rows may report PARTIAL when other source rows fail.
   */
  public ImportExecutionResult importStatement(InputStream csvStream, String fileName)
      throws Exception {
    List<String[]> rows;
    try (CSVReader reader =
        new CSVReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
      rows = reader.readAll();
    }
    Long accountIdFromFilename = parseAccountIdFromFilename(fileName);
    CurrencyType statementBaseCurrency = parseStatementBaseCurrency(rows);
    harvestReferenceRates(rows, statementBaseCurrency);

    Map<String, Integer> col = locateHeader(rows, SECTION);
    Map<String, Integer> dedup = new HashMap<>();
    Map<Long, Account> configuredAccounts = new HashMap<>();
    List<CashOperation> cashOps = new ArrayList<>();
    List<IbkrTradePriceObservation> tradePriceObservations = new ArrayList<>();
    int total = 0;
    int failed = 0;

    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      String[] r = rows.get(rowIndex);
      if (col.isEmpty() || r.length <= 2 || !SECTION.equals(r[0]) || !"Data".equals(r[1])) {
        continue;
      }
      total++;
      try {
        String type = value(r, col, "Transaction Type", "Type");
        Long externalAccountId =
            resolveIbkrAccountId(
                accountIdFromFilename, value(r, col, "Account", "Account ID", "AccountId"));
        Account configuredAccount =
            configuredAccounts.computeIfAbsent(externalAccountId, this::requireIbkrAccount);
        Long account = configuredAccount.getId();
        CurrencyType baseCurrency = statementBaseCurrency;
        String description = value(r, col, "Description");
        String rawSymbol = value(r, col, "Symbol");
        String symbol = cleanSymbol(rawSymbol);
        if (symbol == null && isForexTradeComponent(type, rawSymbol, description)) {
          symbol = rawSymbol(rawSymbol);
        }
        ZonedDateTime date = parseDate(value(r, col, "Date", "Date/Time", "Trade Date"));
        Double net = parseNumber(value(r, col, "Net Amount", "Net Cash", "NetCash", "Proceeds"));
        Double grossAmount =
            parseNumber(value(r, col, "Gross Amount", "Gross Amount ", "Gross Cash", "Proceeds"));
        Double commission = parseNumber(value(r, col, "Commission", "Comm/Fee", "Commission/Fee"));
        Double quantity = parseNumber(value(r, col, "Quantity", "Qty", "Shares"));
        Double price =
            parseNumber(value(r, col, "Price", "T. Price", "Trade Price", "Transaction Price"));
        CurrencyType currency = resolveMonetaryCurrency(r, col, baseCurrency);

        if (net == null) {
          throw new IllegalArgumentException("IBKR row has no net amount");
        }

        CashOperation op = new CashOperation();
        String key =
            String.join(
                "|",
                "IBKR",
                BrokerSourceRowIdentity.part(account),
                BrokerSourceRowIdentity.part(date),
                BrokerSourceRowIdentity.part(type),
                BrokerSourceRowIdentity.part(rawSymbol),
                BrokerSourceRowIdentity.part(description),
                BrokerSourceRowIdentity.part(quantity),
                BrokerSourceRowIdentity.part(price),
                BrokerSourceRowIdentity.part(value(r, col, "Currency", "Price Currency")),
                BrokerSourceRowIdentity.part(grossAmount),
                BrokerSourceRowIdentity.part(commission),
                BrokerSourceRowIdentity.part(net),
                BrokerSourceRowIdentity.part(currency));
        int occurrence = dedup.merge(key, 1, Integer::sum);
        op.setId(BrokerSourceRowIdentity.id(key, occurrence));
        Long sourceRowId =
            sourceEvidenceService == null
                ? null
                : sourceEvidenceService.recordRow(
                    SECTION,
                    null,
                    rowIndex + 1,
                    value(r, col, "Transaction ID", "TransactionID", "Trade ID", "TradeID"),
                    occurrence,
                    String.join(",", r),
                    rawRowValues(r, col));
        ImportEvidenceContext context = ImportEvidenceContext.current();
        op.setImportSourceRowId(sourceRowId);
        op.setImportHistoryId(context == null ? null : context.importHistoryId());
        op.setAccount(account);
        op.setType(mapCashType(type, description));
        op.setSymbol(shouldKeepCashOperationSymbol(op.getType(), type) ? symbol : null);
        if (StringUtils.hasText(op.getSymbol())) {
          op.setSourceAssetSymbol(rawSymbol(rawSymbol));
          op.setBrokerSymbol(rawSymbol(rawSymbol));
        }
        op.setAmount(net);
        op.setCurrency(currency);
        op.setComment(
            buildOperationComment(
                type, rawSymbol, description, quantity, price, grossAmount, commission));
        op.setDate(date);
        if (currencyRateService != null
            && isForexTradeComponent(type, rawSymbol, description)
            && price != null) {
          currencyRateService.bindIbkrExecutionRate(
              op, rawSymbol, BigDecimal.valueOf(price), "IBKR:OPERATION:" + op.getId());
        }
        if (op.getType() == CashOperationType.UNKNOWN) {
          throw new IllegalArgumentException("Unknown IBKR transaction type: " + type);
        }
        String normalizedType = normalizeOperationType(type);
        if (("buy".equals(normalizedType) || "sell".equals(normalizedType))
            && (quantity == null || price == null)) {
          throw new IllegalArgumentException("IBKR trade row has no quantity or price");
        }
        cashOps.add(op);
        maybeBuildTradePriceObservation(r, col, type, symbol, date, price, currency)
            .ifPresent(tradePriceObservations::add);
      } catch (Exception e) {
        failed++;
        log.warn("Skipping IBKR row {}: {}", total, e.getMessage());
      }
    }

    ensureCashOperationAssetsExist(cashOps);
    if (configuredAccounts.isEmpty()) {
      throw new IllegalArgumentException("IBKR statement has no resolvable account");
    }
    applyCashOperationAssetIdentities(cashOps);
    persistTradePriceHistory(tradePriceObservations);
    cashOperationRepository.saveAll(cashOps);
    // Reconstruction re-reads the full canonical cash history for the account. Flush the freshly
    // saved operations so the same-transaction read sees this file's rows; otherwise the rebuild
    // runs on stale (previously committed) data and drops multi-file position history.
    cashOperationRepository.flush();

    List<Long> affectedAccounts =
        cashOps.stream()
            .map(CashOperation::getAccount)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    List<OpenedPosition> openPositions = new ArrayList<>();
    int reconstructedClosedPositions = 0;
    String openPositionsSection = findOpenPositionsSection(rows);
    if (openPositionsSection != null) {
      Long openPositionsAccount =
          affectedAccounts.size() == 1
              ? affectedAccounts.getFirst()
              : Optional.ofNullable(configuredAccounts.get(accountIdFromFilename))
                  .map(Account::getId)
                  .orElse(null);
      if (openPositionsAccount == null) {
        throw new IllegalArgumentException("IBKR open positions have no resolvable account");
      }
      openPositions = importOpenPositions(rows, dedup, openPositionsSection, openPositionsAccount);
      ensureAssetsExist(openPositions);
      applyPositionAssetIdentities(openPositions);
      for (Long accountId : affectedAccounts) {
        List<OpenedPosition> snapshotForAccount =
            openPositions.stream()
                .filter(position -> Objects.equals(position.getAccount(), accountId))
                .toList();
        IbkrPositionReconstructionService.ReconstructionResult rebuild =
            ibkrPositionReconstructionService.rebuildFromCanonicalHistory(
                accountId, snapshotForAccount);
        reconstructedClosedPositions += rebuild.closedPositions().size();
      }
    } else {
      for (Long accountId : affectedAccounts) {
        IbkrPositionReconstructionService.ReconstructionResult rebuild =
            ibkrPositionReconstructionService.rebuildFromCanonicalHistory(accountId, null);
        openPositions.addAll(rebuild.openedPositions());
        reconstructedClosedPositions += rebuild.closedPositions().size();
      }
    }

    String details =
        String.format(
            "IBKR: %d source cash operations, %d reconstructed closed positions, %d open positions, %d skipped",
            cashOps.size(), reconstructedClosedPositions, openPositions.size(), failed);
    log.info(details);
    // Batch audit counters are row-level import metrics, not projection/rebuild output sizes.
    int applied = Math.max(0, total - failed);
    return new ImportExecutionResult(total, applied, failed, details);
  }

  /**
   * Parses the "Open Positions" section (if present) into IBKR opened positions and replaces them.
   */
  private List<OpenedPosition> importOpenPositions(
      List<String[]> rows, Map<String, Integer> dedup, String section, Long accountId) {
    Map<String, Integer> col = locateHeader(rows, section);
    Integer cSymbol = colIndex(col, "Symbol");
    Integer cQuantity = colIndex(col, "Quantity");
    Integer cCurrency = colIndex(col, "Currency");
    Integer cCost = colIndex(col, "Cost Price");
    Integer cBasis = colIndex(col, "Cost Basis", "Cost Basis Money");
    Integer cClose = colIndex(col, "Close Price", "Mark Price");
    Integer cUnrealized = colIndex(col, "Unrealized P/L", "Unrealized P&L", "Fifo P/L Unrealized");
    Integer cDiscriminator = colIndex(col, "DataDiscriminator");

    ZonedDateTime snapshotEffectiveDate = statementEffectiveDate(rows);
    List<OpenedPosition> positions = new ArrayList<>();
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      String[] r = rows.get(rowIndex);
      if (r.length <= 2 || !section.equals(r[0]) || !"Data".equals(r[1])) {
        continue;
      }
      // Skip lot-level / subtotal rows; keep the consolidated "Summary" position.
      if (cDiscriminator != null
          && cDiscriminator < r.length
          && r[cDiscriminator] != null
          && !r[cDiscriminator].trim().isEmpty()
          && !"Summary".equalsIgnoreCase(r[cDiscriminator].trim())) {
        continue;
      }
      String symbol = cleanSymbol(at(r, cSymbol));
      Double quantity = parseNumber(at(r, cQuantity));
      if (symbol == null || quantity == null || quantity == 0.0) {
        continue;
      }
      OpenedPosition p = new OpenedPosition();
      p.setId(syntheticId("POS|" + accountId + "|" + symbol, dedup));
      Long sourceRowId =
          sourceEvidenceService == null
              ? null
              : sourceEvidenceService.recordRow(
                  section,
                  null,
                  rowIndex + 1,
                  value(r, col, "Position ID", "PositionID", "Conid", "ConID"),
                  1,
                  String.join(",", r),
                  rawRowValues(r, col));
      ImportEvidenceContext context = ImportEvidenceContext.current();
      p.setImportSourceRowId(sourceRowId);
      p.setImportHistoryId(context == null ? null : context.importHistoryId());
      p.setAccount(accountId);
      p.setSymbol(symbol);
      p.setSourceAssetSymbol(rawSymbol(at(r, cSymbol)));
      p.setBrokerSymbol(rawSymbol(at(r, cSymbol)));
      p.setType(quantity >= 0 ? PositionType.BUY : PositionType.SELL);
      CurrencyType monetaryCurrency = parseCurrency(at(r, cCurrency));
      p.setPriceCurrency(monetaryCurrency);
      p.setCostCurrency(monetaryCurrency);
      p.setProfitCurrency(monetaryCurrency);
      p.setCommissionCurrency(monetaryCurrency);
      p.setVolume(Math.abs(quantity));
      p.setOpenTime(snapshotEffectiveDate);
      p.setOpenPrice(parseNumber(at(r, cCost)));
      p.setMarketPrice(parseNumber(at(r, cClose)));
      p.setPurchaseValue(parseNumber(at(r, cBasis)));
      p.setProfit(orZero(parseNumber(at(r, cUnrealized))));
      p.setCommission(0.0);
      p.setSwap(0.0);
      p.setComment("IBKR position snapshot");
      positions.add(p);
    }

    return positions;
  }

  private String value(String[] row, Map<String, Integer> col, String... aliases) {
    return at(row, colIndex(col, aliases));
  }

  private Map<String, String> rawRowValues(String[] row, Map<String, Integer> columns) {
    Map<String, String> values = new LinkedHashMap<>();
    columns.forEach((name, index) -> values.put(name, at(row, index)));
    return values;
  }

  private String normalizeOperationType(String type) {
    if (type == null) {
      return null;
    }
    String normalized = type.trim().toLowerCase();
    return normalized.isEmpty() ? null : normalized;
  }

  private Map<String, Integer> locateHeader(List<String[]> rows, String section) {
    Map<String, Integer> col = new HashMap<>();
    for (String[] r : rows) {
      if (r.length > 2 && section.equals(r[0]) && "Header".equals(r[1])) {
        for (int i = 2; i < r.length; i++) {
          col.put(r[i].trim(), i);
        }
        break;
      }
    }
    return col;
  }

  /** Returns the actual open-position section name used by this statement. */
  private String findOpenPositionsSection(List<String[]> rows) {
    Set<String> wanted = Set.of("open positions", "positions");
    for (String[] r : rows) {
      if (r.length > 1
          && "Header".equals(r[1])
          && r[0] != null
          && wanted.contains(r[0].trim().toLowerCase())) {
        return r[0];
      }
    }
    return null;
  }

  private Integer colIndex(Map<String, Integer> col, String... aliases) {
    for (String alias : aliases) {
      Integer idx = col.get(alias);
      if (idx != null) {
        return idx;
      }
    }
    return null;
  }

  private String at(String[] row, Integer idx) {
    if (idx == null || idx >= row.length) {
      return null;
    }
    String v = row[idx];
    return v == null ? null : v.trim();
  }

  private CurrencyType parseCurrency(String raw) {
    if (!StringUtils.hasText(raw)) {
      throw new IllegalArgumentException("IBKR row has no monetary currency");
    }
    try {
      return CurrencyType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unsupported IBKR monetary currency: " + raw, exception);
    }
  }

  /**
   * Resolves the currency of {@code Net Amount}, {@code Gross Amount}, and commission. An explicit
   * amount currency wins. Otherwise IBKR reports these monetary columns in the statement base
   * currency. {@code Price Currency} belongs only to the per-unit trade price and must never be
   * used to label cash amounts.
   */
  private CurrencyType resolveMonetaryCurrency(
      String[] row, Map<String, Integer> col, CurrencyType baseCurrency) {
    String raw = value(row, col, "Currency", "Currency Primary", "CurrencyPrimary");
    if (StringUtils.hasText(raw) && !"-".equals(raw.trim())) {
      return parseCurrency(raw);
    }
    if (baseCurrency != null) {
      return baseCurrency;
    }
    throw new IllegalArgumentException("IBKR row has no monetary currency");
  }

  /** Reads the statement base currency from the {@code Summary} section. */
  private CurrencyType parseStatementBaseCurrency(List<String[]> rows) {
    for (String[] r : rows) {
      if (r.length > 3
          && "Summary".equals(r[0])
          && "Data".equals(r[1])
          && "Base Currency".equalsIgnoreCase(r[2] == null ? null : r[2].trim())) {
        return parseCurrency(r[3]);
      }
    }
    return null;
  }

  private static double orZero(Double value) {
    return value == null ? 0.0 : value;
  }

  private CashOperationType mapCashType(String type, String description) {
    String normalized = normalizeOperationType(type);
    String normalizedDescription =
        description == null ? null : description.trim().toLowerCase(Locale.ROOT);
    if (normalized == null) {
      return CashOperationType.UNKNOWN;
    }
    // Some IBKR exports append qualifiers, e.g. "Corporate Action - Mandatory Redemption".
    if (normalized.startsWith("corporate action")
        || normalized.startsWith("forex trade component")) {
      return CashOperationType.TRANSFER;
    }
    if (normalized.equals("buy")) {
      return CashOperationType.STOCK_PURCHASE;
    }
    if (normalized.equals("sell")) {
      return CashOperationType.STOCK_SELL;
    }
    return switch (normalized) {
      case "dividend" -> CashOperationType.DIVIDEND;
      case "foreign tax withholding" -> CashOperationType.WITHHOLDING_TAX;
      case "credit interest", "investment interest received", "investment interest paid" ->
          CashOperationType.FREE_FUNDS_INTEREST;
      case "deposit" ->
          "cash transfer".equals(normalizedDescription)
              ? CashOperationType.TRANSFER
              : CashOperationType.DEPOSIT;
      case "withdrawal" -> CashOperationType.WITHDRAWAL;
      case "adjustment" -> CashOperationType.CORRECTION;
      default -> CashOperationType.UNKNOWN;
    };
  }

  private String cleanSymbol(String symbol) {
    return assetCatalogService.mapIbkrSymbolToCanonical(symbol);
  }

  private void ensureAssetsExist(List<OpenedPosition> openPositions) {
    List<AssetCatalogService.AssetSeed> assets = new ArrayList<>();
    openPositions.stream()
        .map(OpenedPosition::getSymbol)
        .map(symbol -> assetCatalogService.seedForSymbol(symbol, CurrencyType.USD))
        .filter(Objects::nonNull)
        .forEach(assets::add);
    assetCatalogService.ensureAssetsExist(assets);
  }

  private void ensureCashOperationAssetsExist(List<CashOperation> cashOperations) {
    List<AssetCatalogService.AssetSeed> assets = new ArrayList<>();
    cashOperations.stream()
        .filter(this::hasResolvableAssetSymbol)
        .map(CashOperation::getSymbol)
        .filter(StringUtils::hasText)
        .map(symbol -> assetCatalogService.seedForSymbol(symbol, CurrencyType.USD))
        .filter(Objects::nonNull)
        .forEach(assets::add);
    assetCatalogService.ensureAssetsExist(assets);
  }

  private void applyCashOperationAssetIdentities(List<CashOperation> operations) {
    Set<String> symbols =
        operations.stream()
            .map(CashOperation::getSymbol)
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.toSet());
    Map<String, Long> ids =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .collect(java.util.stream.Collectors.toMap(Asset::getSymbol, Asset::getId));
    operations.forEach(
        operation -> {
          if (hasResolvableAssetSymbol(operation)) {
            operation.setAssetId(requiredAssetId(operation.getSymbol(), ids));
          }
        });
  }

  private void applyPositionAssetIdentities(List<OpenedPosition> positions) {
    Set<String> symbols =
        positions.stream()
            .map(OpenedPosition::getSymbol)
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.toSet());
    Map<String, Long> ids =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .collect(java.util.stream.Collectors.toMap(Asset::getSymbol, Asset::getId));
    positions.forEach(position -> position.setAssetId(requiredAssetId(position.getSymbol(), ids)));
  }

  private Long requiredAssetId(String symbol, Map<String, Long> ids) {
    Long assetId = ids.get(symbol);
    if (assetId == null) {
      throw new IllegalArgumentException("Unknown or ambiguous IBKR asset symbol: " + symbol);
    }
    return assetId;
  }

  private boolean hasResolvableAssetSymbol(CashOperation operation) {
    boolean pseudoFxSymbol =
        operation.getType() == CashOperationType.TRANSFER
            && StringUtils.hasText(operation.getSourceAssetSymbol())
            && operation
                .getSourceAssetSymbol()
                .trim()
                .toUpperCase(Locale.ROOT)
                .matches("^[A-Z]{3}\\.[A-Z]{3}$");
    return StringUtils.hasText(operation.getSymbol()) && !pseudoFxSymbol;
  }

  private boolean shouldKeepCashOperationSymbol(CashOperationType type, String rawType) {
    return type == CashOperationType.STOCK_PURCHASE
        || type == CashOperationType.STOCK_SELL
        || (type == CashOperationType.TRANSFER
            && normalizeOperationType(rawType) != null
            && normalizeOperationType(rawType).startsWith("corporate action"))
        || type == CashOperationType.DIVIDEND
        || type == CashOperationType.WITHHOLDING_TAX
        || type == CashOperationType.FREE_FUNDS_INTEREST;
  }

  private Account requireIbkrAccount(Long externalAccountId) {
    Account account =
        accountRepository
            .findByProviderIgnoreCaseAndExternalAccountId("IBKR", String.valueOf(externalAccountId))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "IBKR account is not configured: " + externalAccountId));
    if (!"IBKR".equalsIgnoreCase(account.getProvider())) {
      throw new IllegalArgumentException(
          "Account "
              + externalAccountId
              + " is configured for provider "
              + account.getProvider()
              + ", not IBKR");
    }
    if (account.getCurrency() == null) {
      throw new IllegalArgumentException(
          "IBKR account has no configured currency: " + externalAccountId);
    }
    return account;
  }

  private Optional<IbkrTradePriceObservation> maybeBuildTradePriceObservation(
      String[] row,
      Map<String, Integer> col,
      String transactionType,
      String symbol,
      ZonedDateTime tradeDate,
      Double price,
      CurrencyType monetaryCurrency) {
    String normalizedType = normalizeOperationType(transactionType);
    if (!"buy".equals(normalizedType) && !"sell".equals(normalizedType)) {
      return Optional.empty();
    }
    if (!StringUtils.hasText(symbol) || tradeDate == null || price == null || price <= 0.0) {
      return Optional.empty();
    }
    String rawPriceCurrency = value(row, col, "Price Currency");
    CurrencyType priceCurrency =
        StringUtils.hasText(rawPriceCurrency) && !"-".equals(rawPriceCurrency)
            ? parseCurrency(rawPriceCurrency)
            : monetaryCurrency;
    return Optional.of(
        new IbkrTradePriceObservation(
            symbol,
            tradeDate.toLocalDate(),
            priceCurrency,
            price,
            rawSymbol(value(row, col, "Symbol"))));
  }

  private void persistTradePriceHistory(List<IbkrTradePriceObservation> observations) {
    if (observations.isEmpty()) {
      return;
    }
    Set<String> symbols =
        observations.stream()
            .map(IbkrTradePriceObservation::symbol)
            .collect(java.util.stream.Collectors.toSet());
    Map<String, Long> assetIdsBySymbol =
        assetRepository.findAllBySymbolIn(symbols).stream()
            .filter(asset -> !Boolean.TRUE.equals(asset.getExcludeFromImport()))
            .collect(
                java.util.stream.Collectors.toMap(
                    Asset::getSymbol, Asset::getId, (existing, ignored) -> existing));
    for (IbkrTradePriceObservation observation : observations) {
      Long assetId = assetIdsBySymbol.get(observation.symbol());
      if (assetId == null) {
        continue;
      }
      assetPriceHistoryRepository.upsertIbkrTradeObservation(
          assetId,
          observation.priceDate(),
          observation.symbol(),
          observation.originalSourceSymbol(),
          observation.priceCurrency().name(),
          BigDecimal.valueOf(observation.priceValue()));
    }
  }

  private Double parseNumber(String raw) {
    if (!StringUtils.hasText(raw) || "-".equals(raw)) {
      return null;
    }
    try {
      return Double.valueOf(raw.replace(",", ""));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Malformed IBKR number: " + raw, e);
    }
  }

  private ZonedDateTime parseDate(String raw) {
    if (!StringUtils.hasText(raw)) {
      throw new IllegalArgumentException("Missing date");
    }
    String value = raw.trim();
    try {
      return ZonedDateTime.parse(value).withZoneSameInstant(ZONE);
    } catch (java.time.format.DateTimeParseException ignored) {
      try {
        return java.time.OffsetDateTime.parse(value).atZoneSameInstant(ZONE);
      } catch (java.time.format.DateTimeParseException ignoredOffset) {
        return LocalDate.parse(value.substring(0, 10)).atStartOfDay(ZONE);
      }
    }
  }

  /** Snapshot positions are bootstrap state; this is the report effective date, not trade time. */
  private ZonedDateTime statementEffectiveDate(List<String[]> rows) {
    return rows.stream()
        .flatMap(row -> Arrays.stream(row == null ? new String[0] : row))
        .map(this::tryParseDate)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(ZonedDateTime.of(LocalDate.of(1970, 1, 1).atStartOfDay(), ZONE));
  }

  private ZonedDateTime tryParseDate(String value) {
    try {
      return parseDate(value);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String isoDate(ZonedDateTime date) {
    return date == null ? "" : date.toLocalDate().toString();
  }

  private String orDefault(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private String rawSymbol(String symbol) {
    if (!StringUtils.hasText(symbol) || "-".equals(symbol.trim())) {
      return null;
    }
    return symbol.trim();
  }

  private Long normalizeIbkrAccountId(String raw) {
    if (!StringUtils.hasText(raw) || "-".equals(raw.trim())) {
      throw new IllegalArgumentException("IBKR row has no account id");
    }
    String trimmed = raw.trim();
    if (!trimmed.matches("(?i)U?\\d{6,}")) {
      throw new IllegalArgumentException("Malformed IBKR account id: " + raw);
    }
    return Long.valueOf(trimmed.replaceFirst("(?i)^U", ""));
  }

  private Long resolveIbkrAccountId(Long accountIdFromFilename, String accountColumnValue) {
    if (accountIdFromFilename != null) {
      if (StringUtils.hasText(accountColumnValue)
          && accountColumnValue.trim().matches("(?i)U?\\d{6,}")) {
        Long accountIdFromRow = normalizeIbkrAccountId(accountColumnValue);
        if (!accountIdFromFilename.equals(accountIdFromRow)) {
          throw new IllegalArgumentException(
              "IBKR filename account "
                  + accountIdFromFilename
                  + " disagrees with statement account "
                  + accountIdFromRow);
        }
      }
      return accountIdFromFilename;
    }
    Long accountIdFromRow = normalizeIbkrAccountId(accountColumnValue);
    return accountIdFromRow;
  }

  private Long parseAccountIdFromFilename(String fileName) {
    if (!StringUtils.hasText(fileName)) {
      return null;
    }
    Matcher matcher = IBKR_FILENAME_ACCOUNT_PATTERN.matcher(fileName);
    if (!matcher.find()) {
      return null;
    }
    return Long.valueOf(matcher.group(1));
  }

  private String buildOperationComment(
      String rawType,
      String rawSymbol,
      String description,
      Double quantity,
      Double price,
      Double grossAmount,
      Double commission) {
    String base = orDefault(description, "");
    List<String> parts = new ArrayList<>();
    if (StringUtils.hasText(base)) {
      parts.add(base);
    }
    if (StringUtils.hasText(rawType)) {
      parts.add("ibkrRawType=" + rawType.trim());
    }
    if (StringUtils.hasText(rawSymbol)) {
      parts.add("ibkrRawSymbol=" + rawSymbol.trim());
    }
    if (quantity != null) {
      parts.add("ibkrQuantity=" + quantity);
    }
    if (price != null) {
      parts.add("ibkrPrice=" + price);
    }
    if (grossAmount != null) {
      parts.add("ibkrGrossAmount=" + grossAmount);
    }
    if (commission != null) {
      parts.add("ibkrCommission=" + (-Math.abs(commission)));
    }
    if (isForexTradeComponent(rawType, rawSymbol, description)) {
      Matcher matcher = INTERNAL_TRANSFER_PATTERN.matcher(orDefault(description, ""));
      if (matcher.find()) {
        parts.add("ibkrTransferFrom=" + matcher.group(1));
        parts.add("ibkrTransferTo=" + matcher.group(2));
      }
    }
    return String.join(" | ", parts);
  }

  private void harvestReferenceRates(List<String[]> rows, CurrencyType statementBaseCurrency) {
    if (currencyRateService == null) return;
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      String[] headerRow = rows.get(rowIndex);
      if (headerRow.length < 3 || !"Header".equalsIgnoreCase(valueAt(headerRow, 1))) continue;
      String section = valueAt(headerRow, 0);
      Map<String, Integer> columns = new HashMap<>();
      for (int index = 2; index < headerRow.length; index++) {
        if (StringUtils.hasText(headerRow[index])) columns.put(headerRow[index].trim(), index);
      }
      String rateColumn =
          firstColumn(columns, "FX Rate", "FX Rate To Base", "Exchange Rate", "Conversion Rate");
      if (rateColumn == null) continue;
      Integer dataRow = rowIndex + 1;
      while (dataRow < rows.size() && section.equals(valueAt(rows.get(dataRow), 0))) {
        String[] row = rows.get(dataRow);
        if ("Data".equalsIgnoreCase(valueAt(row, 1))) {
          ZonedDateTime observedAt =
              parseDate(value(row, columns, "Date", "Report Date", "Date/Time", "Trade Date"));
          BigDecimal rate = decimal(value(row, columns, rateColumn));
          CurrencyType base =
              currency(value(row, columns, "From Currency", "Base Currency", "Source Currency"));
          CurrencyType target =
              currency(value(row, columns, "To Currency", "Quote Currency", "Target Currency"));
          if (base == null) base = currency(value(row, columns, "Currency"));
          if (target == null && rateColumn.toLowerCase(Locale.ROOT).contains("to base"))
            target = statementBaseCurrency;
          if (base != null && target != null && observedAt != null && rate != null) {
            currencyRateService.harvestIbkrDailyReference(
                observedAt, base, target, rate, "IBKR:REFERENCE:" + section + ":" + dataRow);
          }
        }
        dataRow++;
      }
    }
  }

  private String value(String[] row, Map<String, Integer> columns, String column) {
    Integer index = columns.get(column);
    return index == null ? null : at(row, index);
  }

  private String firstColumn(Map<String, Integer> columns, String... aliases) {
    for (String alias : aliases) {
      for (String name : columns.keySet()) {
        if (alias.equalsIgnoreCase(name)) return name;
      }
    }
    return null;
  }

  private static String valueAt(String[] row, int index) {
    return row != null && index < row.length && row[index] != null ? row[index].trim() : "";
  }

  private BigDecimal decimal(String value) {
    Double parsed = parseNumber(value);
    return parsed == null ? null : BigDecimal.valueOf(parsed);
  }

  private static CurrencyType currency(String value) {
    if (!StringUtils.hasText(value)) return null;
    try {
      return CurrencyType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private boolean isForexTradeComponent(String rawType, String rawSymbol, String description) {
    String normalized = normalizeOperationType(rawType);
    if (!"forex trade component".equals(normalized)) {
      return false;
    }
    return StringUtils.hasText(rawSymbol) || StringUtils.hasText(description);
  }

  /** Stable source hash; an occurrence counter disambiguates genuinely identical source rows. */
  private long syntheticId(String key, Map<String, Integer> dedup) {
    return BrokerSourceRowIdentity.id(key, dedup.merge(key, 1, Integer::sum));
  }

  private record IbkrTradePriceObservation(
      String symbol,
      LocalDate priceDate,
      CurrencyType priceCurrency,
      Double priceValue,
      String originalSourceSymbol) {}
}
