package com.smartbox.investory.infrastructure.repository.account;

import java.util.Collection;
import java.util.Map;

public interface AccountRepositoryCustom {

  Map<Long, Account> findMapByIdIn(Collection<Long> ids);
}
