package com.smartbox.investory.retirement.infrastructure.planning;

import com.smartbox.investory.retirement.planning.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningYearValueRepository extends JpaRepository<PlanningYearValueEntity, Long> {
  List<PlanningYearValueEntity> findAllByPlanningYearIdAndValueKind(
      Long planningYearId, PlanningValueKind valueKind);

  Optional<PlanningYearValueEntity> findByPlanningYearIdAndValueKindAndMetric(
      Long planningYearId, PlanningValueKind valueKind, PlanningMetric metric);
}
