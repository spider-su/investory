package com.smartbox.investory.longterm.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetLifecyclePeriodRepository
    extends JpaRepository<LongTermAssetLifecyclePeriod, Long> {
  List<LongTermAssetLifecyclePeriod> findAllByAssetIdOrderByActiveFrom(Long assetId);
}
