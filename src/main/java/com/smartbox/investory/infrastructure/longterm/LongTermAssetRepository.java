package com.smartbox.investory.infrastructure.longterm;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetRepository extends JpaRepository<LongTermAsset, Long> {
  List<LongTermAsset> findAllByPortfolioIdOrderByName(Long portfolioId);

  Optional<LongTermAsset> findByIdAndPortfolioId(Long id, Long portfolioId);

  Optional<LongTermAsset> findByPortfolioIdAndExternalKey(Long portfolioId, String externalKey);

  List<LongTermAsset> findAllByPortfolioIdAndActiveTrueOrderByName(Long portfolioId);
}
