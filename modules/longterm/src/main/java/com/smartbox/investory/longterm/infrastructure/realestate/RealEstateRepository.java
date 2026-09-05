package com.smartbox.investory.longterm.infrastructure.realestate;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RealEstateRepository extends JpaRepository<RealEstateEntity, Long> {
  List<RealEstateEntity> findAllByPortfolioIdOrderByName(Long portfolioId);

  List<RealEstateEntity> findAllByPortfolioIdAndArchivedAtIsNullOrderByName(Long portfolioId);

  List<RealEstateEntity> findAllByPortfolioIdAndArchivedAtIsNotNullOrderByName(Long portfolioId);

  Optional<RealEstateEntity> findByIdAndPortfolioId(Long id, Long portfolioId);

  /** Serializes rental history edits, including creation when no contract row exists yet. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from RealEstateEntity e where e.id = :id and e.portfolioId = :portfolioId")
  Optional<RealEstateEntity> lockByIdAndPortfolioId(
      @Param("id") Long id, @Param("portfolioId") Long portfolioId);

  Optional<RealEstateEntity> findByPortfolioIdAndExternalKey(Long portfolioId, String externalKey);
}
