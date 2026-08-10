package com.example.demo.infrastructure.repository.account;

import java.util.List;
import com.example.demo.infrastructure.repository.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMonthlyPerformanceRepository
    extends ReadOnlyRepository<AccountMonthlyPerformance, AccountMonthlyPerformanceId> {

  List<AccountMonthlyPerformance> findAllByOrderByMonthAscAccountIdAsc();
}
