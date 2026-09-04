package com.smartbox.investory.longterm.infrastructure.asset;

import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LongTermAssetRepository extends JpaRepository<LongTermAssetEntity, Long> {
  List<LongTermAssetEntity> findAllByPortfolioIdOrderByName(Long portfolioId);

  Optional<LongTermAssetEntity> findByIdAndPortfolioId(Long id, Long portfolioId);

  @Query(
      "select a.type from LongTermAssetEntity a where a.id = :id and a.portfolioId = :portfolioId")
  Optional<LongTermAssetType> findTypeByIdAndPortfolioId(
      @Param("id") Long id, @Param("portfolioId") Long portfolioId);

  Optional<LongTermAssetEntity> findByPortfolioIdAndExternalKey(
      Long portfolioId, String externalKey);

  List<LongTermAssetEntity> findAllByPortfolioIdAndActiveFalseOrderByName(Long portfolioId);
}
