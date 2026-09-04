package com.smartbox.investory.retirement.infrastructure.simulation;

import com.smartbox.investory.retirement.api.model.*;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SimulationPlanRepository extends JpaRepository<SimulationPlanEntity, Long> {
  List<SimulationPlanEntity> findAllByPortfolioIdAndArchivedFalseOrderByName(Long portfolioId);

  Optional<SimulationPlanEntity> findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(
      Long portfolioId);

  Optional<SimulationPlanEntity> findFirstByPortfolioIdAndSandboxTrueAndArchivedFalse(
      Long portfolioId);

  Optional<SimulationPlanEntity> findByIdAndPortfolioId(Long id, Long portfolioId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select plan from SimulationPlanEntity plan where plan.id = :id and plan.portfolioId = :portfolioId")
  Optional<SimulationPlanEntity> findByIdAndPortfolioIdForUpdate(
      @Param("id") Long id, @Param("portfolioId") Long portfolioId);

  boolean existsByPortfolioIdAndName(Long portfolioId, String name);
}
