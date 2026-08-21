package com.smartbox.investory.longterm.infrastructure.rental;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetRentalContractRepository
    extends JpaRepository<LongTermAssetRentalContractEntity, Long> {
  List<LongTermAssetRentalContractEntity> findAllByAssetIdOrderByStartDate(Long assetId);
}
