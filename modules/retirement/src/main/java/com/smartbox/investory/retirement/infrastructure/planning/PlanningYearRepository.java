package com.smartbox.investory.retirement.infrastructure.planning;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningYearRepository extends JpaRepository<PlanningYear, Long> {
  Optional<PlanningYear> findByPortfolioIdAndYear(Long portfolioId, int year);

  List<PlanningYear> findAllByPortfolioIdOrderByYearAsc(Long portfolioId);
}
