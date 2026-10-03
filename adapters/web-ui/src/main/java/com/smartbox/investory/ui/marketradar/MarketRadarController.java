package com.smartbox.investory.ui.marketradar;

import com.smartbox.investory.marketradar.application.MarketRadarScanner;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MarketRadarController {
  private final MarketRadarScanner scanner;

  public MarketRadarController(MarketRadarScanner scanner) {
    this.scanner = scanner;
  }

  @GetMapping("/market-radar")
  public String page(Model model) {
    model.addAttribute("signals", scanner.latest());
    return "market-radar";
  }
}
