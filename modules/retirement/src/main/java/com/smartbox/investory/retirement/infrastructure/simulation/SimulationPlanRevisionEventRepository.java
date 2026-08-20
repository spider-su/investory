package com.smartbox.investory.retirement.infrastructure.simulation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationPlanRevisionEventRepository
    extends JpaRepository<SimulationPlanRevisionEvent, Long> {
  List<SimulationPlanRevisionEvent> findAllByRevisionIdOrderByYearAscIdAsc(Long revisionId);
}
