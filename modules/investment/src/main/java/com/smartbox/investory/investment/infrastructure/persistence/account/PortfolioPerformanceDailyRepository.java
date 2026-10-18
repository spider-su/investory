package com.smartbox.investory.investment.infrastructure.persistence.account;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioPerformanceDailyRepository
    extends ReadOnlyRepository<PortfolioPerformanceDailyEntity, PortfolioPerformanceDailyId> {
  List<PortfolioPerformanceDailyRow> findByPortfolioIdAndDateBetweenOrderByDateAsc(
      Long portfolioId, LocalDate from, LocalDate to);

  interface PortfolioPerformanceDailyRow {
    LocalDate getDate();

    java.math.BigDecimal getEndValue();

    java.math.BigDecimal getContributions();

    java.math.BigDecimal getWithdrawals();

    java.math.BigDecimal getInitializationAdjustment();
  }
}
