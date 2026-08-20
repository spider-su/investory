package com.smartbox.investory.investment.infrastructure.persistence.account;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMonthlyPerformanceRepository
    extends ReadOnlyRepository<AccountMonthlyPerformance, AccountMonthlyPerformanceId> {

  List<AccountMonthlyPerformance> findAllByOrderByMonthAscAccountIdAsc();
}
