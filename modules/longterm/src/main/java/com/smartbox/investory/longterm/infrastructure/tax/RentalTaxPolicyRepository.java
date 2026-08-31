package com.smartbox.investory.longterm.infrastructure.tax;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalTaxPolicyRepository extends JpaRepository<RentalTaxPolicyEntity, Long> {
  List<RentalTaxPolicyEntity> findAllByPortfolioIdOrderByValidFrom(Long portfolioId);

  Optional<RentalTaxPolicyEntity>
      findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToGreaterThanEqualOrderByValidFromDesc(
          Long portfolioId, LocalDate date, LocalDate sameDate);

  Optional<RentalTaxPolicyEntity>
      findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToIsNullOrderByValidFromDesc(
          Long portfolioId, LocalDate date);
}
