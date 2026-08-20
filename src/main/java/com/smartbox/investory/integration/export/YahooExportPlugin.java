package com.smartbox.investory.integration.export;

import com.smartbox.investory.integration.IntegrationType;
import com.smartbox.investory.integration.PluginConfig;
import com.smartbox.investory.integration.PluginDescriptor;
import com.smartbox.investory.integration.ValidationResult;
import com.smartbox.investory.integration.export.yahoo.YahooExportService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YahooExportPlugin implements ExportPlugin {
  public static final String ID = "yahoo-finance";
  private final YahooExportService service;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public IntegrationType type() {
    return IntegrationType.EXPORT;
  }

  @Override
  public PluginDescriptor descriptor() {
    return new PluginDescriptor(ID, "Yahoo Finance", type(), List.of(), List.of());
  }

  @Override
  public ValidationResult validate(PluginConfig config) {
    return ValidationResult.success();
  }

  @Override
  public void export(String target, PluginConfig config) throws IOException {
    service.exportToYahooCsv(target);
  }
}
