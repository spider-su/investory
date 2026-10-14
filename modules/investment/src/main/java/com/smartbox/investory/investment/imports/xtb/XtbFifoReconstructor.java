package com.smartbox.investory.investment.imports.xtb;

import com.smartbox.investory.investment.imports.BrokerSourceRowIdentity;
import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.position.PositionType;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Reconstructs currently open XTB lots from the broker's cash-operation comments. */
class XtbFifoReconstructor {

  private static final Pattern LOT_PATTERN =
      Pattern.compile(
          "^(OPEN|CLOSE)\\s+(BUY|SELL)\\s+(?:([A-Z0-9._-]+)\\s+)?([0-9]+(?:[\\.,][0-9]+)?)(?:/[0-9]+(?:[\\.,][0-9]+)?)?\\s+@\\s+([0-9]+(?:[\\.,][0-9]+)?)$",
          Pattern.CASE_INSENSITIVE);

  List<PositionEntity> reconstruct(
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
              LotEvent event = parseLotEvent(op.getComment(), op.getSymbol()).orElse(null);
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

  boolean hasParsedTradeEvent(String comment, String symbol) {
    return parseLotEvent(comment, symbol).isPresent();
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

  private Optional<LotEvent> parseLotEvent(String comment, String symbol) {
    if (!StringUtils.hasText(comment)) {
      return Optional.empty();
    }
    Matcher matcher = LOT_PATTERN.matcher(comment.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    String commentSymbol = matcher.group(3);
    if (StringUtils.hasText(commentSymbol) && !commentSymbol.equalsIgnoreCase(symbol)) {
      return Optional.empty();
    }

    boolean open = "OPEN".equalsIgnoreCase(matcher.group(1));
    PositionType side = XtbWorkbookReader.parsePositionType(matcher.group(2), -1);
    BigDecimal volume = XtbWorkbookReader.parseDecimal(matcher.group(4)).orElseThrow();
    BigDecimal price = XtbWorkbookReader.parseDecimal(matcher.group(5)).orElseThrow();
    return Optional.of(new LotEvent(open, side, volume, price));
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
}
