package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.application.ThemeRadarService;
import com.smartbox.investory.marketradar.domain.ThemeDefinition;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.market-radar.enabled", havingValue = "true")
public class ThemeRadarScheduler {
  private final ThemeRadarService themes;
  private final Clock clock;
  private final String benchmark;

  public ThemeRadarScheduler(
      ThemeRadarService themes,
      Clock clock,
      @Value("${app.market-radar.benchmark:SPY}") String benchmark) {
    this.themes = themes;
    this.clock = clock;
    this.benchmark = benchmark;
  }

  @Scheduled(cron = "${app.market-radar.theme-cron:0 50 22 * * 1-5}", zone = "Europe/Warsaw")
  public void refresh() {
    themes.refresh(loadThemes(), benchmark, LocalDate.now(clock));
  }

  private List<ThemeDefinition> loadThemes() {
    ClassPathResource resource = new ClassPathResource("market-radar/themes.csv");
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .map(this::parse)
          .toList();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to load Market Radar themes", e);
    }
  }

  private ThemeDefinition parse(String line) {
    String[] parts = line.split(",", 3);
    if (parts.length != 3) throw new IllegalArgumentException("Invalid theme row: " + line);
    List<String> members =
        Arrays.stream(parts[2].split(";"))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    return new ThemeDefinition(parts[0].trim(), parts[1].trim(), members);
  }
}
