package com.smartbox.investory.infrastructure.repository.portfolio;

import com.smartbox.investory.infrastructure.repository.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioKpiSummaryRepository
    extends ReadOnlyRepository<PortfolioKpiSummary, Long> {}
