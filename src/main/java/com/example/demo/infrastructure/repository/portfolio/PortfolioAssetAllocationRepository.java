package com.example.demo.infrastructure.repository.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioAssetAllocationRepository
    extends JpaRepository<PortfolioAssetAllocation, PortfolioAssetAllocationId> {}
