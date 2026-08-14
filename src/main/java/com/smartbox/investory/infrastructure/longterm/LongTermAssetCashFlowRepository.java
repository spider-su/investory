package com.smartbox.investory.infrastructure.longterm;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetCashFlowRepository
    extends JpaRepository<LongTermAssetCashFlow, Long> {
  List<LongTermAssetCashFlow> findAllByAssetIdOrderByValidFrom(Long assetId);
}
