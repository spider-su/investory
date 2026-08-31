package com.smartbox.investory.longterm.infrastructure.rental;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LongTermAssetRentalContractRepository
    extends JpaRepository<LongTermAssetRentalContractEntity, Long> {
  List<LongTermAssetRentalContractEntity> findAllByAssetIdOrderByStartDate(Long assetId);

  List<LongTermAssetRentalContractEntity> findAllByAssetIdOrderByStartDateDescIdDesc(Long assetId);

  @Query(
      "select distinct c from LongTermAssetRentalContractEntity c "
          + "left join fetch c.terms where c.assetId in :assetIds "
          + "order by c.assetId, c.startDate, c.id")
  List<LongTermAssetRentalContractEntity> findAllWithTermsByAssetIdIn(
      @Param("assetIds") java.util.Collection<Long> assetIds);
}
