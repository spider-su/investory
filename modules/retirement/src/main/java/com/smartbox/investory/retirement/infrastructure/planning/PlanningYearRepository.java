package com.smartbox.investory.retirement.infrastructure.planning;

import com.smartbox.investory.retirement.api.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningYearRepository extends JpaRepository<PlanningYearEntity, Long> {
  Optional<PlanningYearEntity> findByPortfolioIdAndYear(Long portfolioId, int year);

  List<PlanningYearEntity> findAllByPortfolioIdOrderByYearAsc(Long portfolioId);
}
