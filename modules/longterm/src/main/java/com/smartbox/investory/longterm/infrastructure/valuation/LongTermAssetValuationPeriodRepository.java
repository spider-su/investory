package com.smartbox.investory.longterm.infrastructure.valuation;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetValuationPeriodRepository
    extends JpaRepository<LongTermAssetValuationPeriodEntity, Long> {
  List<LongTermAssetValuationPeriodEntity> findAllByAssetIdOrderByValidFrom(Long assetId);
}
