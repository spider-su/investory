package com.smartbox.investory.retirement.infrastructure.annualcost;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetirementAnnualCostYearRepository
    extends JpaRepository<RetirementAnnualCostYearEntity, Long> {
  Optional<RetirementAnnualCostYearEntity> findByPlanIdAndYear(Long planId, int year);

  List<RetirementAnnualCostYearEntity> findAllByPlanIdAndYearLessThanOrderByYearDesc(
      Long planId, int year);
}
