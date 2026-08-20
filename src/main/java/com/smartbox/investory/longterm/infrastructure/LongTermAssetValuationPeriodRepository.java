package com.smartbox.investory.longterm.infrastructure;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetValuationPeriodRepository
    extends JpaRepository<LongTermAssetValuationPeriod, Long> {
  List<LongTermAssetValuationPeriod> findAllByAssetIdOrderByValidFrom(Long assetId);
}
