package com.example.demo.infrastructure.repository.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioCurrencyBreakdownRepository
    extends JpaRepository<PortfolioCurrencyBreakdown, PortfolioCurrencyBreakdownId> {

  default void deleteAllRows() {}
}
