package com.smartbox.investory.infrastructure.repository.account;

import com.smartbox.investory.infrastructure.repository.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountStatisticsRepository extends ReadOnlyRepository<AccountStatistics, Long> {}
