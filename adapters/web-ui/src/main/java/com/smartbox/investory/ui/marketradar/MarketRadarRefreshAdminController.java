package com.smartbox.investory.ui.marketradar;

import com.smartbox.investory.marketradar.api.MarketRadarRefreshApi;
import com.smartbox.investory.marketradar.domain.RadarRunSummary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/market-radar")
public class MarketRadarRefreshAdminController {
  private final ObjectProvider<MarketRadarRefreshApi> refresh;

  public MarketRadarRefreshAdminController(ObjectProvider<MarketRadarRefreshApi> refresh) {
    this.refresh = refresh;
  }

  @PostMapping("/refresh")
  @ResponseStatus(HttpStatus.OK)
  public RadarRunSummary refresh() {
    MarketRadarRefreshApi api = refresh.getIfAvailable();
    if (api == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Market Radar is disabled. Set MARKET_RADAR_ENABLED=true.");
    }
    return api.refreshNow();
  }
}
