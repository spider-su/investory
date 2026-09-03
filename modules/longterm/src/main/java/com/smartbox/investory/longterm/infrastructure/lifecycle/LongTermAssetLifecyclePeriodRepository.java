package com.smartbox.investory.longterm.infrastructure.lifecycle;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetLifecyclePeriodRepository
    extends JpaRepository<LongTermAssetLifecyclePeriodEntity, Long> {
  List<LongTermAssetLifecyclePeriodEntity> findAllByAssetIdOrderByActiveFrom(Long assetId);

  List<LongTermAssetLifecyclePeriodEntity> findAllByAssetIdInOrderByAssetIdAscActiveFromAsc(
      Collection<Long> assetIds);
}
