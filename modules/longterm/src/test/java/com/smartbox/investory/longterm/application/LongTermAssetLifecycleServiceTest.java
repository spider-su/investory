package com.smartbox.investory.longterm.application;
import com.smartbox.investory.longterm.application.service.LongTermAssetLifecycleService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.lifecycle.LongTermAssetLifecyclePeriodEntity;
import com.smartbox.investory.longterm.infrastructure.lifecycle.LongTermAssetLifecyclePeriodRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LongTermAssetLifecycleServiceTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void archiveOnAcquisitionDateCreatesAValidSameDayPeriod() {
    LongTermAssetEntity asset = asset(true, TODAY);
    LongTermAssetRepository assets = mock(LongTermAssetRepository.class);
    LongTermAssetLifecyclePeriodRepository periods =
        mock(LongTermAssetLifecyclePeriodRepository.class);
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    when(periods.findAllByAssetIdOrderByActiveFrom(1L)).thenReturn(List.of());

    new LongTermAssetLifecycleService(assets, periods, CLOCK).archive(1L, 1L);

    ArgumentCaptor<LongTermAssetLifecyclePeriodEntity> captor =
        ArgumentCaptor.forClass(LongTermAssetLifecyclePeriodEntity.class);
    verify(periods).save(captor.capture());
    assertTrue(!captor.getValue().getActiveTo().isBefore(captor.getValue().getActiveFrom()));
    verify(assets).save(asset);
  }

  @Test
  void reactivationAndArchiveOnSameDateNeverCreatesAnInvalidRange() {
    LongTermAssetEntity asset = asset(false, TODAY.minusDays(1));
    LongTermAssetLifecyclePeriodEntity period = period(TODAY, TODAY);
    LongTermAssetRepository assets = mock(LongTermAssetRepository.class);
    LongTermAssetLifecyclePeriodRepository periods =
        mock(LongTermAssetLifecyclePeriodRepository.class);
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    when(periods.findAllByAssetIdOrderByActiveFrom(1L)).thenReturn(List.of(period));

    LongTermAssetLifecycleService service =
        new LongTermAssetLifecycleService(assets, periods, CLOCK);
    service.reactivate(1L, 1L);
    service.archive(1L, 1L);

    assertTrue(!period.getActiveTo().isBefore(period.getActiveFrom()));
  }

  @Test
  void historicalLookupUsesPersistedPeriodsAndInjectedClock() {
    LongTermAssetEntity asset = asset(false, TODAY.minusDays(10));
    LongTermAssetLifecyclePeriodEntity first = period(TODAY.minusDays(10), TODAY.minusDays(5));
    LongTermAssetLifecyclePeriodEntity second = period(TODAY.minusDays(2), null);
    LongTermAssetRepository assets = mock(LongTermAssetRepository.class);
    LongTermAssetLifecyclePeriodRepository periods =
        mock(LongTermAssetLifecyclePeriodRepository.class);
    when(periods.findAllByAssetIdOrderByActiveFrom(1L)).thenReturn(List.of(first, second));

    LongTermAssetLifecycleService service =
        new LongTermAssetLifecycleService(assets, periods, CLOCK);

    assertTrue(service.activeOn(asset, TODAY.minusDays(7)));
    assertTrue(!service.activeOn(asset, TODAY.minusDays(4)));
    assertTrue(service.activeOn(asset, TODAY));
  }

  private static LongTermAssetEntity asset(boolean active, LocalDate acquisitionDate) {
    LongTermAssetEntity asset = new LongTermAssetEntity();
    asset.setId(1L);
    asset.setPortfolioId(1L);
    asset.setActive(active);
    asset.setAcquisitionDate(acquisitionDate);
    return asset;
  }

  private static LongTermAssetLifecyclePeriodEntity period(LocalDate from, LocalDate to) {
    LongTermAssetLifecyclePeriodEntity period = new LongTermAssetLifecyclePeriodEntity();
    period.setAssetId(1L);
    period.setActiveFrom(from);
    period.setActiveTo(to);
    return period;
  }
}
