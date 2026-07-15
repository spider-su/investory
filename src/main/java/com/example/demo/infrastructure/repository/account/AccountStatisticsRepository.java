package com.example.demo.infrastructure.repository.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountStatisticsRepository extends JpaRepository<AccountStatistics, Long> {

  @Modifying
  @Query("DELETE FROM AccountStatistics")
  void deleteAllRows();

  @Modifying
  @Query(value = "SELECT refresh_account_statistics()", nativeQuery = true)
  void refreshAll();
}
