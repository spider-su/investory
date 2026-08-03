package com.example.demo.infrastructure.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OpenedPositionRepository extends JpaRepository<OpenedPosition, Long> {

  @Override
  @Query("SELECT op FROM OpenedPosition op WHERE op.closeTime IS NULL")
  List<OpenedPosition> findAll();

  @Query("SELECT op FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account = :account")
  List<OpenedPosition> findAllByAccount(@Param("account") Long account);

  @Query("SELECT op FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account IN :accounts")
  List<OpenedPosition> findAllByAccountIn(@Param("accounts") Collection<Long> accounts);

  @Query(
      "SELECT op FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.assetId = :assetId ORDER BY op.account, op.openTime")
  List<OpenedPosition> findOpenByAssetId(@Param("assetId") Long assetId);

  @Modifying
  @Query("DELETE FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account = :account")
  void deleteByAccount(@Param("account") Long account);

  @Modifying
  @Query(
      "DELETE FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account = :account AND op NOT IN :openedPositions")
  void removeAllByAccountNotIn(
      @Param("account") Long account,
      @Param("openedPositions") List<OpenedPosition> openedPositions);
}
