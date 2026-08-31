package com.smartbox.investory.investment.ledger.cash.persistence;

import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CashOperationRepository extends JpaRepository<CashOperationEntity, Long> {
  @Query(
      "SELECT DISTINCT operation.account FROM CashOperationEntity operation WHERE operation.account IS NOT NULL")
  List<Long> findAllAccountIds();

  List<CashOperationEntity> findAllByAccount(Long account);

  List<CashOperationEntity> findAllByAccountIn(Collection<Long> accounts);

  List<CashOperationEntity> findAllByOrderByDateDescIdDesc();

  List<CashOperationEntity> findAllByAssetIdAndTypeInOrderByDateDescIdDesc(
      Long assetId, Collection<CashOperationType> types);
}
