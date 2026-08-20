package com.smartbox.investory.retirement.infrastructure.simulation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationPlanRepository extends JpaRepository<SimulationPlan, Long> {
  List<SimulationPlan> findAllByPortfolioIdOrderByName(Long portfolioId);

  Optional<SimulationPlan> findByIdAndPortfolioId(Long id, Long portfolioId);

  boolean existsByPortfolioIdAndName(Long portfolioId, String name);
}
