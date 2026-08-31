package com.smartbox.investory.infrastructure.repository.portfolio;

import com.smartbox.investory.infrastructure.repository.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioCurrencyBreakdownRepository
    extends ReadOnlyRepository<PortfolioCurrencyBreakdown, PortfolioCurrencyBreakdownId> {}
