package com.example.demo.infrastructure.repository.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountDailyRepository extends JpaRepository<AccountDaily, Long> {

  @Modifying
  @Query("DELETE FROM AccountDaily")
  void deleteAllRows();
}
