package com.smartbox.investory.retirement.infrastructure.simulation;

import com.smartbox.investory.retirement.api.model.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationPlanRevisionRepository
    extends JpaRepository<SimulationPlanRevisionEntity, Long> {
  Optional<SimulationPlanRevisionEntity> findByIdAndSimulationPlanId(Long id, Long planId);

  Optional<SimulationPlanRevisionEntity> findBySimulationPlanIdAndRevisionNumber(
      Long planId, int revisionNumber);

  List<SimulationPlanRevisionEntity> findAllBySimulationPlanIdOrderByRevisionNumberDesc(
      Long planId);

  Optional<SimulationPlanRevisionEntity> findFirstBySimulationPlanIdOrderByRevisionNumberDesc(
      Long planId);

  long countBySimulationPlanId(Long planId);
}
