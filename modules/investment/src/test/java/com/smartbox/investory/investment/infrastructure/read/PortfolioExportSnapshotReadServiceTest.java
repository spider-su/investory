package com.smartbox.investory.investment.infrastructure.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioExportSnapshotReadServiceTest {

  @Test
  void readsOnlyRowsForPortfolioAccounts() {
    PositionRepository positions = mock(PositionRepository.class);
    AccountStatisticsRepository statistics = mock(AccountStatisticsRepository.class);
    AccountRepository accounts = mock(AccountRepository.class);
    PortfolioExportSnapshotReadService service =
        new PortfolioExportSnapshotReadService(positions, statistics, accounts);
    AccountEntity account = new AccountEntity();
    account.setId(7L);
    PositionEntity position = new PositionEntity();
    position.setAccount(7L);
    position.setSymbol("VWCE.DE");
    position.setVolume(BigDecimal.ONE);
    AccountStatisticsEntity statistic = new AccountStatisticsEntity();
    statistic.setAccountId(7L);
    statistic.setCashBalance(BigDecimal.TEN);
    when(accounts.findAllByPortfolioId(3L)).thenReturn(List.of(account));
    when(positions.findAllByAccountIn(any())).thenReturn(List.of(position));
    when(statistics.findAllByAccountIdIn(any())).thenReturn(List.of(statistic));

    var snapshot = service.currentSnapshot(3L);

    assertThat(snapshot.positions()).hasSize(1);
    assertThat(snapshot.cashBalances()).hasSize(1);
    verify(positions).findAllByAccountIn(Set.of(7L));
    verify(statistics).findAllByAccountIdIn(Set.of(7L));
  }
}
