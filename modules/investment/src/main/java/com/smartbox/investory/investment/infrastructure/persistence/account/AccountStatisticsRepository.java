package com.smartbox.investory.investment.infrastructure.persistence.account;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountStatisticsRepository
    extends ReadOnlyRepository<AccountStatisticsEntity, Long> {}
