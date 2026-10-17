package com.smartbox.investory.investment.infrastructure.persistence.account;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountDailyRepository extends JpaRepository<AccountDailyEntity, Long> {

  List<AccountDailyEntity> findByDateOrderByAccountIdAsc(LocalDate date);

  List<AccountDailyEntity> findByDateAndAccountIdInOrderByAccountIdAsc(
      LocalDate date, java.util.Collection<Long> accountIds);

  List<AccountDailyEntity> findByDateGreaterThanEqualOrderByDateAscAccountIdAsc(LocalDate from);

  List<AccountDailyEntity> findByDateGreaterThanEqualAndAccountIdInOrderByDateAscAccountIdAsc(
      LocalDate from, java.util.Collection<Long> accountIds);

  @Query("select min(row.date) from AccountDailyEntity row where row.accountId in :accountIds")
  LocalDate findEarliestDateByAccountIdIn(@Param("accountIds") Collection<Long> accountIds);

  @Modifying
  @Query(
      """
      DELETE FROM AccountDailyEntity row
      WHERE row.accountId = :accountId AND row.date >= :date
      """)
  void deleteByAccountIdAndDateGreaterThanEqual(
      @Param("accountId") Long accountId, @Param("date") LocalDate date);
}
