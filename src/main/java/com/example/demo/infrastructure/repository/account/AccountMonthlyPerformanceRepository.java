package com.example.demo.infrastructure.repository.account;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMonthlyPerformanceRepository
    extends JpaRepository<AccountMonthlyPerformance, AccountMonthlyPerformanceId> {

  List<AccountMonthlyPerformance> findAllByOrderByMonthAscAccountIdAsc();

}
