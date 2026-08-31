package com.smartbox.investory.integrations.infrastructure.integration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationJobRepository extends JpaRepository<IntegrationJobEntity, Long> {
  List<IntegrationJobEntity> findByEnabledTrue();

  List<IntegrationJobEntity> findByIntegrationInstanceId(Long integrationInstanceId);
}
