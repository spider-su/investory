package com.smartbox.investory.integrations.infrastructure.integration.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSecretRepository extends JpaRepository<IntegrationSecretEntity, Long> {
  Optional<IntegrationSecretEntity> findByIntegrationInstanceIdAndSecretName(
      Long instanceId, String name);

  Iterable<IntegrationSecretEntity> findByIntegrationInstanceId(Long instanceId);

  void deleteByIntegrationInstanceIdAndSecretName(Long instanceId, String name);
}
