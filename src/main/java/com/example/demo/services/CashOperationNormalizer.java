package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.repository.CashOperation;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CashOperationNormalizer {

  public static final String CLASSIFICATION_VERSION = "xtb-v2026-07-28";
  private static final double EPSILON = 1e-9;
  private static final Duration SUBACCOUNT_PAIR_WINDOW = Duration.ofMinutes(10);
  private static final Duration INTERNAL_TRANSFER_PAIR_WINDOW = Duration.ofHours(12);
  private static final Duration FX_PAIR_WINDOW = Duration.ofMinutes(10);
  private static final Pattern ACCOUNT_TRANSFER_PATTERN =
      Pattern.compile("transfer\\s+(in|out)\\s+operation\\s+on\\s+account\\s+with\\s+id\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ACCOUNT_TO_ACCOUNT_TRANSFER_PATTERN =
      Pattern.compile("transfer\\s+from\\s+(\\d+)\\s+to\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern FX_PATTERN =
      Pattern.compile(
          "currency conversion,\\s*([A-Z]{3})\\s+to\\s+([A-Z]{3}).*?from\\s+TA:\\s*(\\d+)\\s+to:\\s*(\\d+).*?exchange rate:\\s*([0-9]+(?:[\\.,][0-9]+)?)",
          Pattern.CASE_INSENSITIVE);

  public List<NormalizedCashOperation> normalize(List<CashOperation> operations) {
    List<MutableNormalized> normalized = new ArrayList<>();
    for (int index = 0; index < operations.size(); index++) {
      normalized.add(classifyBase(index, operations.get(index)));
    }
    pairSubaccountTransfers(normalized);
    pairInternalTransferLedger(normalized);
    pairFxConversions(normalized);
    return normalized.stream().sorted(Comparator.comparingInt(MutableNormalized::index)).map(MutableNormalized::freeze).toList();
  }

  private MutableNormalized classifyBase(int index, CashOperation operation) {
    CashOperationType rawType = operation.getType();
    String comment = normalizedComment(operation.getComment());
    double amount = nz(operation.getAmount());
    EconomicDirection direction =
        amount > EPSILON ? EconomicDirection.INFLOW : amount < -EPSILON ? EconomicDirection.OUTFLOW : EconomicDirection.NEUTRAL;
    boolean reversalHint = comment.contains("reversal") || comment.contains("reverse") || comment.contains("correction");

    if (rawType == CashOperationType.DEPOSIT) {
      Optional<AccountTransferHint> hint = parseAccountTransfer(operation.getComment());
      if (hint.isPresent()) {
        AccountTransferHint transferHint = hint.get();
        return MutableNormalized.of(
            index,
            operation,
            transferHint.direction() == EconomicDirection.INFLOW
                ? NormalizedCategory.INTERNAL_TRANSFER_IN
                : NormalizedCategory.INTERNAL_TRANSFER_OUT,
            "XTB_ACCOUNT_TRANSFER",
            direction,
            false,
            true,
            false,
            false,
            false,
            false,
            "explicit transfer in/out comment");
      }
      if (amount >= 0.0) {
        return MutableNormalized.of(
            index,
            operation,
            NormalizedCategory.EXTERNAL_DEPOSIT,
            "RAW_DEPOSIT",
            direction,
            true,
            false,
            false,
            false,
            false,
            false,
            "positive raw deposit");
      }
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.EXTERNAL_WITHDRAWAL,
          "RAW_DEPOSIT_NEGATIVE",
          direction,
          true,
          false,
          false,
          false,
          false,
          false,
          "negative raw deposit fallback");
    }

    if (rawType == CashOperationType.WITHDRAWAL) {
      return MutableNormalized.of(
          index,
          operation,
          amount <= 0.0 ? NormalizedCategory.EXTERNAL_WITHDRAWAL : NormalizedCategory.EXTERNAL_DEPOSIT,
          amount <= 0.0 ? "RAW_WITHDRAWAL" : "RAW_WITHDRAWAL_POSITIVE",
          direction,
          true,
          false,
          false,
          false,
          false,
          false,
          "raw withdrawal sign mapping");
    }

    if (rawType == CashOperationType.TRANSFER && parseFxConversion(operation.getComment()).isPresent()) {
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.FX_CONVERSION,
          "FX_CONVERSION",
          direction,
          false,
          true,
          true,
          false,
          false,
          false,
          "currency conversion comment");
    }

    if (rawType == CashOperationType.TRANSFER) {
      Optional<TransferBetweenAccountsHint> hint = parseTransferBetweenAccounts(operation.getComment());
      if (hint.isPresent()) {
        return MutableNormalized.of(
            index,
            operation,
            amount >= 0.0 ? NormalizedCategory.INTERNAL_TRANSFER_IN : NormalizedCategory.INTERNAL_TRANSFER_OUT,
            "ACCOUNT_TRANSFER",
            direction,
            false,
            true,
            false,
            false,
            false,
            false,
            "transfer from X to Y comment");
      }
    }

    if (rawType == CashOperationType.SUBACCOUNT_TRANSFER) {
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.INTERNAL_BOOKKEEPING,
          "SUBACCOUNT_TRANSFER",
          direction,
          false,
          true,
          false,
          false,
          false,
          false,
          "raw subaccount transfer");
    }

    if (rawType == CashOperationType.DIVIDEND) {
      boolean reversal = amount < 0.0 || reversalHint;
      return MutableNormalized.of(
          index,
          operation,
          reversal ? NormalizedCategory.DIVIDEND_REVERSAL : NormalizedCategory.DIVIDEND,
          reversal ? "DIVIDEND_REVERSAL" : "DIVIDEND",
          direction,
          false,
          false,
          false,
          false,
          false,
          reversal,
          reversal ? "negative/correction dividend" : "regular dividend");
    }

    if (rawType == CashOperationType.FREE_FUNDS_INTEREST) {
      boolean reversal = amount < 0.0 || reversalHint;
      return MutableNormalized.of(
          index,
          operation,
          reversal ? NormalizedCategory.INTEREST_REVERSAL : NormalizedCategory.INTEREST,
          reversal ? "INTEREST_REVERSAL" : "INTEREST",
          direction,
          false,
          false,
          false,
          false,
          false,
          reversal,
          reversal ? "negative/correction interest" : "regular interest");
    }

    if (rawType == CashOperationType.WITHHOLDING_TAX) {
      boolean reversal = amount > 0.0 || reversalHint;
      return MutableNormalized.of(
          index,
          operation,
          reversal ? NormalizedCategory.WITHHOLDING_TAX_REVERSAL : NormalizedCategory.WITHHOLDING_TAX,
          reversal ? "WITHHOLDING_TAX_REVERSAL" : "WITHHOLDING_TAX",
          direction,
          false,
          false,
          false,
          false,
          false,
          reversal,
          reversal ? "positive/correction withholding tax" : "regular withholding tax");
    }

    if (rawType == CashOperationType.FREE_FUNDS_INTEREST_TAX
        || rawType == CashOperationType.TRANSACTION_TAX
        || rawType == CashOperationType.STAMP_DUTY) {
      boolean reversal = amount > 0.0 || reversalHint;
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.OTHER_TAX,
          reversal ? "TAX_REVERSAL" : "TAX",
          direction,
          false,
          false,
          false,
          false,
          false,
          reversal,
          "tax mapping");
    }

    if (rawType == CashOperationType.COMMISSION
        || rawType == CashOperationType.SEC_FEE
        || rawType == CashOperationType.SWAP) {
      boolean reversal = amount > 0.0 || reversalHint;
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.FEE,
          reversal ? "FEE_REVERSAL" : "FEE",
          direction,
          false,
          false,
          false,
          false,
          false,
          reversal,
          "fee mapping");
    }

    if (rawType == CashOperationType.STOCK_PURCHASE) {
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.TRADE_PURCHASE,
          "TRADE_PURCHASE",
          direction,
          false,
          false,
          false,
          true,
          false,
          false,
          "raw stock purchase");
    }

    if (rawType == CashOperationType.STOCK_SELL) {
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.TRADE_SALE,
          "TRADE_SALE",
          direction,
          false,
          false,
          false,
          true,
          false,
          false,
          "raw stock sale");
    }

    if (rawType == CashOperationType.CLOSE_TRADE) {
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.REALIZED_TRADE_RESULT,
          "REALIZED_TRADE_RESULT",
          direction,
          false,
          false,
          false,
          true,
          false,
          false,
          "raw close trade");
    }

    if (rawType == CashOperationType.CORRECTION) {
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.CORRECTION,
          "UNCLASSIFIED_CORRECTION",
          direction,
          false,
          false,
          false,
          false,
          true,
          reversalHint,
          "raw correction requires review");
    }

    if (rawType == CashOperationType.ROLLOVER) {
      return MutableNormalized.of(
          index,
          operation,
          NormalizedCategory.REALIZED_TRADE_RESULT,
          "ROLLOVER",
          direction,
          false,
          false,
          false,
          true,
          false,
          false,
          "rollover treated as trade result");
    }

    return MutableNormalized.of(
        index,
        operation,
        NormalizedCategory.UNCLASSIFIED,
        "UNCLASSIFIED",
        direction,
        false,
        false,
        false,
        false,
        rawType == CashOperationType.CORRECTION,
        reversalHint,
        "fallback");
  }

  private void pairSubaccountTransfers(List<MutableNormalized> normalized) {
    Map<String, Deque<MutableNormalized>> outflows = new HashMap<>();
    for (MutableNormalized current : normalized) {
      if (current.category != NormalizedCategory.INTERNAL_BOOKKEEPING) {
        continue;
      }
      String key =
          current.operation.getAccount()
              + "|"
              + roundedAbsAmount(current.operation.getAmount())
              + "|"
              + normalizedComment(current.operation.getComment());
      if (current.direction == EconomicDirection.OUTFLOW) {
        outflows.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(current);
        continue;
      }
      Deque<MutableNormalized> candidates = outflows.get(key);
      if (candidates == null) {
        continue;
      }
      MutableNormalized match = pollWithinWindow(candidates, current.operation.getDate(), SUBACCOUNT_PAIR_WINDOW);
      if (match != null) {
        String groupId = stableGroupId("SUB", match.operation, current.operation);
        current.relatedOperationId = String.valueOf(opId(match.operation));
        match.relatedOperationId = String.valueOf(opId(current.operation));
        current.transferGroupId = groupId;
        match.transferGroupId = groupId;
        current.classificationReason = "paired zero-net subaccount bookkeeping";
        match.classificationReason = "paired zero-net subaccount bookkeeping";
      }
    }
  }

  private void pairInternalTransferLedger(List<MutableNormalized> normalized) {
    Map<String, Deque<MutableNormalized>> outflows = new HashMap<>();
    for (MutableNormalized current : normalized) {
      if (current.category != NormalizedCategory.INTERNAL_TRANSFER_OUT
          && current.category != NormalizedCategory.INTERNAL_TRANSFER_IN) {
        continue;
      }
      String key = current.operation.getCurrency() + "|" + roundedAbsAmount(current.operation.getAmount());
      if (current.category == NormalizedCategory.INTERNAL_TRANSFER_OUT) {
        outflows.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(current);
        continue;
      }
      Deque<MutableNormalized> candidates = outflows.get(key);
      if (candidates == null) {
        continue;
      }
      MutableNormalized match = pollWithinWindow(candidates, current.operation.getDate(), INTERNAL_TRANSFER_PAIR_WINDOW);
      if (match != null && !Objects.equals(match.operation.getAccount(), current.operation.getAccount())) {
        String groupId = stableGroupId("INT", match.operation, current.operation);
        current.relatedOperationId = String.valueOf(opId(match.operation));
        match.relatedOperationId = String.valueOf(opId(current.operation));
        current.transferGroupId = groupId;
        match.transferGroupId = groupId;
        current.classificationReason = "paired transfer in/out ledger rows";
        match.classificationReason = "paired transfer in/out ledger rows";
      }
    }
  }

  private void pairFxConversions(List<MutableNormalized> normalized) {
    Map<String, Deque<MutableNormalized>> groups = new HashMap<>();
    for (MutableNormalized current : normalized) {
      if (current.category != NormalizedCategory.FX_CONVERSION) {
        continue;
      }
      Optional<FxHint> hint = parseFxConversion(current.operation.getComment());
      if (hint.isEmpty()) {
        continue;
      }
      FxHint fxHint = hint.get();
      String key =
          fxHint.sourceAccount()
              + "|"
              + fxHint.targetAccount()
              + "|"
              + fxHint.sourceCurrency()
              + "|"
              + fxHint.targetCurrency()
              + "|"
              + roundedRate(fxHint.rate());
      Deque<MutableNormalized> bucket = groups.computeIfAbsent(key, ignored -> new ArrayDeque<>());
      MutableNormalized match =
          bucket.stream()
              .filter(candidate -> withinWindow(candidate.operation.getDate(), current.operation.getDate(), FX_PAIR_WINDOW))
              .filter(candidate -> !Objects.equals(candidate.operation.getCurrency(), current.operation.getCurrency()))
              .findFirst()
              .orElse(null);
      if (match == null) {
        bucket.addLast(current);
        continue;
      }
      bucket.remove(match);
      String groupId = stableGroupId("FX", match.operation, current.operation);
      current.relatedOperationId = String.valueOf(opId(match.operation));
      match.relatedOperationId = String.valueOf(opId(current.operation));
      current.transferGroupId = groupId;
      match.transferGroupId = groupId;
      current.classificationReason = "paired FX conversion legs";
      match.classificationReason = "paired FX conversion legs";
    }
  }

  private static MutableNormalized pollWithinWindow(
      Deque<MutableNormalized> candidates, ZonedDateTime timestamp, Duration window) {
    while (!candidates.isEmpty()) {
      MutableNormalized candidate = candidates.peekFirst();
      if (withinWindow(candidate.operation.getDate(), timestamp, window)) {
        return candidates.pollFirst();
      }
      candidates.pollFirst();
    }
    return null;
  }

  private static boolean withinWindow(ZonedDateTime left, ZonedDateTime right, Duration window) {
    if (left == null || right == null) {
      return false;
    }
    return Duration.between(left, right).abs().compareTo(window) <= 0;
  }

  private static Optional<AccountTransferHint> parseAccountTransfer(String comment) {
    if (!StringUtils.hasText(comment)) {
      return Optional.empty();
    }
    Matcher matcher = ACCOUNT_TRANSFER_PATTERN.matcher(comment);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(
        new AccountTransferHint(
            "in".equalsIgnoreCase(matcher.group(1)) ? EconomicDirection.INFLOW : EconomicDirection.OUTFLOW,
            Long.parseLong(matcher.group(2))));
  }

  private static Optional<FxHint> parseFxConversion(String comment) {
    if (!StringUtils.hasText(comment)) {
      return Optional.empty();
    }
    Matcher matcher = FX_PATTERN.matcher(comment);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(
        new FxHint(
            matcher.group(1).toUpperCase(Locale.ROOT),
            matcher.group(2).toUpperCase(Locale.ROOT),
            Long.parseLong(matcher.group(3)),
            Long.parseLong(matcher.group(4)),
            Double.parseDouble(matcher.group(5).replace(',', '.'))));
  }

  private static Optional<TransferBetweenAccountsHint> parseTransferBetweenAccounts(String comment) {
    if (!StringUtils.hasText(comment)) {
      return Optional.empty();
    }
    Matcher matcher = ACCOUNT_TO_ACCOUNT_TRANSFER_PATTERN.matcher(comment);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(
        new TransferBetweenAccountsHint(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))));
  }

  private static String normalizedComment(String comment) {
    return comment == null ? "" : comment.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static long roundedAbsAmount(Double amount) {
    return Math.round(Math.abs(nz(amount)) * 100.0d);
  }

  private static long roundedRate(double rate) {
    return Math.round(rate * 1_000_000d);
  }

  private static String stableGroupId(String prefix, CashOperation left, CashOperation right) {
    long leftId = opId(left);
    long rightId = opId(right);
    long low = Math.min(leftId, rightId);
    long high = Math.max(leftId, rightId);
    return prefix + ":" + low + ":" + high;
  }

  private static long opId(CashOperation operation) {
    return operation.getId() != null ? operation.getId() : System.identityHashCode(operation);
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private record AccountTransferHint(EconomicDirection direction, long accountId) {}

  private record TransferBetweenAccountsHint(long sourceAccount, long targetAccount) {}

  private record FxHint(
      String sourceCurrency, String targetCurrency, long sourceAccount, long targetAccount, double rate) {}

  private static final class MutableNormalized {
    private final int index;
    private final CashOperation operation;
    private final NormalizedCategory category;
    private final String subtype;
    private final EconomicDirection direction;
    private final boolean externalFlow;
    private final boolean internalTransfer;
    private final boolean fxConversion;
    private final boolean tradeCashFlow;
    private final boolean correction;
    private final boolean reversal;
    private String relatedOperationId;
    private String transferGroupId;
    private String classificationReason;

    private MutableNormalized(
        int index,
        CashOperation operation,
        NormalizedCategory category,
        String subtype,
        EconomicDirection direction,
        boolean externalFlow,
        boolean internalTransfer,
        boolean fxConversion,
        boolean tradeCashFlow,
        boolean correction,
        boolean reversal,
        String classificationReason) {
      this.index = index;
      this.operation = operation;
      this.category = category;
      this.subtype = subtype;
      this.direction = direction;
      this.externalFlow = externalFlow;
      this.internalTransfer = internalTransfer;
      this.fxConversion = fxConversion;
      this.tradeCashFlow = tradeCashFlow;
      this.correction = correction;
      this.reversal = reversal;
      this.classificationReason = classificationReason;
    }

    private static MutableNormalized of(
        int index,
        CashOperation operation,
        NormalizedCategory category,
        String subtype,
        EconomicDirection direction,
        boolean externalFlow,
        boolean internalTransfer,
        boolean fxConversion,
        boolean tradeCashFlow,
        boolean correction,
        boolean reversal,
        String classificationReason) {
      return new MutableNormalized(
          index,
          operation,
          category,
          subtype,
          direction,
          externalFlow,
          internalTransfer,
          fxConversion,
          tradeCashFlow,
          correction,
          reversal,
          classificationReason);
    }

    private int index() {
      return index;
    }

    private NormalizedCashOperation freeze() {
      return new NormalizedCashOperation(
          operation,
          operation.getType() == null ? null : operation.getType().name(),
          category,
          subtype,
          direction,
          externalFlow,
          internalTransfer,
          fxConversion,
          tradeCashFlow,
          correction,
          reversal,
          relatedOperationId,
          transferGroupId,
          classificationReason,
          CLASSIFICATION_VERSION);
    }
  }

  public record NormalizedCashOperation(
      CashOperation operation,
      String rawOperation,
      NormalizedCategory normalizedCategory,
      String normalizedSubtype,
      EconomicDirection economicDirection,
      boolean externalFlow,
      boolean internalTransfer,
      boolean fxConversion,
      boolean tradeCashFlow,
      boolean correction,
      boolean reversal,
      String relatedOperationId,
      String transferGroupId,
      String classificationReason,
      String classificationVersion) {}

  public enum EconomicDirection {
    INFLOW,
    OUTFLOW,
    NEUTRAL
  }

  public enum NormalizedCategory {
    EXTERNAL_DEPOSIT,
    EXTERNAL_WITHDRAWAL,
    INTERNAL_TRANSFER_IN,
    INTERNAL_TRANSFER_OUT,
    INTERNAL_BOOKKEEPING,
    FX_CONVERSION,
    TRADE_PURCHASE,
    TRADE_SALE,
    REALIZED_TRADE_RESULT,
    DIVIDEND,
    DIVIDEND_REVERSAL,
    INTEREST,
    INTEREST_REVERSAL,
    WITHHOLDING_TAX,
    WITHHOLDING_TAX_REVERSAL,
    FEE,
    OTHER_TAX,
    CORRECTION,
    UNCLASSIFIED
  }
}
