package com.smartbox.investory.longterm.infrastructure.rental;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetCashFlowRepository
    extends JpaRepository<LongTermAssetCashFlowEntity, Long> {
  List<LongTermAssetCashFlowEntity> findAllByAssetIdOrderByValidFrom(Long assetId);
}
