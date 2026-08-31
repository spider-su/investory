package com.smartbox.investory.investment.ledger.cash;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Calculates account-only funding effects for paired XTB sub-account transfers. */
public final class XtbAccountFundingCalculator {

  private final CashOperationNormalizer normalizer;

  public XtbAccountFundingCalculator(CashOperationNormalizer normalizer) {
    this.normalizer = normalizer;
  }

  public Map<Long, Double> calculate(
      Collection<CashOperationEntity> operations, Map<Long, AccountEntity> accountsById) {
    if (operations == null || operations.isEmpty() || accountsById.isEmpty()) {
      return Map.of();
    }
    Set<String> countedGroups = new HashSet<>();
    Map<Long, Double> effects = new HashMap<>();
    for (CashOperationNormalizer.NormalizedCashOperation row :
        normalizer.normalize(List.copyOf(operations))) {
      CashOperationEntity operation = row.operation();
      Long accountId = operation.getAccount();
      AccountEntity account = accountsById.get(accountId);
      if (!isEligible(account)
          || operation.getType() == null
          || operation.getType() != CashOperationType.SUBACCOUNT_TRANSFER
          || row.transferGroupId() == null
          || !countedGroups.add(groupKey(row.transferGroupId(), accountId))) {
        continue;
      }
      CashOperationNormalizer.SubaccountTransferHint hint =
          CashOperationNormalizer.parseSubaccountTransfer(operation.getComment()).orElse(null);
      if (hint == null) {
        continue;
      }
      double effect = Math.abs(nz(operation.getAmount()));
      if (hint.targetAccount() == accountId) {
        effects.merge(accountId, effect, Double::sum);
      } else if (hint.sourceAccount() == accountId) {
        effects.merge(accountId, -effect, Double::sum);
      }
    }
    return effects;
  }

  private static boolean isEligible(AccountEntity account) {
    return account != null
        && "XTB".equalsIgnoreCase(account.getProvider())
        && account.getCurrency() == CurrencyType.USD;
  }

  private static String groupKey(String transferGroupId, Long accountId) {
    return transferGroupId + "|" + accountId;
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static double nz(java.math.BigDecimal value) {
    return value == null ? 0.0 : value.doubleValue();
  }
}
