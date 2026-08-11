package com.example.demo.infrastructure.repository.account;

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
  public Map<Long, Account> findMapByIdIn(Collection<Long> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Map.of();
    }
    return entityManager
        .createQuery("select a from Account a where a.id in :ids order by a.id", Account.class)
        .setParameter("ids", ids)
        .getResultList()
        .stream()
        .filter(account -> account.getId() != null)
        .collect(
            Collectors.toMap(
                Account::getId,
                account -> account,
                (existing, replacement) -> existing,
                LinkedHashMap::new));
  }
}
