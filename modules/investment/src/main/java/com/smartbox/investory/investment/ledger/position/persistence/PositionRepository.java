package com.smartbox.investory.investment.ledger.position.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

  @Query("SELECT position FROM PositionEntity position WHERE position.closeTime IS NULL")
  List<PositionEntity> findOpen();

  @Query("SELECT position FROM PositionEntity position WHERE position.closeTime IS NOT NULL")
  List<PositionEntity> findClosed();

  @Query(
      "SELECT position FROM PositionEntity position WHERE position.closeTime IS NULL AND position.account IN :accounts")
  List<PositionEntity> findOpenByAccountIn(@Param("accounts") Collection<Long> accounts);

  @Query(
      "SELECT position FROM PositionEntity position WHERE position.closeTime IS NOT NULL AND position.account IN :accounts")
  List<PositionEntity> findClosedByAccountIn(@Param("accounts") Collection<Long> accounts);

  List<PositionEntity> findAllByAccountIn(Collection<Long> accounts);

  @Query(
      "SELECT DISTINCT position.account FROM PositionEntity position WHERE position.account IS NOT NULL")
  List<Long> findDistinctAccountIds();

  @Query(
      "SELECT position FROM PositionEntity position WHERE position.closeTime IS NULL AND position.assetId = :assetId ORDER BY position.account, position.openTime")
  List<PositionEntity> findOpenByAssetId(@Param("assetId") Long assetId);

  @Query(
      "SELECT position FROM PositionEntity position WHERE position.closeTime IS NULL AND position.assetId = :assetId AND position.account IN :accounts ORDER BY position.account, position.openTime")
  List<PositionEntity> findOpenByAssetIdAndAccountIn(
      @Param("assetId") Long assetId, @Param("accounts") Collection<Long> accounts);

  @Query(
      "SELECT position FROM PositionEntity position WHERE position.closeTime IS NOT NULL AND position.assetId = :assetId ORDER BY position.closeTime DESC")
  List<PositionEntity> findClosedByAssetId(@Param("assetId") Long assetId);

  @Query(
      "SELECT position FROM PositionEntity position WHERE position.closeTime IS NOT NULL AND position.assetId = :assetId AND position.account IN :accounts ORDER BY position.closeTime DESC")
  List<PositionEntity> findClosedByAssetIdAndAccountIn(
      @Param("assetId") Long assetId, @Param("accounts") Collection<Long> accounts);

  @Modifying
  @Query(
      "DELETE FROM PositionEntity position WHERE position.closeTime IS NULL AND position.account = :account")
  void deleteOpenByAccount(@Param("account") Long account);

  @Modifying
  @Query(
      "DELETE FROM PositionEntity position WHERE position.closeTime IS NOT NULL AND position.account = :account")
  void deleteClosedByAccount(@Param("account") Long account);

  @Modifying
  @Query(
      "DELETE FROM PositionEntity position WHERE position.closeTime IS NULL AND position.account = :account AND position NOT IN :positions")
  void removeOpenByAccountNotIn(
      @Param("account") Long account, @Param("positions") List<PositionEntity> positions);
}
