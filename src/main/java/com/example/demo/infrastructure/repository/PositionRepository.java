package com.example.demo.infrastructure.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

  @Query("SELECT position FROM Position position WHERE position.closeTime IS NULL")
  List<Position> findOpen();

  @Query("SELECT position FROM Position position WHERE position.closeTime IS NOT NULL")
  List<Position> findClosed();

  @Query("SELECT position FROM Position position WHERE position.closeTime IS NULL AND position.account = :account")
  List<Position> findOpenByAccount(@Param("account") Long account);

  @Query("SELECT position FROM Position position WHERE position.closeTime IS NULL AND position.account IN :accounts")
  List<Position> findOpenByAccountIn(@Param("accounts") Collection<Long> accounts);

  @Query("SELECT position FROM Position position WHERE position.closeTime IS NOT NULL AND position.account IN :accounts")
  List<Position> findClosedByAccountIn(@Param("accounts") Collection<Long> accounts);

  @Query("SELECT position FROM Position position WHERE position.closeTime IS NULL AND position.assetId = :assetId ORDER BY position.account, position.openTime")
  List<Position> findOpenByAssetId(@Param("assetId") Long assetId);

  @Query("SELECT position FROM Position position WHERE position.closeTime IS NOT NULL AND position.assetId = :assetId ORDER BY position.closeTime DESC")
  List<Position> findClosedByAssetId(@Param("assetId") Long assetId);

  @Modifying
  @Query("DELETE FROM Position position WHERE position.closeTime IS NULL AND position.account = :account")
  void deleteOpenByAccount(@Param("account") Long account);

  @Modifying
  @Query("DELETE FROM Position position WHERE position.closeTime IS NOT NULL AND position.account = :account")
  void deleteClosedByAccount(@Param("account") Long account);

  @Modifying
  @Query("DELETE FROM Position position WHERE position.closeTime IS NULL AND position.account = :account AND position NOT IN :positions")
  void removeOpenByAccountNotIn(
      @Param("account") Long account, @Param("positions") List<Position> positions);
}
