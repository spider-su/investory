package com.smartbox.investory.retirement.infrastructure.annualcost;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetirementAnnualCostGroupRepository
    extends JpaRepository<RetirementAnnualCostGroupEntity, Long> {
  List<RetirementAnnualCostGroupEntity> findAllByYearIdOrderBySortOrderAscIdAsc(Long yearId);

  Optional<RetirementAnnualCostGroupEntity> findByIdAndYearId(Long id, Long yearId);

  boolean existsByYearIdAndNameIgnoreCaseAndIdNot(Long yearId, String name, Long id);

  boolean existsByYearIdAndNameIgnoreCase(Long yearId, String name);

  int countByYearId(Long yearId);
}
