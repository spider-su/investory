package com.smartbox.investory.longterm.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermAssetRentalContractRepository extends JpaRepository<LongTermAssetRentalContract, Long> {
  List<LongTermAssetRentalContract> findAllByAssetIdOrderByStartDate(Long assetId);
}
