package com.smartbox.investory.investment.infrastructure.persistence.account;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMonthlyPerformanceRepository
    extends ReadOnlyRepository<AccountMonthlyPerformanceEntity, AccountMonthlyPerformanceId> {

  List<AccountMonthlyPerformanceEntity> findAllByOrderByMonthAscAccountIdAsc();

  List<AccountMonthlyPerformanceEntity> findByMonthGreaterThanEqualOrderByMonthAscAccountIdAsc(
      LocalDate from);
}
