package com.smartbox.investory.investment.infrastructure.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioExportSnapshotReadServiceTest {

  @Test
  void readsOnlyOpenRowsForPortfolioAccounts() {
    PositionRepository positions = mock(PositionRepository.class);
    AccountStatisticsRepository statistics = mock(AccountStatisticsRepository.class);
    AccountRepository accounts = mock(AccountRepository.class);
    AssetRepository assets = mock(AssetRepository.class);
    PortfolioExportSnapshotReadService service =
        new PortfolioExportSnapshotReadService(positions, statistics, accounts, assets);
    AccountEntity account = new AccountEntity();
    account.setId(7L);
    PositionEntity position = new PositionEntity();
    position.setAccount(7L);
    position.setAssetId(9L);
    position.setSymbol("US91282CRC72");
    position.setVolume(BigDecimal.ONE);
    AssetEntity asset = new AssetEntity();
    asset.setId(9L);
    asset.setAssetType("BOND");
    asset.setMarketPrice(BigDecimal.valueOf(98.81));
    AccountStatisticsEntity statistic = new AccountStatisticsEntity();
    statistic.setAccountId(7L);
    statistic.setCashBalance(BigDecimal.TEN);
    when(accounts.findAllByPortfolioId(3L)).thenReturn(List.of(account));
    when(positions.findOpenByAccountIn(any())).thenReturn(List.of(position));
    when(assets.findAllById(List.of(9L))).thenReturn(List.of(asset));
    when(statistics.findAllByAccountIdIn(any())).thenReturn(List.of(statistic));

    var snapshot = service.currentSnapshot(3L);

    assertThat(snapshot.positions()).isEmpty();
    assertThat(snapshot.cashBalances()).hasSize(2);
    assertThat(snapshot.cashBalances())
        .extracting(PortfolioExportSnapshotReader.ExportCashBalance::amount)
        .anySatisfy(amount -> assertThat(amount).isEqualByComparingTo("10"));
    assertThat(snapshot.cashBalances())
        .extracting(PortfolioExportSnapshotReader.ExportCashBalance::amount)
        .anySatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.9881"));
    verify(positions).findOpenByAccountIn(Set.of(7L));
    verify(statistics).findAllByAccountIdIn(Set.of(7L));
  }
}
