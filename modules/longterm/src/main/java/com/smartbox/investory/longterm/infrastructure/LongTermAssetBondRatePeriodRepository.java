package com.smartbox.investory.longterm.infrastructure;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetBondRatePeriodRepository
    extends JpaRepository<LongTermAssetBondRatePeriod, Long> {
  List<LongTermAssetBondRatePeriod> findAllByAssetIdOrderByValidFrom(Long assetId);
}
