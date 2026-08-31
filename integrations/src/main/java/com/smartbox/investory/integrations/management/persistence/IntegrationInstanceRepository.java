package com.smartbox.investory.integrations.management.persistence;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationInstanceRepository
    extends JpaRepository<IntegrationInstanceEntity, Long> {
  Optional<IntegrationInstanceEntity> findByOwnerIdAndPluginIdAndPluginType(
      Long ownerId, String pluginId, IntegrationType pluginType);

  List<IntegrationInstanceEntity> findByEnabledTrue();
}
