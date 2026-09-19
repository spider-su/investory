package com.smartbox.investory.ryczalt.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import com.smartbox.investory.ryczalt.settlement.SettlementService;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LegacyAccountingUserApiAdapterTest {
  private final AccountingUserApi legacy = Mockito.mock(AccountingUserApi.class);
  private final RyczaltPeriodJpaRepository periods = Mockito.mock(RyczaltPeriodJpaRepository.class);
  private final SettlementService settlement = Mockito.mock(SettlementService.class);
  private final RyczaltPeriodLifecycleService lifecycle =
      Mockito.mock(RyczaltPeriodLifecycleService.class);
  private final LegacyAccountingUserApiAdapter adapter =
      new LegacyAccountingUserApiAdapter(legacy, periods, settlement, lifecycle);

  @Test
  void delegatesHistoricalSettlementToLegacyApi() {
    YearMonth month = YearMonth.of(2026, 1);
    when(periods.findByProfileIdAndYearAndMonth(7, 2026, 1)).thenReturn(Optional.empty());

    adapter.settle(7, month);

    verify(legacy).settle(7, month);
  }

  @Test
  void usesNativeSettlementForRyczaltBackedPeriod() {
    YearMonth month = YearMonth.of(2026, 1);
    when(periods.findByProfileIdAndYearAndMonth(7, 2026, 1))
        .thenReturn(
            Optional.of(
                new RyczaltPeriodEntity(
                    7, 2026, 1, com.smartbox.investory.ryczalt.domain.PeriodStatus.OPEN)));

    adapter.settle(7, month);

    verify(settlement).settlePeriod(7, month);
    Mockito.verifyNoInteractions(legacy);
  }
}
