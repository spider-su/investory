package com.smartbox.investory.investment.infrastructure.persistence.account;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountStatisticsRepository
    extends ReadOnlyRepository<AccountStatisticsEntity, Long> {

  List<AccountStatisticsEntity> findAllByAccountIdIn(Collection<Long> accountIds);
}
