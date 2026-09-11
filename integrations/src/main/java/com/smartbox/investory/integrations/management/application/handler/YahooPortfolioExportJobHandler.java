package com.smartbox.investory.integrations.management.application.handler;

import com.smartbox.investory.integrations.export.yahoo.YahooExportPlugin;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandler;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YahooPortfolioExportJobHandler implements IntegrationJobHandler {
  private final IntegrationConfigurationService configurationService;
  private final YahooExportPlugin yahooExport;

  @Override
  public IntegrationType integrationType() {
    return IntegrationType.EXPORT;
  }

  @Override
  public String jobType() {
    return "export-portfolio";
  }

  @Override
  public void execute(IntegrationJobContext context) {
    try {
      yahooExport.exportScheduled(configurationService.resolve(context.instance()));
    } catch (IOException exception) {
      throw new IllegalStateException("Yahoo Finance export failed", exception);
    }
  }
}
