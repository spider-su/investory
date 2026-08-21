package com.smartbox.investory.longterm.infrastructure;

import com.smartbox.investory.longterm.api.LongTermAssetType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LongTermAssetRepository extends JpaRepository<LongTermAsset, Long> {
  List<LongTermAsset> findAllByPortfolioIdOrderByName(Long portfolioId);

  Optional<LongTermAsset> findByIdAndPortfolioId(Long id, Long portfolioId);

  @Query("select a.type from LongTermAsset a where a.id = :id and a.portfolioId = :portfolioId")
  Optional<LongTermAssetType> findTypeByIdAndPortfolioId(
      @Param("id") Long id, @Param("portfolioId") Long portfolioId);

  Optional<LongTermAsset> findByPortfolioIdAndExternalKey(Long portfolioId, String externalKey);

  List<LongTermAsset> findAllByPortfolioIdAndActiveTrueOrderByName(Long portfolioId);
}
