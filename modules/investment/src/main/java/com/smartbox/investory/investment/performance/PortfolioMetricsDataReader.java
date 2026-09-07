package com.smartbox.investory.investment.performance;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Portfolio-scoped persistence reads used by the metrics calculators. */
final class PortfolioMetricsDataReader {
  private final AccountRepository accounts;
  private final AccountStatisticsRepository statistics;
  private final CashOperationRepository cashOperations;
  private final PositionRepository closedPositions;
  private final PositionRepository openPositions;

  PortfolioMetricsDataReader(
      AccountRepository accounts,
      AccountStatisticsRepository statistics,
      CashOperationRepository cashOperations,
      PositionRepository closedPositions,
      PositionRepository openPositions) {
    this.accounts = accounts;
    this.statistics = statistics;
    this.cashOperations = cashOperations;
    this.closedPositions = closedPositions;
    this.openPositions = openPositions;
  }

  Set<Long> accountIds(Long portfolioId) {
    return accounts.findIdsByPortfolioId(portfolioId).stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  List<AccountEntity> accounts(Long portfolioId) {
    return accounts.findAllByPortfolioId(portfolioId);
  }

  List<AccountStatisticsEntity> statistics(Set<Long> accountIds) {
    return statistics.findAllByAccountIdIn(accountIds);
  }

  List<CashOperationEntity> cashOperations(Set<Long> accountIds) {
    return cashOperations.findAllByAccountIn(accountIds);
  }

  List<PositionEntity> closedPositions(Set<Long> accountIds) {
    return closedPositions.findClosedByAccountIn(accountIds);
  }

  List<PositionEntity> openPositions(Set<Long> accountIds) {
    return openPositions.findOpenByAccountIn(accountIds);
  }
}
