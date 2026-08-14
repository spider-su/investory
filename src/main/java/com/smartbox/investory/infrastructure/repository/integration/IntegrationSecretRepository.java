package com.smartbox.investory.infrastructure.repository.integration;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSecretRepository extends JpaRepository<IntegrationSecret, Long> {
  Optional<IntegrationSecret> findByIntegrationInstanceIdAndSecretName(
      Long instanceId, String name);

  Iterable<IntegrationSecret> findByIntegrationInstanceId(Long instanceId);

  void deleteByIntegrationInstanceIdAndSecretName(Long instanceId, String name);
}
