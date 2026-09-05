package com.smartbox.investory.longterm.infrastructure.bond;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BondRepository extends JpaRepository<BondEntity, Long> {
  List<BondEntity> findAllByPortfolioIdAndArchivedAtIsNullOrderByName(Long portfolioId);

  List<BondEntity> findAllByPortfolioIdAndArchivedAtIsNotNullOrderByName(Long portfolioId);

  Optional<BondEntity> findByIdAndPortfolioId(Long id, Long portfolioId);

  Optional<BondEntity> findByPortfolioIdAndExternalKey(Long portfolioId, String externalKey);
}
