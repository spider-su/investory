package com.smartbox.investory.retirement.infrastructure.simulation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationPlanRepository extends JpaRepository<SimulationPlanEntity, Long> {
  List<SimulationPlanEntity> findAllByPortfolioIdOrderByName(Long portfolioId);

  Optional<SimulationPlanEntity> findByIdAndPortfolioId(Long id, Long portfolioId);

  boolean existsByPortfolioIdAndName(Long portfolioId, String name);
}
