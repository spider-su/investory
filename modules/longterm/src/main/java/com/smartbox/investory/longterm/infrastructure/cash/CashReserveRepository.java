package com.smartbox.investory.longterm.infrastructure.cash;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashReserveRepository extends JpaRepository<CashReserveEntity, Long> {
  List<CashReserveEntity> findAllByPortfolioIdAndArchivedAtIsNullOrderByName(Long portfolioId);

  List<CashReserveEntity> findAllByPortfolioIdAndArchivedAtIsNotNullOrderByName(Long portfolioId);

  Optional<CashReserveEntity> findByIdAndPortfolioId(Long id, Long portfolioId);

  Optional<CashReserveEntity> findByPortfolioIdAndExternalKey(Long portfolioId, String externalKey);
}
