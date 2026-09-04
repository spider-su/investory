package com.smartbox.investory.integrations.management.application.handler;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandler;
import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshPricesJobHandler implements IntegrationJobHandler {
  private final InvestmentMaintenanceApi investmentMaintenance;

  public IntegrationType integrationType() {
    return IntegrationType.MARKET_DATA;
  }

  public String jobType() {
    return "refresh-prices";
  }

  public void execute(IntegrationJobContext context) {
    investmentMaintenance.refreshPrices();
  }
}
