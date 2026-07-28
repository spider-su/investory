package com.example.demo.infrastructure.repository.portfolio;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PortfolioFallbackReconciliationRepository {
    private final EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Object[]> findAllStatuses() {
        return entityManager.createNativeQuery(
            "SELECT fallback_reconciliation_status, realized_profit_difference, "
                + "unrealized_profit_difference, dividends_difference, interest_difference "
                + "FROM investory.v_portfolio_service_fallback_reconciliation")
            .getResultList();
    }
}
