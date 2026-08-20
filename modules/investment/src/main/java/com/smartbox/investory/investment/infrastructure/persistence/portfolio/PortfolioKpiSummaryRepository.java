package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioKpiSummaryRepository
    extends ReadOnlyRepository<PortfolioKpiSummary, Long> {}
