package com.smartbox.investory.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefApi;
import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefSyncResult;
import com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RyczaltKsefSchedulerTest {
  @Mock private RyczaltKsefApi ksef;
  @Mock private RyczaltPeriodJpaRepository periods;

  @Test
  void syncsCurrentMonthForEveryConfiguredProfile() {
    when(periods.findDistinctProfileIds()).thenReturn(List.of(1L, 2L));
    when(ksef.sync(1L, YearMonth.of(2026, 9), Set.of(KsefSyncMode.SALES, KsefSyncMode.PURCHASES)))
        .thenReturn(new RyczaltKsefSyncResult(0, 0, 0, 0, 0));
    when(ksef.sync(2L, YearMonth.of(2026, 9), Set.of(KsefSyncMode.SALES, KsefSyncMode.PURCHASES)))
        .thenReturn(new RyczaltKsefSyncResult(0, 0, 0, 0, 0));

    var scheduler =
        new RyczaltKsefScheduler(
            ksef,
            periods,
            Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneId.of("Europe/Warsaw")),
            ZoneId.of("Europe/Warsaw"));

    scheduler.syncCurrentMonth();

    verify(ksef)
        .sync(1L, YearMonth.of(2026, 9), Set.of(KsefSyncMode.SALES, KsefSyncMode.PURCHASES));
    verify(ksef)
        .sync(2L, YearMonth.of(2026, 9), Set.of(KsefSyncMode.SALES, KsefSyncMode.PURCHASES));
  }
}
