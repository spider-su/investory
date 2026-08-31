package com.smartbox.investory.integrations.management.application;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists only sanitized probe metadata after the external call has completed. */
@Service
@RequiredArgsConstructor
public class IntegrationTestResultRecorder {
  private final IntegrationInstanceRepository instanceRepository;

  @Transactional
  public void record(
      IntegrationType type, String pluginId, ConnectionTestResult result, String message) {
    IntegrationInstanceEntity instance =
        instanceRepository.findByOwnerIdAndPluginIdAndPluginType(null, pluginId, type).orElse(null);
    if (instance == null) return;
    instance.setLastTestAt(ZonedDateTime.now());
    instance.setLastTestStatus(result.success() ? "SUCCESS" : "FAILED");
    instance.setLastTestMessage(message);
    instance.setUpdatedAt(ZonedDateTime.now());
    instanceRepository.save(instance);
  }
}
