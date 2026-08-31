package com.smartbox.investory.infrastructure.longterm;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalTaxPolicyRepository extends JpaRepository<RentalTaxPolicy, Long> {
  Optional<RentalTaxPolicy>
      findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToGreaterThanEqualOrderByValidFromDesc(
          Long portfolioId, LocalDate date, LocalDate sameDate);

  Optional<RentalTaxPolicy>
      findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToIsNullOrderByValidFromDesc(
          Long portfolioId, LocalDate date);
}
