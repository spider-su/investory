package com.smartbox.investory.ui.marketradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.marketradar.api.MarketRadarApi;
import com.smartbox.investory.marketradar.api.MarketRadarRefreshApi;
import com.smartbox.investory.marketradar.api.StrategistOutlookApi;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.ConcurrentModel;

class MarketRadarOutlookPageTest {
  @Test
  void addsRequestedHorizonAndAsOfOutlookWithoutChangingRadarPage() {
    MarketRadarApi radar = mock(MarketRadarApi.class);
    ObjectProvider<MarketRadarRefreshApi> refresh = mock(ObjectProvider.class);
    ObjectProvider<StrategistOutlookApi> outlookProvider = mock(ObjectProvider.class);
    StrategistOutlookApi outlook = mock(StrategistOutlookApi.class);
    when(radar.latestSignals()).thenReturn(java.util.List.of());
    when(outlookProvider.getIfAvailable()).thenReturn(outlook);
    var controller = new MarketRadarController(radar, refresh, outlookProvider);
    var model = new ConcurrentModel();
    LocalDate horizon = LocalDate.of(2027, 12, 31);
    LocalDate snapshot = LocalDate.of(2026, 10, 10);

    String view = controller.page(null, false, horizon, snapshot, model);

    assertThat(view).isEqualTo("market-radar");
    assertThat(model.getAttribute("outlook")).isNull();
    verify(outlook).outlook(horizon, snapshot);
  }
}
