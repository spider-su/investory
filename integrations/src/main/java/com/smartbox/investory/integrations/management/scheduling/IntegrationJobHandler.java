package com.smartbox.investory.integrations.management.scheduling;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;

public interface IntegrationJobHandler {
  IntegrationType integrationType();

  String jobType();

  void execute(IntegrationJobContext context);
}
