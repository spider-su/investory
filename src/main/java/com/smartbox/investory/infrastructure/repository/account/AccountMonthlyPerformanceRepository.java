package com.smartbox.investory.infrastructure.repository.account;

import com.smartbox.investory.infrastructure.repository.ReadOnlyRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMonthlyPerformanceRepository
    extends ReadOnlyRepository<AccountMonthlyPerformance, AccountMonthlyPerformanceId> {

  List<AccountMonthlyPerformance> findAllByOrderByMonthAscAccountIdAsc();
}
