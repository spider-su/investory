package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CashOperationType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CashOperationRepository extends JpaRepository<CashOperation, Long> {
  List<CashOperation> findAllByAccount(Long account);

  List<CashOperation> findAllByAccountIn(Collection<Long> accounts);

  List<CashOperation> findAllByOrderByDateDescIdDesc();

  List<CashOperation> findAllByAssetIdAndTypeInOrderByDateDescIdDesc(
      Long assetId, Collection<CashOperationType> types);
}
