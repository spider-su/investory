package com.smartbox.investory.ui.marketradar;

import com.smartbox.investory.marketradar.api.MediaRadarApi;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MediaRadarController {
  private final MediaRadarApi media;

  public MediaRadarController(MediaRadarApi media) {
    this.media = media;
  }

  @GetMapping("/market-radar/media")
  public String page(@RequestParam(required = false) String symbol, Model model) {
    model.addAttribute("signals", media.attentionSignals(100));
    if (symbol != null && !symbol.isBlank()) {
      String normalized = symbol.trim().toUpperCase(Locale.ROOT);
      model.addAttribute("selectedSymbol", normalized);
      model.addAttribute("mentions", media.recentMentions(normalized, 100));
    }
    return "market-radar-media";
  }
}
