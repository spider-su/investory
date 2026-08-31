package com.smartbox.investory.retirement.infrastructure.simulation;

import com.smartbox.investory.retirement.api.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationPlanRevisionEventRepository
    extends JpaRepository<SimulationPlanRevisionEventEntity, Long> {
  List<SimulationPlanRevisionEventEntity> findAllByRevisionIdOrderByYearAscIdAsc(Long revisionId);
}
