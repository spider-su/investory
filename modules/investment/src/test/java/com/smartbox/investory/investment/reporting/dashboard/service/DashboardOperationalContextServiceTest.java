package com.smartbox.investory.investment.reporting.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportRepository;
import com.smartbox.investory.investment.performance.model.Portfolio;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Dashboard Operational Context Service")
class DashboardOperationalContextServiceTest {
  @DisplayName("no Import Is Explicit And Uses Existing Quality Facts")
  @Test
  void noImportIsExplicitAndUsesExistingQualityFacts() {
    ImportRepository imports = mock(ImportRepository.class);
    AccountStatisticsRepository accounts = mock(AccountStatisticsRepository.class);
    when(imports.findFirstByStatusOrderByFinishedAtDesc(
            com.smartbox.investory.investment.imports.ImportBatchStatus.COMPLETED))
        .thenReturn(java.util.Optional.empty());
    when(accounts.findAll()).thenReturn(List.of());

    var view = new DashboardOperationalContextService(imports, accounts).load(new Portfolio());

    assertThat(view.importContext().available()).isFalse();
    assertThat(view.freshness().latestTransaction()).isNull();
    assertThat(view.yahoo().neverExported()).isTrue();
  }

  @DisplayName("account Counts Exclude Cash Only Accounts")
  @Test
  void accountCountsExcludeCashOnlyAccounts() {
    ImportRepository imports = mock(ImportRepository.class);
    AccountStatisticsRepository statistics = mock(AccountStatisticsRepository.class);
    AccountRepository accounts = mock(AccountRepository.class);
    when(imports.findFirstByStatusOrderByFinishedAtDesc(
            com.smartbox.investory.investment.imports.ImportBatchStatus.COMPLETED))
        .thenReturn(java.util.Optional.empty());
    when(statistics.findAll()).thenReturn(List.of());
    AccountEntity investment = new AccountEntity();
    investment.setCashOnly(false);
    AccountEntity cashOnly = new AccountEntity();
    cashOnly.setCashOnly(true);
    when(accounts.findAll()).thenReturn(List.of(investment, cashOnly));

    var view =
        new DashboardOperationalContextService(imports, statistics, null, accounts)
            .load(new Portfolio());

    assertThat(view.importContext().accountsProcessed()).isEqualTo(1);
    assertThat(view.freshness().accountsUpdated()).isEqualTo(1);
  }
}
