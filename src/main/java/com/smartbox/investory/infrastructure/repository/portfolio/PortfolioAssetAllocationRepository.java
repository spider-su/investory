package com.smartbox.investory.infrastructure.repository.portfolio;

import com.smartbox.investory.infrastructure.repository.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioAssetAllocationRepository
    extends ReadOnlyRepository<PortfolioAssetAllocation, PortfolioAssetAllocationId> {
  java.util.List<PortfolioAssetAllocation> findAllByPortfolioId(Long portfolioId);
}
