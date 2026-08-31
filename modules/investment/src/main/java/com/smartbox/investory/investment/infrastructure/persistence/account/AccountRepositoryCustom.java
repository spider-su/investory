package com.smartbox.investory.investment.infrastructure.persistence.account;

import java.util.Collection;
import java.util.Map;

public interface AccountRepositoryCustom {

  Map<Long, AccountEntity> findMapByIdIn(Collection<Long> ids);
}
