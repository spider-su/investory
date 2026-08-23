package com.smartbox.investory.investment.infrastructure.integration.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSecretRepository extends JpaRepository<IntegrationSecretEntity, Long> {
  Optional<IntegrationSecretEntity> findByIntegrationInstanceIdAndSecretName(
      Long instanceId, String name);

  Iterable<IntegrationSecretEntity> findByIntegrationInstanceId(Long instanceId);

  void deleteByIntegrationInstanceIdAndSecretName(Long instanceId, String name);
}
