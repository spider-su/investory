package com.example.demo.infrastructure.repository.portfolio;

import com.example.demo.infrastructure.repository.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioCurrencyBreakdownRepository
    extends ReadOnlyRepository<PortfolioCurrencyBreakdown, PortfolioCurrencyBreakdownId> {}
