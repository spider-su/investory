package com.smartbox.investory.investment.infrastructure.persistence.account;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AccountDailyRepository extends JpaRepository<AccountDailyEntity, Long> {

  List<AccountDailyEntity> findAllByOrderByDateAscAccountIdAsc();

  List<AccountDailyEntity> findAllByAccountIdOrderByDateAsc(Long accountId);

  @Modifying
  @Query("DELETE FROM AccountDailyEntity")
  void deleteAllRows();

  @Modifying
  @Query(
      """
      DELETE FROM AccountDailyEntity row
      WHERE row.accountId = :accountId AND row.date >= :date
      """)
  void deleteByAccountIdAndDateGreaterThanEqual(
      @Param("accountId") Long accountId, @Param("date") LocalDate date);

  @Query(value = "SELECT investory.refresh_app_views()", nativeQuery = true)
  Object refreshReportingViews();

  @Query(value = "SELECT investory.refresh_recon_views()", nativeQuery = true)
  Object refreshReconciliationViews();

  @Query(value = "SELECT investory.refresh_reconstructed_position_daily()", nativeQuery = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  Object refreshReconstructedPositionDaily();

  @Query(
      value = "SELECT investory.refresh_reconstructed_account_market_daily()",
      nativeQuery = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  Object refreshReconstructedAccountMarketDaily();

  @Query(value = "SELECT investory.refresh_reconstructed_cash_daily()", nativeQuery = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  Object refreshReconstructedCashDaily();

  @Query(value = "SELECT investory.refresh_account_daily_reconciliation()", nativeQuery = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  Object refreshAccountDailyReconciliation();

  @Query(value = "SELECT investory.refresh_reconciliation_reporting_views()", nativeQuery = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  Object refreshReconciliationReportingViews();
}
