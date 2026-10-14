package com.smartbox.investory.longterm.infrastructure.bond;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BondRepository extends JpaRepository<BondEntity, Long> {
  List<BondEntity> findAllByPortfolioIdAndArchivedAtIsNullOrderByName(Long portfolioId);

  List<BondEntity> findAllByPortfolioIdAndArchivedAtIsNotNullOrderByName(Long portfolioId);

  Optional<BondEntity> findByIdAndPortfolioId(Long id, Long portfolioId);

  Optional<BondEntity> findByPortfolioIdAndExternalKey(Long portfolioId, String externalKey);

  @Query(
      "SELECT bond.value AS value, bond.currency AS currency, bond.interestRate AS interestRate, bond.maturityDate AS maturityDate FROM BondEntity bond WHERE bond.portfolioId = :portfolioId AND bond.archivedAt IS NULL AND (bond.acquisitionDate IS NULL OR bond.acquisitionDate <= :date)")
  List<WeightedReturnBond> findWeightedReturnBonds(
      @Param("portfolioId") Long portfolioId, @Param("date") LocalDate date);

  interface WeightedReturnBond {
    BigDecimal getValue();

    com.smartbox.investory.shared.currency.CurrencyType getCurrency();

    BigDecimal getInterestRate();

    LocalDate getMaturityDate();
  }
}
