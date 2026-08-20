package com.smartbox.investory.integration.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationJobRepository extends JpaRepository<IntegrationJob, Long> {
  List<IntegrationJob> findByEnabledTrue();

  List<IntegrationJob> findByIntegrationInstanceId(Long integrationInstanceId);
}
