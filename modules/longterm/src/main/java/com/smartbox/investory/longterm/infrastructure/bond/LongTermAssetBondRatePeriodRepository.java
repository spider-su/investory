package com.smartbox.investory.longterm.infrastructure.bond;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetBondRatePeriodRepository
    extends JpaRepository<LongTermAssetBondRatePeriodEntity, Long> {
  List<LongTermAssetBondRatePeriodEntity> findAllByAssetIdOrderByValidFrom(Long assetId);

  List<LongTermAssetBondRatePeriodEntity> findAllByAssetIdInOrderByAssetIdAscValidFromAsc(
      Collection<Long> assetIds);
}
