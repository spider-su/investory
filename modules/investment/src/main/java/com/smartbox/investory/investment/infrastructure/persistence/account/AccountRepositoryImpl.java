package com.smartbox.investory.investment.infrastructure.persistence.account;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepositoryCustom {

  private final EntityManager entityManager;

  @Override
  public Map<Long, AccountEntity> findMapByIdIn(Collection<Long> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Map.of();
    }
    return entityManager
        .createQuery(
            "select a from AccountEntity a where a.id in :ids order by a.id", AccountEntity.class)
        .setParameter("ids", ids)
        .getResultList()
        .stream()
        .filter(account -> account.getId() != null)
        .collect(
            Collectors.toMap(
                AccountEntity::getId,
                account -> account,
                (existing, replacement) -> existing,
                LinkedHashMap::new));
  }
}
