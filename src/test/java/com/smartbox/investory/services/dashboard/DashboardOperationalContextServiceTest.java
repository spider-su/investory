package com.smartbox.investory.services.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.infrastructure.repository.account.AccountStatisticsRepository;
import com.smartbox.investory.infrastructure.repository.imports.ImportRepository;
import com.smartbox.investory.services.models.Portfolio;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardOperationalContextServiceTest {
  @Test
  void noImportIsExplicitAndUsesExistingQualityFacts() {
    ImportRepository imports = mock(ImportRepository.class);
    AccountStatisticsRepository accounts = mock(AccountStatisticsRepository.class);
    when(imports.findFirstByStatusOrderByFinishedAtDesc(
            com.smartbox.investory.infrastructure.ImportBatchStatus.COMPLETED))
        .thenReturn(java.util.Optional.empty());
    when(accounts.findAll()).thenReturn(List.of());

    var view = new DashboardOperationalContextService(imports, accounts).load(new Portfolio());

    assertThat(view.importContext().available()).isFalse();
    assertThat(view.freshness().latestTransaction()).isNull();
    assertThat(view.yahoo().neverExported()).isTrue();
  }
}
