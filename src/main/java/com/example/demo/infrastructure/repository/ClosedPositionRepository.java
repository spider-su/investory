package com.example.demo.infrastructure.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClosedPositionRepository extends JpaRepository<ClosedPosition, Long> {

  @Override
  @Query("SELECT cp FROM ClosedPosition cp WHERE cp.closeTime IS NOT NULL")
  List<ClosedPosition> findAll();

  @Query(
      "SELECT cp FROM ClosedPosition cp WHERE cp.closeTime IS NOT NULL AND cp.account IN :accounts")
  List<ClosedPosition> findAllByAccountIn(@Param("accounts") Collection<Long> accounts);

  @Query(
      "SELECT cp FROM ClosedPosition cp WHERE cp.closeTime IS NOT NULL AND cp.assetId = :assetId ORDER BY cp.closeTime DESC")
  List<ClosedPosition> findClosedByAssetId(@Param("assetId") Long assetId);

  @Modifying
  @Query("DELETE FROM ClosedPosition cp WHERE cp.closeTime IS NOT NULL AND cp.account = :account")
  void deleteByAccount(@Param("account") Long account);
}
