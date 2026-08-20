package com.smartbox.investory.retirement.infrastructure.simulation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationPlanRevisionRepository
    extends JpaRepository<SimulationPlanRevision, Long> {
  Optional<SimulationPlanRevision> findByIdAndSimulationPlanId(Long id, Long planId);

  Optional<SimulationPlanRevision> findBySimulationPlanIdAndRevisionNumber(
      Long planId, int revisionNumber);

  List<SimulationPlanRevision> findAllBySimulationPlanIdOrderByRevisionNumberDesc(Long planId);

  long countBySimulationPlanId(Long planId);
}
