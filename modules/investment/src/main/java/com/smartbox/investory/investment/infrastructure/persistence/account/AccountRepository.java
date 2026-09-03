package com.smartbox.investory.investment.infrastructure.persistence.account;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository
    extends JpaRepository<AccountEntity, Long>, AccountRepositoryCustom {

  List<AccountEntity> findAllByPortfolioId(Long portfolioId);

  @Query(
      "SELECT DISTINCT account.portfolioId FROM AccountEntity account WHERE account.portfolioId IS NOT NULL")
  List<Long> findDistinctPortfolioIds();

  List<AccountEntity> findAllByProviderIgnoreCase(String provider);

  java.util.Optional<AccountEntity> findByProviderIgnoreCaseAndExternalAccountId(
      String provider, String externalAccountId);

  java.util.Optional<AccountEntity> findByPortfolioIdAndProviderIgnoreCaseAndExternalAccountId(
      Long portfolioId, String provider, String externalAccountId);

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
