package com.smartbox.investory.longterm.infrastructure.personal;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalAssetRepository extends JpaRepository<PersonalAssetEntity, Long> {
  List<PersonalAssetEntity> findAllByPortfolioIdAndArchivedAtIsNullOrderByName(Long portfolioId);

  List<PersonalAssetEntity> findAllByPortfolioIdAndArchivedAtIsNotNullOrderByName(Long portfolioId);

  Optional<PersonalAssetEntity> findByIdAndPortfolioId(Long id, Long portfolioId);

  Optional<PersonalAssetEntity> findByPortfolioIdAndExternalKey(
      Long portfolioId, String externalKey);
}
