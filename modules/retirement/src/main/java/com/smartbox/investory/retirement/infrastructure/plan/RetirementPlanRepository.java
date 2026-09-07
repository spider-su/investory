package com.smartbox.investory.retirement.infrastructure.plan;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetirementPlanRepository extends JpaRepository<RetirementPlanEntity, Long> {
  List<RetirementPlanEntity> findAllByPortfolioIdAndArchivedFalseOrderByName(Long portfolioId);

  Optional<RetirementPlanEntity> findByIdAndPortfolioId(Long id, Long portfolioId);

  Optional<RetirementPlanEntity> findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(
      Long portfolioId);
}
