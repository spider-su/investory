package com.smartbox.investory.ui.marketradar;

import com.smartbox.investory.marketradar.api.MarketRadarApi;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MarketRadarController {
  private final MarketRadarApi radar;

  public MarketRadarController(MarketRadarApi radar) {
    this.radar = radar;
  }

  @GetMapping("/market-radar")
  public String page(Model model) {
    model.addAttribute("signals", radar.latestSignals());
    return "market-radar";
  }
}
