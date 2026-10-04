package com.smartbox.investory.ui.marketradar;

import com.smartbox.investory.marketradar.api.ThemeRadarApi;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ThemeRadarController {
  private final ThemeRadarApi themes;

  public ThemeRadarController(ThemeRadarApi themes) {
    this.themes = themes;
  }

  @GetMapping("/market-radar/themes")
  public String page(Model model) {
    model.addAttribute("themes", themes.latestThemes());
    return "market-radar-themes";
  }
}
