package com.smartbox.investory.marketradar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import com.smartbox.investory.marketradar.port.RadarSnapshotStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketRadarScannerTest {

  @Test
  void continuesWhenOneSymbolFails() {
    MarketRadarService radar = mock(MarketRadarService.class);
    RadarSnapshotStore store = mock(RadarSnapshotStore.class);
    RadarSnapshot good =
        new RadarSnapshot(
            "GOOD",
            LocalDate.of(2026, 10, 2),
            RadarState.NORMAL,
            100,
            0.01,
            0.02,
            1.0,
            0.01,
            50.0,
            List.of());

    when(radar.analyze("BAD")).thenThrow(new IllegalStateException("provider failed"));
    when(radar.analyze("GOOD")).thenReturn(Optional.of(good));

    List<RadarSnapshot> refreshed =
        new MarketRadarScanner(radar, store).refresh(List.of("BAD", "GOOD"));

    assertThat(refreshed).containsExactly(good);
    verify(store).save(good);
  }
}
