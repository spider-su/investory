package com.smartbox.investory.investment.infrastructure.persistence;

import com.smartbox.investory.investment.accounting.CashOperationType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CashOperationRepository extends JpaRepository<CashOperationEntity, Long> {
  List<CashOperationEntity> findAllByAccount(Long account);

  List<CashOperationEntity> findAllByAccountIn(Collection<Long> accounts);

  List<CashOperationEntity> findAllByOrderByDateDescIdDesc();

  List<CashOperationEntity> findAllByAssetIdAndTypeInOrderByDateDescIdDesc(
      Long assetId, Collection<CashOperationType> types);
}
