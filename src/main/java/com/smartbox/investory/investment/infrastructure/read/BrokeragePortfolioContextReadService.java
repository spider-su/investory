package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repository-backed implementation of the brokerage portfolio context boundary. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BrokeragePortfolioContextReadService implements PortfolioContextReader {
  private final PortfolioKpiSummaryRepository portfolioSummaries;

  @Override
  public Optional<PortfolioContext> findById(Long portfolioId) {
    return portfolioSummaries
        .findById(portfolioId)
        .map(summary -> new PortfolioContext(portfolioId, summary.getBaseCurrency()));
  }
}
