package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.account.Account;
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
      Collection<CashOperation> operations, Map<Long, Account> accountsById) {
    if (operations == null || operations.isEmpty() || accountsById.isEmpty()) {
      return Map.of();
    }
    Set<String> countedGroups = new HashSet<>();
    Map<Long, Double> effects = new HashMap<>();
    for (CashOperationNormalizer.NormalizedCashOperation row :
        normalizer.normalize(List.copyOf(operations))) {
      CashOperation operation = row.operation();
      Long accountId = operation.getAccount();
      Account account = accountsById.get(accountId);
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

  private static boolean isEligible(Account account) {
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
}
