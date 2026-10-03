package com.smartbox.investory.ui.marketradar;

import com.smartbox.investory.marketradar.api.MarketRadarApi;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MarketRadarController {
  private final MarketRadarApi radar;

  public MarketRadarController(MarketRadarApi radar) {
    this.radar = radar;
  }

  @GetMapping("/market-radar")
  public String page(
      @RequestParam(required = false) RadarState state,
      @RequestParam(defaultValue = "false") boolean includeNormal,
      Model model) {
    List<RadarSnapshot> all = radar.latestSignals();
    List<RadarSnapshot> visible =
        all.stream()
            .filter(signal -> includeNormal || signal.state() != RadarState.NORMAL)
            .filter(signal -> state == null || signal.state() == state)
            .sorted(
                Comparator.comparingInt((RadarSnapshot signal) -> priority(signal.state()))
                    .thenComparing(RadarSnapshot::symbol))
            .toList();

    model.addAttribute("signals", visible);
    model.addAttribute("totalCount", all.size());
    model.addAttribute(
        "interestingCount", all.stream().filter(s -> s.state() != RadarState.NORMAL).count());
    model.addAttribute("selectedState", state);
    model.addAttribute("includeNormal", includeNormal);
    model.addAttribute(
        "states", Arrays.stream(RadarState.values()).filter(s -> s != RadarState.NORMAL).toList());
    return "market-radar";
  }

  @GetMapping("/market-radar/{symbol}")
  public String detail(@PathVariable String symbol, Model model) {
    String normalized = symbol.trim().toUpperCase(Locale.ROOT);
    List<RadarSnapshot> history = radar.signalHistory(normalized, 90);
    if (history.isEmpty()) return "redirect:/market-radar";
    model.addAttribute("symbol", normalized);
    model.addAttribute("current", history.getFirst());
    model.addAttribute("history", history);
    model.addAttribute("outcomes", radar.outcomes(normalized, 270));
    return "market-radar-detail";
  }

  private int priority(RadarState state) {
    return switch (state) {
      case EMERGING -> 0;
      case TRENDING -> 1;
      case HOT -> 2;
      case EXTENDED -> 3;
      case COOLING -> 4;
      case NORMAL -> 5;
    };
  }
}
