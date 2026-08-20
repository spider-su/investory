package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PortfolioFallbackReconciliationRepository {
  private final EntityManager entityManager;

  @SuppressWarnings("unchecked")
  public List<Object[]> findAllStatuses() {
    return entityManager
        .createNativeQuery(
            "SELECT fallback_reconciliation_status, realized_profit_difference, "
                + "unrealized_profit_difference, dividends_difference, interest_difference "
                + "FROM investory.recon_v_portfolio_service_fallback")
        .getResultList();
  }
}
