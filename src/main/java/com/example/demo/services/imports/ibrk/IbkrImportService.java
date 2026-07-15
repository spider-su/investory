package com.example.demo.services.imports.ibrk;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
import com.example.demo.infrastructure.repository.*;
import com.example.demo.services.AssetCatalogService;
import com.example.demo.services.imports.ImportExecutionResult;
import com.opencsv.CSVReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Imports an Interactive Brokers (IBKR) "Transaction History" activity CSV.
 *
 * <p>IBKR exports a flat cash ledger rather than XTB's position-based sheets. All rows (Buy, Sell,
 * dividends, interest, withholding, deposits, withdrawals, forex, corporate actions) are stored as
 * {@code cash_operations} in their original ledger form without FIFO matching. IBKR rows have no
 * broker id, so stable <b>negative</b> synthetic ids are generated (XTB uses positive ids) to keep
 * re-imports idempotent.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class IbkrImportService {

  private static final ZoneId ZONE = ZoneId.systemDefault();
  private static final String SECTION = "Transaction History";
  private static final Long IBKR_ACCOUNT = 17959259L;
  private static final Pattern BOND_CALL_PRICE_PATTERN =
      Pattern.compile("(?i)for\\s+USD\\s+([0-9]+(?:\\.[0-9]+)?)\\s+per\\s+Bond");

  private final ClosedPositionRepository closedPositionRepository;
  private final CashOperationRepository cashOperationRepository;
  private final OpenedPositionRepository openedPositionRepository;
  private final AssetCatalogService assetCatalogService;

  public ImportExecutionResult importStatement(InputStream csvStream) throws Exception {
    List<String[]> rows;
    try (CSVReader reader =
        new CSVReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
      rows = reader.readAll();
    }

    Map<String, Integer> col = locateHeader(rows, SECTION);
    Map<String, Integer> dedup = new HashMap<>();
    List<CashOperation> cashOps = new ArrayList<>();
    List<ClosedPosition> closedPositions = new ArrayList<>();
    List<PositionTransaction> positionTransactions = new ArrayList<>();
    Map<String, ReconstructedPosition> reconstructedPositions = new LinkedHashMap<>();
    int total = 0;
    int failed = 0;

    for (String[] r : rows) {
      if (col.isEmpty() || r.length <= 2 || !SECTION.equals(r[0]) || !"Data".equals(r[1])) {
        continue;
      }
      total++;
      try {
        String type = value(r, col, "Transaction Type", "Type");
        Long account =
            normalizeIbkrAccountId(value(r, col, "Account", "Account ID", "AccountId"));
        String symbol = cleanSymbol(value(r, col, "Symbol"));
        String description = value(r, col, "Description");
        ZonedDateTime date = parseDate(value(r, col, "Date", "Date/Time", "Trade Date"));
        Double net = parseNumber(value(r, col, "Net Amount", "Net Cash", "NetCash", "Proceeds"));
        Double quantity = parseNumber(value(r, col, "Quantity", "Qty", "Shares"));
        Double price =
            parseNumber(value(r, col, "Price", "T. Price", "Trade Price", "Transaction Price"));
        CurrencyType currency =
            parseCurrency(value(r, col, "Currency", "Currency Primary", "CurrencyPrimary"));

        CashOperation op = new CashOperation();
        String key =
            String.join(
                "|",
                String.valueOf(account),
                isoDate(date),
                type,
                String.valueOf(symbol),
                String.valueOf(net),
                description);
        op.setId(syntheticId(key, dedup));
        op.setAccount(account);
        op.setType(mapCashType(type));
        op.setSymbol(shouldKeepCashOperationSymbol(op.getType()) ? symbol : null);
        op.setAmount(net != null ? net : 0.0);
        op.setCurrency(currency);
        op.setComment(description);
        op.setDate(date);
        cashOps.add(op);
        positionTransactions.add(
            new PositionTransaction(op, type, symbol, description, quantity, price));
      } catch (Exception e) {
        failed++;
        log.warn("Skipping IBKR row: {}", e.getMessage());
      }
    }

    positionTransactions.stream()
        .sorted(Comparator.comparing(tx -> tx.operation().getDate()))
        .forEach(tx -> applyPositionTransaction(reconstructedPositions, closedPositions, dedup, tx));

    ensureCashOperationAssetsExist(cashOps);
    cashOperationRepository.saveAll(cashOps);
    closedPositionRepository.saveAll(closedPositions);

    List<OpenedPosition> openPositions = new ArrayList<>();
    if (findSection(rows, "Open Positions", "Positions") != null) {
      // Activity Statement: use broker-reported open positions (market values) + NAV.
      openPositions = importOpenPositions(rows, dedup);
      openedPositionRepository.removeAllByAccountNotIn(IBKR_ACCOUNT, openPositions);
      openedPositionRepository.saveAll(openPositions);
      ensureAssetsExist(openPositions);
    } else {
      openPositions = reconstructOpenPositions(reconstructedPositions, dedup);
      if (!openPositions.isEmpty()) {
        openedPositionRepository.removeAllByAccountNotIn(IBKR_ACCOUNT, openPositions);
        openedPositionRepository.saveAll(openPositions);
      } else if (!reconstructedPositions.isEmpty()) {
        openedPositionRepository.deleteByAccount(IBKR_ACCOUNT);
      }
    }

    String details =
        String.format(
            "IBKR: %d cash operations, %d closed positions, %d open positions, %d skipped",
            cashOps.size(), closedPositions.size(), openPositions.size(), failed);
    log.info(details);
    return new ImportExecutionResult(
        total, cashOps.size() + closedPositions.size() + openPositions.size(), failed, details);
  }

  private void applyPositionTransaction(
      Map<String, ReconstructedPosition> positions,
      List<ClosedPosition> closedPositions,
      Map<String, Integer> dedup,
      PositionTransaction tx) {
    CashOperation op = tx.operation();
    String symbol = tx.symbol();
    if (op.getType() != CashOperationType.STOCK_PURCHASE
        && op.getType() != CashOperationType.STOCK_SELL
        && !isBondRedemption(tx)) {
      return;
    }
    if (!StringUtils.hasText(symbol)) {
      return;
    }

    ReconstructedPosition position =
        positions.computeIfAbsent(
            op.getAccount() + "|" + symbol,
            ignored ->
                new ReconstructedPosition(
                    op.getAccount(), symbol, op.getCurrency(), op.getDate()));

    if (op.getType() == CashOperationType.TRANSFER) {
      List<ClosedSlice> closedSlices =
          closeReconstructedPosition(position, inferredBondRedemptionQuantity(position, tx));
      addClosedPositions(closedPositions, dedup, position, tx, op, closedSlices);
      position.lastDate = op.getDate();
      return;
    }

    if (tx.quantity() == null || tx.quantity() == 0.0) {
      return;
    }

    double tradeQuantity = Math.abs(tx.quantity());
    double cashAmount = Math.abs(nz(op.getAmount()));
    double tradeValue = cashAmount > 0.0 ? cashAmount : tradeQuantity * nz(tx.price());
    if (op.getType() == CashOperationType.STOCK_PURCHASE) {
      position.addLot(op.getDate(), tradeQuantity, tradeValue);
    } else {
      List<ClosedSlice> closedSlices = closeReconstructedPosition(position, tradeQuantity);
      addClosedPositions(closedPositions, dedup, position, tx, op, closedSlices);
    }
    position.lastDate = op.getDate();
  }

  private void addClosedPositions(
      List<ClosedPosition> closedPositions,
      Map<String, Integer> dedup,
      ReconstructedPosition position,
      PositionTransaction tx,
      CashOperation op,
      List<ClosedSlice> closedSlices) {
    if (closedSlices.isEmpty()) {
      return;
    }
    double totalClosedQuantity = closedSlices.stream().mapToDouble(ClosedSlice::quantity).sum();
    double totalSaleValue = Math.abs(nz(op.getAmount()));
    if (totalSaleValue <= 0.0) {
      totalSaleValue = totalClosedQuantity * nz(tx.price());
    }
    for (ClosedSlice closed : closedSlices) {
      double saleValue =
          totalClosedQuantity == 0.0
              ? 0.0
              : totalSaleValue * closed.quantity() / totalClosedQuantity;
      addClosedPosition(closedPositions, dedup, position, op, closed, saleValue);
    }
  }

  private void addClosedPosition(
      List<ClosedPosition> closedPositions,
      Map<String, Integer> dedup,
      ReconstructedPosition position,
      CashOperation op,
      ClosedSlice closed,
      double saleValue) {
    ClosedPosition closedPosition = new ClosedPosition();
    closedPosition.setId(
        syntheticId(
            "CLOSED|"
                + op.getAccount()
                + "|"
                + position.symbol
                + "|"
                + isoDate(op.getDate())
                + "|"
                + isoDate(closed.openDate())
                + "|"
                + closed.quantity()
                + "|"
                + saleValue,
            dedup));
    closedPosition.setAccount(op.getAccount());
    closedPosition.setSymbol(position.symbol);
    closedPosition.setType(PositionType.BUY);
    closedPosition.setCurrency(position.currency);
    closedPosition.setVolume(closed.quantity());
    closedPosition.setOpenTime(closed.openDate());
    closedPosition.setOpenPrice(closed.averageCost());
    closedPosition.setCloseTime(op.getDate());
    closedPosition.setClosePrice(closed.quantity() == 0.0 ? 0.0 : saleValue / closed.quantity());
    closedPosition.setPurchaseValue(closed.costBasis());
    closedPosition.setSaleValue(saleValue);
    closedPosition.setMargin(0.0);
    closedPosition.setCommission(0.0);
    closedPosition.setSwap(0.0);
    closedPosition.setProfit(saleValue - closed.costBasis());
    closedPositions.add(closedPosition);
  }

  private boolean isBondRedemption(PositionTransaction tx) {
    String normalizedType = normalizeOperationType(tx.transactionType());
    String description = tx.description();
    if (tx.operation().getType() != CashOperationType.TRANSFER
        || !StringUtils.hasText(tx.symbol())
        || !StringUtils.hasText(description)
        || normalizedType == null
        || !normalizedType.startsWith("corporate action")
        || tx.operation().getAmount() <= 0.0) {
      return false;
    }
    String normalizedDescription = description.toLowerCase(Locale.ROOT);
    return normalizedDescription.contains("call") && normalizedDescription.contains("redemption");
  }

  private double inferredBondRedemptionQuantity(
      ReconstructedPosition position, PositionTransaction tx) {
    Double redemptionPrice = parseBondCallPrice(tx.description());
    if (redemptionPrice != null && redemptionPrice > 0.0) {
      return Math.abs(tx.operation().getAmount()) / redemptionPrice;
    }
    if (position.quantity <= 0.0 || position.costBasis <= 0.0) {
      return 0.0;
    }
    double averageCost = position.costBasis / position.quantity;
    return averageCost <= 0.0 ? 0.0 : Math.abs(tx.operation().getAmount()) / averageCost;
  }

  private Double parseBondCallPrice(String description) {
    Matcher matcher = BOND_CALL_PRICE_PATTERN.matcher(description);
    if (!matcher.find()) {
      return null;
    }
    return parseNumber(matcher.group(1));
  }

  private List<ClosedSlice> closeReconstructedPosition(
      ReconstructedPosition position, double requestedQuantity) {
    double remaining = Math.min(Math.abs(requestedQuantity), position.quantity);
    if (remaining <= 0.0) {
      return List.of();
    }

    List<ClosedSlice> slices = new ArrayList<>();
    while (remaining > 0.000001 && !position.lots.isEmpty()) {
      ReconstructedLot lot = position.lots.peekFirst();
      double closeQuantity = Math.min(remaining, lot.quantity);
      double averageCost = lot.quantity == 0.0 ? 0.0 : lot.costBasis / lot.quantity;
      double closedCostBasis = averageCost * closeQuantity;
      lot.quantity -= closeQuantity;
      lot.costBasis -= closedCostBasis;
      position.quantity -= closeQuantity;
      position.costBasis -= closedCostBasis;
      remaining -= closeQuantity;
      slices.add(new ClosedSlice(closeQuantity, closedCostBasis, averageCost, lot.openDate));
      if (lot.quantity <= 0.000001) {
        position.lots.removeFirst();
      }
    }
    position.normalize();
    return slices;
  }

  private List<OpenedPosition> reconstructOpenPositions(
      Map<String, ReconstructedPosition> reconstructedPositions, Map<String, Integer> dedup) {
    List<OpenedPosition> positions = new ArrayList<>();
    for (ReconstructedPosition reconstructed : reconstructedPositions.values()) {
      if (reconstructed.account == null
          || !Objects.equals(IBKR_ACCOUNT, reconstructed.account)
          || reconstructed.quantity <= 0.0) {
        continue;
      }
      int lotIndex = 0;
      for (ReconstructedLot lot : reconstructed.lots) {
        if (lot.quantity <= 0.0) {
          continue;
        }
        OpenedPosition position = new OpenedPosition();
        position.setId(
            syntheticId(
                "POS|"
                    + reconstructed.account
                    + "|"
                    + reconstructed.symbol
                    + "|TXN|"
                    + isoDate(lot.openDate)
                    + "|"
                    + lotIndex,
                dedup));
        position.setAccount(reconstructed.account);
        position.setSymbol(reconstructed.symbol);
        position.setType(PositionType.BUY);
        position.setCurrency(reconstructed.currency);
        position.setVolume(lot.quantity);
        position.setOpenTime(lot.openDate != null ? lot.openDate : ZonedDateTime.now());
        position.setPurchaseValue(lot.costBasis);
        position.setOpenPrice(lot.quantity == 0.0 ? 0.0 : lot.costBasis / lot.quantity);
        position.setProfit(0.0);
        position.setCommission(0.0);
        position.setSwap(0.0);
        position.setComment("IBKR reconstructed from transactions");
        positions.add(position);
        lotIndex++;
      }
    }
    return positions;
  }

  /**
   * Parses the "Open Positions" section (if present) into IBKR opened positions and replaces them.
   */
  private List<OpenedPosition> importOpenPositions(
      List<String[]> rows, Map<String, Integer> dedup) {
    String section = findSection(rows, "Open Positions", "Positions");
    if (section == null) {
      return List.of();
    }
    Map<String, Integer> col = locateHeader(rows, section);
    Integer cSymbol = colIndex(col, "Symbol");
    Integer cQuantity = colIndex(col, "Quantity");
    Integer cCurrency = colIndex(col, "Currency");
    Integer cCost = colIndex(col, "Cost Price");
    Integer cBasis = colIndex(col, "Cost Basis", "Cost Basis Money");
    Integer cClose = colIndex(col, "Close Price", "Mark Price");
    Integer cUnrealized = colIndex(col, "Unrealized P/L", "Unrealized P&L", "Fifo P/L Unrealized");
    Integer cDiscriminator = colIndex(col, "DataDiscriminator");

    ZonedDateTime now = ZonedDateTime.now();
    List<OpenedPosition> positions = new ArrayList<>();
    for (String[] r : rows) {
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
      p.setId(syntheticId("POS|" + IBKR_ACCOUNT + "|" + symbol, dedup));
      p.setAccount(IBKR_ACCOUNT);
      p.setSymbol(symbol);
      p.setType(quantity >= 0 ? PositionType.BUY : PositionType.SELL);
      p.setCurrency(parseCurrency(at(r, cCurrency)));
      p.setVolume(quantity);
      p.setOpenTime(now);
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

  /** Returns the actual section name (first column) matching one of the candidate aliases. */
  private String findSection(List<String[]> rows, String... candidates) {
    Set<String> wanted = new HashSet<>();
    for (String c : candidates) {
      wanted.add(c.toLowerCase());
    }
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
    if (StringUtils.hasText(raw)) {
      try {
        return CurrencyType.valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
        // unsupported currency (e.g. GBP) -> treat as base USD
      }
    }
    return CurrencyType.USD;
  }

  private static double orZero(Double value) {
    return value == null ? 0.0 : value;
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private CashOperationType mapCashType(String type) {
    String normalized = normalizeOperationType(type);
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
      case "deposit" -> CashOperationType.DEPOSIT;
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
        .map(CashOperation::getSymbol)
        .filter(StringUtils::hasText)
        .map(symbol -> assetCatalogService.seedForSymbol(symbol, CurrencyType.USD))
        .filter(Objects::nonNull)
        .forEach(assets::add);
    assetCatalogService.ensureAssetsExist(assets);
  }

  private boolean shouldKeepCashOperationSymbol(CashOperationType type) {
    return type == CashOperationType.STOCK_PURCHASE
        || type == CashOperationType.STOCK_SELL
        || type == CashOperationType.DIVIDEND
        || type == CashOperationType.WITHHOLDING_TAX;
  }

  private Double parseNumber(String raw) {
    if (!StringUtils.hasText(raw) || "-".equals(raw)) {
      return null;
    }
    try {
      return Double.valueOf(raw.replace(",", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private ZonedDateTime parseDate(String raw) {
    if (!StringUtils.hasText(raw)) {
      throw new IllegalArgumentException("Missing date");
    }
    return LocalDate.parse(raw.trim().substring(0, 10)).atStartOfDay(ZONE);
  }

  private String isoDate(ZonedDateTime date) {
    return date == null ? "" : date.toLocalDate().toString();
  }

  private String orDefault(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private Long normalizeIbkrAccountId(String raw) {
    String source = orDefault(raw, "U" + IBKR_ACCOUNT);
    String trimmed = source.trim();
    if (trimmed.startsWith("U") || trimmed.startsWith("u")) {
      trimmed = trimmed.substring(1);
    }
    String digits = trimmed.replaceAll("\\D+", "");
    if (!StringUtils.hasText(digits)) {
      return IBKR_ACCOUNT;
    }
    return Long.valueOf(digits);
  }

  /** Stable negative id from content hash; an occurrence counter disambiguates identical rows. */
  private long syntheticId(String key, Map<String, Integer> dedup) {
    int occurrence = dedup.merge(key, 1, Integer::sum);
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest((key + "#" + occurrence).getBytes(StandardCharsets.UTF_8));
      long value = 0L;
      for (int i = 0; i < 8; i++) {
        value = (value << 8) | (hash[i] & 0xffL);
      }
      value &= Long.MAX_VALUE; // non-negative
      return -(value == 0 ? 1 : value); // negative -> never collides with positive XTB ids
    } catch (Exception e) {
      throw new IllegalStateException("Cannot hash IBKR row id", e);
    }
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private static class ReconstructedPosition {
    private final Long account;
    private final String symbol;
    private final CurrencyType currency;
    private final Deque<ReconstructedLot> lots = new ArrayDeque<>();
    private double quantity;
    private double costBasis;
    private ZonedDateTime lastDate;

    private ReconstructedPosition(
        Long account, String symbol, CurrencyType currency, ZonedDateTime lastDate) {
      this.account = account;
      this.symbol = symbol;
      this.currency = currency == null ? CurrencyType.USD : currency;
      this.lastDate = lastDate;
    }

    private void addLot(ZonedDateTime openDate, double quantity, double costBasis) {
      lots.addLast(new ReconstructedLot(openDate, quantity, costBasis));
      this.quantity += quantity;
      this.costBasis += costBasis;
    }

    private void normalize() {
      if (Math.abs(quantity) < 0.000001) {
        quantity = 0.0;
        costBasis = 0.0;
        lots.clear();
      }
    }
  }

  private static class ReconstructedLot {
    private final ZonedDateTime openDate;
    private double quantity;
    private double costBasis;

    private ReconstructedLot(ZonedDateTime openDate, double quantity, double costBasis) {
      this.openDate = openDate;
      this.quantity = quantity;
      this.costBasis = costBasis;
    }
  }

  private record ClosedSlice(
      double quantity, double costBasis, double averageCost, ZonedDateTime openDate) {}

  private record PositionTransaction(
      CashOperation operation,
      String transactionType,
      String symbol,
      String description,
      Double quantity,
      Double price) {}
}
