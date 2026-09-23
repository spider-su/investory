package com.smartbox.investory.integrations.management.application.handler;

import com.smartbox.investory.integrations.ksef.KsefSyncJobPort;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandler;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KsefSyncJobHandler implements IntegrationJobHandler {
  private static final String JOB_TYPE = "sync-invoices";

  private final KsefSyncJobPort sync;
  private final boolean enabled;

  public KsefSyncJobHandler(
      KsefSyncJobPort sync, @Value("${app.ryczalt.ksef.sync.enabled:false}") boolean enabled) {
    this.sync = sync;
    this.enabled = enabled;
  }

  @Override
  public IntegrationType integrationType() {
    return IntegrationType.E_INVOICING;
  }

  @Override
  public String jobType() {
    return JOB_TYPE;
  }

  @Override
  public void execute(IntegrationJobContext context) {
    if (!enabled) return;
    ZoneId zone = ZoneId.of(context.job().getTimezone());
    sync.sync(YearMonth.from(context.now().withZoneSameInstant(zone)));
  }
}
