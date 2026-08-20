package com.smartbox.investory.integration.infrastructure.persistence;

import com.smartbox.investory.integration.IntegrationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationInstanceRepository extends JpaRepository<IntegrationInstance, Long> {
  Optional<IntegrationInstance> findByOwnerIdAndPluginIdAndPluginType(
      Long ownerId, String pluginId, IntegrationType pluginType);

  List<IntegrationInstance> findByEnabledTrue();
}
