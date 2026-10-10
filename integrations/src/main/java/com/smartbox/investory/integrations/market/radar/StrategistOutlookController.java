package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.api.StrategistOutlookApi;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market-radar/strategist-outlook")
public class StrategistOutlookController {
  private final StrategistOutlookApi outlook;
  private final Clock clock;

  public StrategistOutlookController(StrategistOutlookApi outlook, Clock clock) {
    this.outlook = outlook;
    this.clock = clock;
  }

  @GetMapping
  public StrategistOutlookApi.Outlook outlook(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return outlook.outlook(targetDate, asOf == null ? LocalDate.now(clock) : asOf);
  }
}
