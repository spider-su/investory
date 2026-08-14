package com.smartbox.investory.infrastructure.longterm;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetBondRatePeriodRepository
    extends JpaRepository<LongTermAssetBondRatePeriod, Long> {
  List<LongTermAssetBondRatePeriod> findAllByAssetIdOrderByValidFrom(Long assetId);
}
