package com.smartbox.investory.retirement.infrastructure.simulation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationPlanEventRepository extends JpaRepository<SimulationPlanEventEntity, Long> {
  List<SimulationPlanEventEntity> findAllBySimulationPlanIdOrderByYearAscIdAsc(Long simulationPlanId);

  Optional<SimulationPlanEventEntity> findByIdAndSimulationPlanId(Long id, Long simulationPlanId);
}
