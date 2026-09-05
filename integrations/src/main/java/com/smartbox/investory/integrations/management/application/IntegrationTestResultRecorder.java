package com.smartbox.investory.integrations.management.application;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.shared.time.ApplicationTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists only sanitized probe metadata after the external call has completed. */
@Service
@RequiredArgsConstructor
public class IntegrationTestResultRecorder {
  private final IntegrationInstanceRepository instanceRepository;
  private final ApplicationTime applicationTime;

  @Transactional
  public void record(
      IntegrationType type, String pluginId, ConnectionTestResult result, String message) {
    IntegrationInstanceEntity instance =
        instanceRepository.findByOwnerIdAndPluginIdAndPluginType(null, pluginId, type).orElse(null);
    if (instance == null) return;
    instance.setLastTestAt(applicationTime.now(applicationTime.businessZone()));
    instance.setLastTestStatus(result.success() ? "SUCCESS" : "FAILED");
    instance.setLastTestMessage(message);
    instance.setUpdatedAt(applicationTime.now(applicationTime.businessZone()));
    instanceRepository.save(instance);
  }
}
