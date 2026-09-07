package com.smartbox.investory.retirement.infrastructure.plan;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetirementPlanEventRepository
    extends JpaRepository<RetirementPlanEventEntity, Long> {
  List<RetirementPlanEventEntity> findAllByPlanIdOrderByYearAscIdAsc(Long planId);

  Optional<RetirementPlanEventEntity> findByIdAndPlanId(Long id, Long planId);
}
