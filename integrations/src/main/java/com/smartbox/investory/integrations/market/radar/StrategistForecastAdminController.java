package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.application.StrategistForecastImportService;
import com.smartbox.investory.marketradar.application.StrategistForecastImportService.ImportRecord;
import com.smartbox.investory.marketradar.application.StrategistForecastImportService.ImportResult;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/market-radar/strategist-forecasts")
public class StrategistForecastAdminController {
  private final StrategistForecastImportService importer;

  public StrategistForecastAdminController(StrategistForecastImportService importer) {
    this.importer = importer;
  }

  @PostMapping
  @Transactional
  public ImportResult importForecasts(@RequestBody List<ImportRecord> forecasts) {
    return importer.importForecasts(forecasts);
  }
}
