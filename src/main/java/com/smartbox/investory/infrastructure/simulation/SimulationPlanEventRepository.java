package com.smartbox.investory.infrastructure.simulation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationPlanEventRepository extends JpaRepository<SimulationPlanEvent, Long> {
  List<SimulationPlanEvent> findAllBySimulationPlanIdOrderByYearAscIdAsc(Long simulationPlanId);

  Optional<SimulationPlanEvent> findByIdAndSimulationPlanId(Long id, Long simulationPlanId);
}
