package com.smartbox.investory.retirement.infrastructure.planning;

import com.smartbox.investory.retirement.planning.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningYearValueRepository extends JpaRepository<PlanningYearValue, Long> {
  List<PlanningYearValue> findAllByPlanningYearIdAndValueKind(
      Long planningYearId, PlanningValueKind valueKind);

  Optional<PlanningYearValue> findByPlanningYearIdAndValueKindAndMetric(
      Long planningYearId, PlanningValueKind valueKind, PlanningMetric metric);
}
