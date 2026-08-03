package com.example.demo.infrastructure.repository.account;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountDailyRepository extends JpaRepository<AccountDaily, Long> {

  List<AccountDaily> findAllByOrderByDateAscAccountIdAsc();

  List<AccountDaily> findAllByAccountIdOrderByDateAsc(Long accountId);

  @Modifying
  @Query("DELETE FROM AccountDaily")
  void deleteAllRows();

  @Modifying
  @Query(
      """
      DELETE FROM AccountDaily row
      WHERE row.accountId = :accountId AND row.date >= :date
      """)
  void deleteByAccountIdAndDateGreaterThanEqual(
      @Param("accountId") Long accountId, @Param("date") LocalDate date);

  @Query(value = "SELECT investory.refresh_reporting_views()", nativeQuery = true)
  Object refreshReportingViews();
}
