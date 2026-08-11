package com.example.demo.infrastructure.repository.account;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long>, AccountRepositoryCustom {

  List<Account> findAllByProviderIgnoreCase(String provider);

  java.util.Optional<Account> findByProviderIgnoreCaseAndExternalAccountId(
      String provider, String externalAccountId);

  @Query(
      value =
          """
          select a.id as accountId, p.base_currency as baseCurrency
          from investory.accounts a
          join investory.portfolios p on p.id = a.portfolio_id
          where a.id in (:accountIds)
          """,
      nativeQuery = true)
  List<AccountPortfolioCurrencyRow> findPortfolioCurrenciesByAccountIdIn(
      @Param("accountIds") Collection<Long> accountIds);

  interface AccountPortfolioCurrencyRow {
    Long getAccountId();

    String getBaseCurrency();
  }
}
