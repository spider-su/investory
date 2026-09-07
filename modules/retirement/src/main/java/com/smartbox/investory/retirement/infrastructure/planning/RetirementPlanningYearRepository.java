package com.smartbox.investory.retirement.infrastructure.planning;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetirementPlanningYearRepository
    extends JpaRepository<RetirementPlanningYearEntity, Long> {
  Optional<RetirementPlanningYearEntity> findByPortfolioIdAndYear(Long portfolioId, int year);

  List<RetirementPlanningYearEntity> findAllByPortfolioIdOrderByYearAsc(Long portfolioId);

  List<RetirementPlanningYearEntity> findAllByPortfolioIdAndYearLessThanOrderByYearAsc(
      Long portfolioId, int year);
}
