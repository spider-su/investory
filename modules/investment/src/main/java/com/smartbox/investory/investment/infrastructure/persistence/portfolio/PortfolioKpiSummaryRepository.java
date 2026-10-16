package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioKpiSummaryRepository
    extends ReadOnlyRepository<PortfolioKpiSummaryEntity, Long> {

  @Query(
      value =
          "select p.base_currency as baseCurrency, p.local_currency as localCurrency "
              + "from investory.portfolios p where p.id = :portfolioId",
      nativeQuery = true)
  Optional<PortfolioCurrenciesRow> findCurrenciesByPortfolioId(
      @Param("portfolioId") Long portfolioId);

  interface PortfolioCurrenciesRow {
    String getBaseCurrency();

    String getLocalCurrency();
  }
}
