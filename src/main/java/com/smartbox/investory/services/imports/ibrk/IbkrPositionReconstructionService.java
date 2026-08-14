package com.smartbox.investory.services.imports.ibrk;

import com.smartbox.investory.infrastructure.CashOperationType;
import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.infrastructure.PositionType;
import com.smartbox.investory.infrastructure.repository.CashOperation;
import com.smartbox.investory.infrastructure.repository.CashOperationRepository;
import com.smartbox.investory.infrastructure.repository.ClosedPosition;
import com.smartbox.investory.infrastructure.repository.ClosedPositionRepository;
import com.smartbox.investory.infrastructure.repository.OpenedPosition;
import com.smartbox.investory.infrastructure.repository.OpenedPositionRepository;
import com.smartbox.investory.services.ReportingDateHelper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class IbkrPositionReconstructionService {

  private static final double EPSILON = 0.000001d;
  private static final java.util.regex.Pattern COMPACT_BOND_SYMBOL_PATTERN =
      java.util.regex.Pattern.compile("^T\\d{6,}(?:\\.[A-Z]{2})?$");
  private static final java.util.regex.Pattern ISIN_PATTERN =
      java.util.regex.Pattern.compile("^[A-Z]{2}[A-Z0-9]{9}[0-9]$");

  private final CashOperationRepository cashOperationRepository;
  private final OpenedPositionRepository openedPositionRepository;
  private final ClosedPositionRepository closedPositionRepository;

  @Transactional
  public ReconstructionResult rebuildFromCanonicalHistory(
      Long accountId, List<OpenedPosition> authoritativeOpenPositions) {
    List<CashOperation> canonicalOperations =
        cashOperationRepository.findAllByAccount(accountId).stream()
            .sorted(
                Comparator.comparing(CashOperation::getDate)
                    .thenComparing(op -> orDefault(op.getSymbol(), ""))
                    .thenComparing(op -> orDefault(op.getComment(), ""))
                    .thenComparing(CashOperation::getId))
            .toList();

    Map<String, Integer> dedup = new HashMap<>();
    Map<String, ReconstructedPosition> positions = new LinkedHashMap<>();
    List<ClosedPosition> closed = new ArrayList<>();

    canonicalOperations.stream()
        .map(this::toCanonicalTrade)
        .filter(Objects::nonNull)
        .forEach(tx -> applyTrade(positions, closed, dedup, tx));

    validateQuantityReconciliation(positions, canonicalOperations);

    List<OpenedPosition> canonicalOpen = buildOpenPositions(positions, dedup);
    List<OpenedPosition> open =
        mergeAuthoritativeOpenPositionFallback(
            canonicalOpen, authoritativeOpenPositions, canonicalOperations);

    replaceDerivedPositions(accountId, open, closed);
    return new ReconstructionResult(open, closed);
  }

  private void replaceDerivedPositions(
      Long accountId, List<OpenedPosition> open, List<ClosedPosition> closed) {
    openedPositionRepository.deleteByAccount(accountId);
    closedPositionRepository.deleteByAccount(accountId);
    if (!closed.isEmpty()) {
      closedPositionRepository.saveAll(closed);
      closedPositionRepository.flush();
    }
    if (!open.isEmpty()) {
      openedPositionRepository.saveAll(open);
      openedPositionRepository.flush();
    }
  }

  private CanonicalTrade toCanonicalTrade(CashOperation operation) {
    if (operation.getAccount() == null) {
      return null;
    }
    if (operation.getType() != CashOperationType.STOCK_PURCHASE
        && operation.getType() != CashOperationType.STOCK_SELL
        && operation.getType() != CashOperationType.TRANSFER) {
      return null;
    }
    String symbol = operation.getSymbol();
    String rawType = commentField(operation.getComment(), "ibkrRawType");
    String description = operation.getComment();
    Double quantity = parseDouble(commentField(operation.getComment(), "ibkrQuantity"));
    Double price = parseDouble(commentField(operation.getComment(), "ibkrPrice"));
    Double grossAmount = parseDouble(commentField(operation.getComment(), "ibkrGrossAmount"));
    Double commission = parseDouble(commentField(operation.getComment(), "ibkrCommission"));
    String rawSymbol = commentField(operation.getComment(), "ibkrRawSymbol");
    return new CanonicalTrade(
        operation,
        rawType,
        symbol,
        rawSymbol,
        description,
        quantity,
        price,
        grossAmount,
        commission);
  }

  private void applyTrade(
      Map<String, ReconstructedPosition> positions,
      List<ClosedPosition> closedPositions,
      Map<String, Integer> dedup,
      CanonicalTrade tx) {
    CashOperation op = tx.operation();
    if (op.getType() == CashOperationType.TRANSFER && !isBondRedemption(tx)) {
      return;
    }
    if (!StringUtils.hasText(tx.symbol())) {
      return;
    }
    ReconstructedPosition position =
        positions.computeIfAbsent(
            op.getAccount() + "|" + tx.symbol(),
            ignored ->
                new ReconstructedPosition(
                    op.getAccount(),
                    op.getAssetId(),
                    tx.symbol(),
                    op.getSourceAssetSymbol(),
                    op.getCurrency()));

    if (op.getType() == CashOperationType.STOCK_PURCHASE) {
      if (tx.quantity() == null || Math.abs(tx.quantity()) < EPSILON) {
        return;
      }
      double qty = Math.abs(tx.quantity());
      double value = grossTradeValue(tx) > EPSILON ? grossTradeValue(tx) : qty * nz(tx.price());
      position.addLot(
          op.getDate(),
          qty,
          value,
          Math.abs(nz(tx.commission())),
          op.getImportHistoryId(),
          op.getImportSourceRowId());
      return;
    }

    double requestedQuantity;
    if (op.getType() == CashOperationType.TRANSFER) {
      requestedQuantity = inferredBondRedemptionQuantity(position, tx);
    } else {
      requestedQuantity = tx.quantity() == null ? 0.0 : Math.abs(tx.quantity());
    }
    if (requestedQuantity <= EPSILON) {
      return;
    }
    List<ClosedSlice> slices = closeReconstructedPosition(position, requestedQuantity);
    addClosedPositions(closedPositions, dedup, position, tx, slices);
  }

  private List<OpenedPosition> buildOpenPositions(
      Map<String, ReconstructedPosition> reconstructedPositions, Map<String, Integer> dedup) {
    List<OpenedPosition> positions = new ArrayList<>();
    for (ReconstructedPosition reconstructed : reconstructedPositions.values()) {
      if (reconstructed.quantity <= EPSILON) {
        continue;
      }
      int lotIndex = 0;
      for (ReconstructedLot lot : reconstructed.lots) {
        if (lot.quantity <= EPSILON) {
          continue;
        }
        OpenedPosition position = new OpenedPosition();
        position.setId(
            syntheticId(
                "POS|"
                    + reconstructed.account
                    + "|"
                    + reconstructed.symbol
                    + "|"
                    + ReportingDateHelper.toReportingDate(lot.openDate)
                    + "|"
                    + lotIndex,
                dedup));
        position.setAccount(reconstructed.account);
        position.setAssetId(reconstructed.assetId);
        position.setSymbol(reconstructed.symbol);
        position.setSourceAssetSymbol(reconstructed.sourceAssetSymbol);
        position.setType(PositionType.BUY);
        position.setPriceCurrency(reconstructed.currency);
        position.setCostCurrency(reconstructed.currency);
        position.setProfitCurrency(reconstructed.currency);
        position.setCommissionCurrency(reconstructed.currency);
        position.setVolume(lot.quantity);
        position.setOpenTime(lot.openDate);
        position.setPurchaseValue(lot.costBasis);
        position.setOpenPrice(lot.quantity <= EPSILON ? 0.0 : lot.costBasis / lot.quantity);
        position.setCommission(-lot.commissionCostBasis);
        position.setSwap(0.0);
        position.setProfit(0.0);
        position.setComment("IBKR reconstructed from canonical cash history");
        position.setImportHistoryId(lot.importHistoryId);
        position.setImportSourceRowId(lot.sourceRowId);
        positions.add(position);
        lotIndex++;
      }
    }
    return positions;
  }

  /**
   * Activity-statement open-position snapshots are fallback only.
   *
   * <p>Canonical trade history is the source of truth for symbols that have any trade ledger rows.
   * Snapshot-only symbols are appended as bootstrap rows when no canonical trade history exists for
   * that symbol yet (for example, first import starts mid-history).
   */
  private List<OpenedPosition> mergeAuthoritativeOpenPositionFallback(
      List<OpenedPosition> canonicalOpen,
      List<OpenedPosition> authoritativeOpenPositions,
      List<CashOperation> canonicalOperations) {
    if (authoritativeOpenPositions == null) {
      return canonicalOpen;
    }

    Set<String> canonicalTradeSymbols = new LinkedHashSet<>();
    canonicalOperations.stream()
        .map(this::toCanonicalTrade)
        .filter(Objects::nonNull)
        .map(CanonicalTrade::symbol)
        .filter(StringUtils::hasText)
        .forEach(canonicalTradeSymbols::add);

    Set<String> canonicalOpenSymbols = new LinkedHashSet<>();
    canonicalOpen.stream()
        .map(OpenedPosition::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(canonicalOpenSymbols::add);

    List<OpenedPosition> merged = new ArrayList<>(canonicalOpen);
    for (OpenedPosition snapshotPosition : authoritativeOpenPositions) {
      String symbol = snapshotPosition.getSymbol();
      if (!StringUtils.hasText(symbol)) {
        continue;
      }
      if (canonicalOpenSymbols.contains(symbol)) {
        continue;
      }
      if (canonicalTradeSymbols.contains(symbol)) {
        continue;
      }
      merged.add(snapshotPosition);
      canonicalOpenSymbols.add(symbol);
    }
    return merged;
  }

  private void addClosedPositions(
      List<ClosedPosition> closedPositions,
      Map<String, Integer> dedup,
      ReconstructedPosition position,
      CanonicalTrade tx,
      List<ClosedSlice> closedSlices) {
    if (closedSlices.isEmpty()) {
      return;
    }
    CashOperation op = tx.operation();
    double totalClosedQuantity = closedSlices.stream().mapToDouble(ClosedSlice::quantity).sum();
    double totalSaleValue = grossTradeValue(tx);
    if (totalSaleValue <= EPSILON) {
      totalSaleValue = totalClosedQuantity * nz(tx.price());
    }
    for (ClosedSlice closed : closedSlices) {
      double saleValue =
          totalClosedQuantity <= EPSILON
              ? 0.0
              : totalSaleValue * closed.quantity() / totalClosedQuantity;
      double allocatedClosingCommission =
          totalClosedQuantity <= EPSILON
              ? 0.0
              : Math.abs(nz(tx.commission())) * closed.quantity() / totalClosedQuantity;
      ClosedPosition row = new ClosedPosition();
      row.setId(
          syntheticId(
              "CLOSED|"
                  + op.getAccount()
                  + "|"
                  + position.symbol
                  + "|"
                  + ReportingDateHelper.toReportingDate(op.getDate())
                  + "|"
                  + ReportingDateHelper.toReportingDate(closed.openDate())
                  + "|"
                  + closed.quantity()
                  + "|"
                  + saleValue,
              dedup));
      row.setAccount(op.getAccount());
      row.setAssetId(position.assetId);
      row.setSymbol(position.symbol);
      row.setSourceAssetSymbol(position.sourceAssetSymbol);
      row.setType(PositionType.BUY);
      row.setPriceCurrency(position.currency);
      row.setCostCurrency(position.currency);
      row.setProfitCurrency(position.currency);
      row.setCommissionCurrency(position.currency);
      row.setVolume(closed.quantity());
      row.setOpenTime(closed.openDate());
      row.setOpenPrice(closed.averageCost());
      row.setCloseTime(op.getDate());
      row.setClosePrice(closed.quantity() <= EPSILON ? 0.0 : saleValue / closed.quantity());
      row.setPurchaseValue(closed.costBasis());
      row.setSaleValue(saleValue);
      row.setCommission(-(closed.openingCommission() + allocatedClosingCommission));
      row.setSwap(0.0);
      row.setMargin(0.0);
      row.setProfit(saleValue - closed.costBasis());
      row.setImportHistoryId(op.getImportHistoryId());
      row.setImportSourceRowId(op.getImportSourceRowId());
      closedPositions.add(row);
    }
  }

  private List<ClosedSlice> closeReconstructedPosition(
      ReconstructedPosition position, double requestedQuantity) {
    if (requestedQuantity > position.quantity + EPSILON) {
      throw new IllegalStateException(
          "IBKR sell exceeds reconstructed long inventory for "
              + position.symbol
              + ": requested="
              + requestedQuantity
              + ", available="
              + position.quantity);
    }
    double remaining = Math.abs(requestedQuantity);
    List<ClosedSlice> slices = new ArrayList<>();
    while (remaining > EPSILON && !position.lots.isEmpty()) {
      ReconstructedLot lot = position.lots.peekFirst();
      double closeQuantity = Math.min(remaining, lot.quantity);
      double averageCost = lot.quantity <= EPSILON ? 0.0 : lot.costBasis / lot.quantity;
      double averageOpeningCommission =
          lot.quantity <= EPSILON ? 0.0 : lot.commissionCostBasis / lot.quantity;
      double closedCostBasis = averageCost * closeQuantity;
      double closedOpeningCommission = averageOpeningCommission * closeQuantity;
      lot.quantity -= closeQuantity;
      lot.costBasis -= closedCostBasis;
      lot.commissionCostBasis -= closedOpeningCommission;
      position.quantity -= closeQuantity;
      position.costBasis -= closedCostBasis;
      remaining -= closeQuantity;
      slices.add(
          new ClosedSlice(
              closeQuantity, closedCostBasis, averageCost, closedOpeningCommission, lot.openDate));
      if (lot.quantity <= EPSILON) {
        position.lots.removeFirst();
      }
    }
    position.normalize();
    return slices;
  }

  private double inferredBondRedemptionQuantity(ReconstructedPosition position, CanonicalTrade tx) {
    Double redemptionPrice = parseBondCallPrice(tx.description());
    if (redemptionPrice != null && redemptionPrice > 0.0) {
      double quantity = Math.abs(tx.operation().getAmount()) / redemptionPrice;
      return quantity;
    }
    if (position.quantity <= EPSILON || position.costBasis <= EPSILON) {
      return 0.0;
    }
    double averageCost = position.costBasis / position.quantity;
    double quantity =
        averageCost <= EPSILON ? 0.0 : Math.abs(tx.operation().getAmount()) / averageCost;
    return quantity;
  }

  private double grossTradeValue(CanonicalTrade tx) {
    if (Math.abs(nz(tx.grossAmount())) > EPSILON) {
      return Math.abs(tx.grossAmount());
    }
    double net = Math.abs(nz(tx.operation().getAmount()));
    return Math.max(0.0, net - Math.abs(nz(tx.commission())));
  }

  private boolean isKnownBondAsset(String symbol) {
    if (!StringUtils.hasText(symbol)) {
      return false;
    }
    String normalized = symbol.trim().toUpperCase(Locale.ROOT);
    return COMPACT_BOND_SYMBOL_PATTERN.matcher(normalized).matches()
        || ISIN_PATTERN.matcher(normalized).matches();
  }

  private Double parseBondCallPrice(String description) {
    if (!StringUtils.hasText(description)) {
      return null;
    }
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("(?i)for\\s+USD\\s+([0-9]+(?:\\.[0-9]+)?)\\s+per\\s+Bond")
            .matcher(description);
    if (!matcher.find()) {
      return null;
    }
    return parseDouble(matcher.group(1));
  }

  private boolean isBondRedemption(CanonicalTrade tx) {
    if (tx.operation().getType() != CashOperationType.TRANSFER
        || !StringUtils.hasText(tx.symbol())) {
      return false;
    }
    String rawType = tx.rawType() == null ? "" : tx.rawType().trim().toLowerCase(Locale.ROOT);
    String description = tx.description() == null ? "" : tx.description().toLowerCase(Locale.ROOT);
    return rawType.startsWith("corporate action")
        && description.contains("call")
        && description.contains("redemption");
  }

  private void validateQuantityReconciliation(
      Map<String, ReconstructedPosition> positions, List<CashOperation> canonicalOperations) {
    Map<String, Double> netByPosition = new HashMap<>();
    Map<String, ReconstructedPosition> validationPositions = new HashMap<>();
    for (CashOperation operation : canonicalOperations) {
      if (!StringUtils.hasText(operation.getSymbol())) {
        continue;
      }
      Double quantity = parseDouble(commentField(operation.getComment(), "ibkrQuantity"));
      String positionKey = operation.getAccount() + "|" + operation.getSymbol();
      if (operation.getType() == CashOperationType.STOCK_PURCHASE && quantity != null) {
        netByPosition.merge(positionKey, Math.abs(quantity), Double::sum);
      } else if (operation.getType() == CashOperationType.STOCK_SELL && quantity != null) {
        netByPosition.merge(positionKey, -Math.abs(quantity), Double::sum);
      } else if (operation.getType() == CashOperationType.TRANSFER) {
        ReconstructedPosition validationPosition =
            validationPositions.computeIfAbsent(
                positionKey,
                ignored -> {
                  ReconstructedPosition seeded =
                      new ReconstructedPosition(
                          operation.getAccount(),
                          operation.getAssetId(),
                          operation.getSymbol(),
                          operation.getSourceAssetSymbol(),
                          operation.getCurrency());
                  ReconstructedPosition existing = positions.get(positionKey);
                  if (existing != null) {
                    seeded.quantity = existing.quantity;
                    seeded.costBasis = existing.costBasis;
                  }
                  return seeded;
                });
        CanonicalTrade tx = toCanonicalTrade(operation);
        if (tx != null && isBondRedemption(tx)) {
          double redeemed = inferredBondRedemptionQuantity(validationPosition, tx);
          netByPosition.merge(positionKey, -Math.abs(redeemed), Double::sum);
        }
      }
    }
    for (Map.Entry<String, Double> entry : netByPosition.entrySet()) {
      if (entry.getValue() < -EPSILON) {
        throw new IllegalStateException(
            "IBKR transaction history contains an unsupported short/oversell for "
                + entry.getKey()
                + ": netQuantity="
                + entry.getValue());
      }
      ReconstructedPosition position = positions.get(entry.getKey());
      double reconstructed = position == null ? 0.0 : position.quantity;
      if (Math.abs(entry.getValue() - reconstructed) > EPSILON) {
        throw new IllegalStateException(
            "IBKR quantity reconstruction mismatch for "
                + entry.getKey()
                + ": netTransactions="
                + entry.getValue()
                + ", reconstructedOpen="
                + reconstructed);
      }
    }
  }

  private static String commentField(String comment, String key) {
    if (!StringUtils.hasText(comment)) {
      return null;
    }
    for (String part : comment.split("\\|")) {
      String trimmed = part.trim();
      if (trimmed.startsWith(key + "=")) {
        return trimmed.substring((key + "=").length()).trim();
      }
    }
    return null;
  }

  private static Double parseDouble(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Double.valueOf(value.replace(",", ""));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String orDefault(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

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
      value &= Long.MAX_VALUE;
      return -(value == 0 ? 1 : value);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot hash IBKR row id", e);
    }
  }

  public record ReconstructionResult(
      List<OpenedPosition> openedPositions, List<ClosedPosition> closedPositions) {}

  private record CanonicalTrade(
      CashOperation operation,
      String rawType,
      String symbol,
      String rawSymbol,
      String description,
      Double quantity,
      Double price,
      Double grossAmount,
      Double commission) {}

  private static final class ReconstructedPosition {
    private final Long account;
    private final Long assetId;
    private final String symbol;
    private final String sourceAssetSymbol;
    private final CurrencyType currency;
    private final Deque<ReconstructedLot> lots = new ArrayDeque<>();
    private double quantity;
    private double costBasis;

    private ReconstructedPosition(
        Long account,
        Long assetId,
        String symbol,
        String sourceAssetSymbol,
        CurrencyType currency) {
      this.account = account;
      this.assetId = Objects.requireNonNull(assetId, "IBKR canonical asset id");
      this.symbol = symbol;
      this.sourceAssetSymbol = sourceAssetSymbol;
      this.currency = Objects.requireNonNull(currency, "IBKR monetary currency");
    }

    private void addLot(
        ZonedDateTime openDate,
        double quantity,
        double costBasis,
        double commissionCostBasis,
        Long importHistoryId,
        Long sourceRowId) {
      lots.addLast(
          new ReconstructedLot(
              openDate, quantity, costBasis, commissionCostBasis, importHistoryId, sourceRowId));
      this.quantity += quantity;
      this.costBasis += costBasis;
    }

    private void normalize() {
      if (Math.abs(quantity) < EPSILON) {
        quantity = 0.0;
        costBasis = 0.0;
        lots.clear();
      }
    }
  }

  private static final class ReconstructedLot {
    private final ZonedDateTime openDate;
    private double quantity;
    private double costBasis;
    private double commissionCostBasis;
    private final Long importHistoryId;
    private final Long sourceRowId;

    private ReconstructedLot(
        ZonedDateTime openDate,
        double quantity,
        double costBasis,
        double commissionCostBasis,
        Long importHistoryId,
        Long sourceRowId) {
      this.openDate = openDate;
      this.quantity = quantity;
      this.costBasis = costBasis;
      this.commissionCostBasis = commissionCostBasis;
      this.importHistoryId = importHistoryId;
      this.sourceRowId = sourceRowId;
    }

    private ZonedDateTime openDate() {
      return openDate;
    }
  }

  private record ClosedSlice(
      double quantity,
      double costBasis,
      double averageCost,
      double openingCommission,
      ZonedDateTime openDate) {}
}
