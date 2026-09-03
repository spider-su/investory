package com.smartbox.investory.investment.imports.ibkr;

import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationRepository;
import com.smartbox.investory.investment.ledger.position.PositionType;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.reporting.ReportingDateHelper;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
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

  private static final BigDecimal EPSILON = new BigDecimal("0.000001");
  private static final java.util.regex.Pattern COMPACT_BOND_SYMBOL_PATTERN =
      java.util.regex.Pattern.compile("^T\\d{6,}(?:\\.[A-Z]{2})?$");
  private static final java.util.regex.Pattern ISIN_PATTERN =
      java.util.regex.Pattern.compile("^[A-Z]{2}[A-Z0-9]{9}[0-9]$");

  private final CashOperationRepository cashOperationRepository;
  private final PositionRepository openedPositionRepository;
  private final PositionRepository closedPositionRepository;

  @Transactional
  public ReconstructionResult rebuildFromCanonicalHistory(
      Long accountId, List<PositionEntity> authoritativeOpenPositions) {
    List<CashOperationEntity> canonicalOperations =
        cashOperationRepository.findAllByAccount(accountId).stream()
            .sorted(
                Comparator.comparing(CashOperationEntity::getDate)
                    .thenComparing(op -> orDefault(op.getSymbol(), ""))
                    .thenComparing(op -> orDefault(op.getComment(), ""))
                    .thenComparing(CashOperationEntity::getId))
            .toList();

    Map<String, Integer> dedup = new HashMap<>();
    Map<String, ReconstructedPosition> positions = new LinkedHashMap<>();
    List<PositionEntity> closed = new ArrayList<>();

    canonicalOperations.stream()
        .map(this::toCanonicalTrade)
        .filter(Objects::nonNull)
        .forEach(tx -> applyTrade(positions, closed, dedup, tx));

    validateQuantityReconciliation(positions, canonicalOperations);

    List<PositionEntity> canonicalOpen = buildOpenPositions(positions, dedup);
    List<PositionEntity> open =
        mergeAuthoritativeOpenPositionFallback(
            canonicalOpen, authoritativeOpenPositions, canonicalOperations);

    replaceDerivedPositions(accountId, open, closed);
    return new ReconstructionResult(open, closed);
  }

  private void replaceDerivedPositions(
      Long accountId, List<PositionEntity> open, List<PositionEntity> closed) {
    openedPositionRepository.deleteOpenByAccount(accountId);
    closedPositionRepository.deleteClosedByAccount(accountId);
    if (!closed.isEmpty()) {
      closedPositionRepository.saveAll(closed);
      closedPositionRepository.flush();
    }
    if (!open.isEmpty()) {
      openedPositionRepository.saveAll(open);
      openedPositionRepository.flush();
    }
  }

  private CanonicalTrade toCanonicalTrade(CashOperationEntity operation) {
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
    BigDecimal quantity = parseDecimal(commentField(operation.getComment(), "ibkrQuantity"));
    BigDecimal price = parseDecimal(commentField(operation.getComment(), "ibkrPrice"));
    BigDecimal grossAmount = parseDecimal(commentField(operation.getComment(), "ibkrGrossAmount"));
    BigDecimal commission = parseDecimal(commentField(operation.getComment(), "ibkrCommission"));
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
      List<PositionEntity> closedPositions,
      Map<String, Integer> dedup,
      CanonicalTrade tx) {
    CashOperationEntity op = tx.operation();
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
      if (tx.quantity() == null || tx.quantity().abs().compareTo(EPSILON) < 0) {
        return;
      }
      BigDecimal qty = tx.quantity().abs();
      BigDecimal grossValue = grossTradeValue(tx);
      BigDecimal value =
          grossValue.compareTo(EPSILON) > 0 ? grossValue : qty.multiply(nz(tx.price()));
      position.addLot(
          op.getDate(),
          qty,
          value,
          nz(tx.commission()).abs(),
          op.getImportHistoryId(),
          op.getImportSourceRowId());
      return;
    }

    BigDecimal requestedQuantity;
    if (op.getType() == CashOperationType.TRANSFER) {
      requestedQuantity = inferredBondRedemptionQuantity(position, tx);
    } else {
      requestedQuantity = tx.quantity() == null ? BigDecimal.ZERO : tx.quantity().abs();
    }
    if (requestedQuantity.compareTo(EPSILON) <= 0) {
      return;
    }
    List<ClosedSlice> slices = closeReconstructedPosition(position, requestedQuantity);
    addPositionEntities(closedPositions, dedup, position, tx, slices);
  }

  private List<PositionEntity> buildOpenPositions(
      Map<String, ReconstructedPosition> reconstructedPositions, Map<String, Integer> dedup) {
    List<PositionEntity> positions = new ArrayList<>();
    for (ReconstructedPosition reconstructed : reconstructedPositions.values()) {
      if (reconstructed.quantity.compareTo(EPSILON) <= 0) {
        continue;
      }
      int lotIndex = 0;
      for (ReconstructedLot lot : reconstructed.lots) {
        if (lot.quantity.compareTo(EPSILON) <= 0) {
          continue;
        }
        PositionEntity position = new PositionEntity();
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
        position.setOpenPrice(
            lot.quantity.signum() == 0
                ? BigDecimal.ZERO
                : lot.costBasis.divide(lot.quantity, 16, java.math.RoundingMode.HALF_UP));
        position.setCommission(lot.commissionCostBasis.negate());
        position.setSwap(BigDecimal.ZERO);
        position.setProfit(BigDecimal.ZERO);
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
  private List<PositionEntity> mergeAuthoritativeOpenPositionFallback(
      List<PositionEntity> canonicalOpen,
      List<PositionEntity> authoritativeOpenPositions,
      List<CashOperationEntity> canonicalOperations) {
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
        .map(PositionEntity::getSymbol)
        .filter(StringUtils::hasText)
        .forEach(canonicalOpenSymbols::add);

    List<PositionEntity> merged = new ArrayList<>(canonicalOpen);
    for (PositionEntity snapshotPosition : authoritativeOpenPositions) {
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

  private void addPositionEntities(
      List<PositionEntity> closedPositions,
      Map<String, Integer> dedup,
      ReconstructedPosition position,
      CanonicalTrade tx,
      List<ClosedSlice> closedSlices) {
    if (closedSlices.isEmpty()) {
      return;
    }
    CashOperationEntity op = tx.operation();
    BigDecimal totalClosedQuantity =
        closedSlices.stream().map(ClosedSlice::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalSaleValue = grossTradeValue(tx);
    if (totalSaleValue.compareTo(EPSILON) <= 0) {
      totalSaleValue = totalClosedQuantity.multiply(nz(tx.price()));
    }
    for (ClosedSlice closed : closedSlices) {
      BigDecimal saleValue =
          totalClosedQuantity.compareTo(EPSILON) <= 0
              ? BigDecimal.ZERO
              : totalSaleValue
                  .multiply(closed.quantity())
                  .divide(totalClosedQuantity, 16, java.math.RoundingMode.HALF_UP);
      BigDecimal allocatedClosingCommission =
          totalClosedQuantity.compareTo(EPSILON) <= 0
              ? BigDecimal.ZERO
              : nz(tx.commission())
                  .abs()
                  .multiply(closed.quantity())
                  .divide(totalClosedQuantity, 16, java.math.RoundingMode.HALF_UP);
      PositionEntity row = new PositionEntity();
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
      row.setClosePrice(
          closed.quantity().signum() == 0
              ? BigDecimal.ZERO
              : saleValue.divide(closed.quantity(), 16, java.math.RoundingMode.HALF_UP));
      row.setPurchaseValue(closed.costBasis());
      row.setSaleValue(saleValue);
      row.setCommission(closed.openingCommission().add(allocatedClosingCommission).negate());
      row.setSwap(BigDecimal.ZERO);
      row.setMargin(BigDecimal.ZERO);
      row.setProfit(saleValue.subtract(closed.costBasis()));
      row.setImportHistoryId(op.getImportHistoryId());
      row.setImportSourceRowId(op.getImportSourceRowId());
      closedPositions.add(row);
    }
  }

  private List<ClosedSlice> closeReconstructedPosition(
      ReconstructedPosition position, BigDecimal requestedQuantity) {
    if (requestedQuantity.compareTo(position.quantity.add(EPSILON)) > 0) {
      throw new IllegalStateException(
          "IBKR sell exceeds reconstructed long inventory for "
              + position.symbol
              + ": requested="
              + requestedQuantity
              + ", available="
              + position.quantity);
    }
    BigDecimal remaining = requestedQuantity.abs();
    List<ClosedSlice> slices = new ArrayList<>();
    while (remaining.compareTo(EPSILON) > 0 && !position.lots.isEmpty()) {
      ReconstructedLot lot = position.lots.peekFirst();
      BigDecimal closeQuantity = remaining.min(lot.quantity);
      BigDecimal averageCost =
          lot.quantity.signum() == 0
              ? BigDecimal.ZERO
              : lot.costBasis.divide(lot.quantity, 16, java.math.RoundingMode.HALF_UP);
      BigDecimal averageOpeningCommission =
          lot.quantity.signum() == 0
              ? BigDecimal.ZERO
              : lot.commissionCostBasis.divide(lot.quantity, 16, java.math.RoundingMode.HALF_UP);
      BigDecimal closedCostBasis = averageCost.multiply(closeQuantity);
      BigDecimal closedOpeningCommission = averageOpeningCommission.multiply(closeQuantity);
      lot.quantity = lot.quantity.subtract(closeQuantity);
      lot.costBasis = lot.costBasis.subtract(closedCostBasis);
      lot.commissionCostBasis = lot.commissionCostBasis.subtract(closedOpeningCommission);
      position.quantity = position.quantity.subtract(closeQuantity);
      position.costBasis = position.costBasis.subtract(closedCostBasis);
      remaining = remaining.subtract(closeQuantity);
      slices.add(
          new ClosedSlice(
              closeQuantity, closedCostBasis, averageCost, closedOpeningCommission, lot.openDate));
      if (lot.quantity.compareTo(EPSILON) <= 0) {
        position.lots.removeFirst();
      }
    }
    position.normalize();
    return slices;
  }

  private BigDecimal inferredBondRedemptionQuantity(
      ReconstructedPosition position, CanonicalTrade tx) {
    BigDecimal redemptionPrice = parseBondCallPrice(tx.description());
    if (redemptionPrice != null && redemptionPrice.signum() > 0) {
      return tx.operation()
          .getAmount()
          .abs()
          .divide(redemptionPrice, 16, java.math.RoundingMode.HALF_UP);
    }
    if (position.quantity.compareTo(EPSILON) <= 0 || position.costBasis.compareTo(EPSILON) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal averageCost =
        position.costBasis.divide(position.quantity, 16, java.math.RoundingMode.HALF_UP);
    return averageCost.compareTo(EPSILON) <= 0
        ? BigDecimal.ZERO
        : tx.operation().getAmount().abs().divide(averageCost, 16, java.math.RoundingMode.HALF_UP);
  }

  private BigDecimal grossTradeValue(CanonicalTrade tx) {
    if (nz(tx.grossAmount()).abs().compareTo(EPSILON) > 0) {
      return nz(tx.grossAmount()).abs();
    }
    BigDecimal net = tx.operation().getAmount().abs();
    return net.subtract(nz(tx.commission()).abs()).max(BigDecimal.ZERO);
  }

  private boolean isKnownBondAsset(String symbol) {
    if (!StringUtils.hasText(symbol)) {
      return false;
    }
    String normalized = symbol.trim().toUpperCase(Locale.ROOT);
    return COMPACT_BOND_SYMBOL_PATTERN.matcher(normalized).matches()
        || ISIN_PATTERN.matcher(normalized).matches();
  }

  private BigDecimal parseBondCallPrice(String description) {
    if (!StringUtils.hasText(description)) {
      return null;
    }
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("(?i)for\\s+USD\\s+([0-9]+(?:\\.[0-9]+)?)\\s+per\\s+Bond")
            .matcher(description);
    if (!matcher.find()) {
      return null;
    }
    return parseDecimal(matcher.group(1));
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
      Map<String, ReconstructedPosition> positions, List<CashOperationEntity> canonicalOperations) {
    Map<String, BigDecimal> netByPosition = new HashMap<>();
    Map<String, ReconstructedPosition> validationPositions = new HashMap<>();
    for (CashOperationEntity operation : canonicalOperations) {
      if (!StringUtils.hasText(operation.getSymbol())) {
        continue;
      }
      BigDecimal quantity = parseDecimal(commentField(operation.getComment(), "ibkrQuantity"));
      String positionKey = operation.getAccount() + "|" + operation.getSymbol();
      if (operation.getType() == CashOperationType.STOCK_PURCHASE && quantity != null) {
        netByPosition.merge(positionKey, quantity.abs(), BigDecimal::add);
      } else if (operation.getType() == CashOperationType.STOCK_SELL && quantity != null) {
        netByPosition.merge(positionKey, quantity.abs().negate(), BigDecimal::add);
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
          BigDecimal redeemed = inferredBondRedemptionQuantity(validationPosition, tx);
          netByPosition.merge(positionKey, redeemed.abs().negate(), BigDecimal::add);
        }
      }
    }
    for (Map.Entry<String, BigDecimal> entry : netByPosition.entrySet()) {
      if (entry.getValue().compareTo(EPSILON.negate()) < 0) {
        throw new IllegalStateException(
            "IBKR transaction history contains an unsupported short/oversell for "
                + entry.getKey()
                + ": netQuantity="
                + entry.getValue());
      }
      ReconstructedPosition position = positions.get(entry.getKey());
      BigDecimal reconstructed = position == null ? BigDecimal.ZERO : position.quantity;
      if (entry.getValue().subtract(reconstructed).abs().compareTo(EPSILON) > 0) {
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

  private static BigDecimal parseDecimal(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return new BigDecimal(value.replace(",", ""));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String orDefault(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private static BigDecimal nz(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
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
      List<PositionEntity> openedPositions, List<PositionEntity> closedPositions) {}

  private record CanonicalTrade(
      CashOperationEntity operation,
      String rawType,
      String symbol,
      String rawSymbol,
      String description,
      BigDecimal quantity,
      BigDecimal price,
      BigDecimal grossAmount,
      BigDecimal commission) {}

  private static final class ReconstructedPosition {
    private final Long account;
    private final Long assetId;
    private final String symbol;
    private final String sourceAssetSymbol;
    private final CurrencyType currency;
    private final Deque<ReconstructedLot> lots = new ArrayDeque<>();
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal costBasis = BigDecimal.ZERO;

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
        BigDecimal quantity,
        BigDecimal costBasis,
        BigDecimal commissionCostBasis,
        Long importHistoryId,
        Long sourceRowId) {
      lots.addLast(
          new ReconstructedLot(
              openDate, quantity, costBasis, commissionCostBasis, importHistoryId, sourceRowId));
      this.quantity = this.quantity.add(quantity);
      this.costBasis = this.costBasis.add(costBasis);
    }

    private void normalize() {
      if (quantity.abs().compareTo(EPSILON) < 0) {
        quantity = BigDecimal.ZERO;
        costBasis = BigDecimal.ZERO;
        lots.clear();
      }
    }
  }

  private static final class ReconstructedLot {
    private final ZonedDateTime openDate;
    private BigDecimal quantity;
    private BigDecimal costBasis;
    private BigDecimal commissionCostBasis;
    private final Long importHistoryId;
    private final Long sourceRowId;

    private ReconstructedLot(
        ZonedDateTime openDate,
        BigDecimal quantity,
        BigDecimal costBasis,
        BigDecimal commissionCostBasis,
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
      BigDecimal quantity,
      BigDecimal costBasis,
      BigDecimal averageCost,
      BigDecimal openingCommission,
      ZonedDateTime openDate) {}
}
