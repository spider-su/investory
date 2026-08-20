package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioAssetAllocationRepository
    extends ReadOnlyRepository<PortfolioAssetAllocation, PortfolioAssetAllocationId> {
  java.util.List<PortfolioAssetAllocation> findAllByPortfolioId(Long portfolioId);
}
