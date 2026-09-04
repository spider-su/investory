package com.smartbox.investory.integrations.management.application.handler;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandler;
import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshFxJobHandler implements IntegrationJobHandler {
  private final InvestmentMaintenanceApi investmentMaintenance;

  public IntegrationType integrationType() {
    return IntegrationType.FX_DATA;
  }

  public String jobType() {
    return "refresh-rates";
  }

  public void execute(IntegrationJobContext context) {
    var result = investmentMaintenance.refreshCurrency();
    if (result == null || !result.failed().isEmpty()) {
      throw new IllegalStateException(
          result == null ? "FX refresh returned no result" : String.join("; ", result.failed()));
    }
  }
}
