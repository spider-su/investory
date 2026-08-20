package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.investment.api.BrokeragePortfolioContext;
import com.smartbox.investory.investment.api.BrokeragePortfolioContextReader;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repository-backed implementation of the brokerage portfolio context boundary. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BrokeragePortfolioContextReadService implements BrokeragePortfolioContextReader {
  private final PortfolioKpiSummaryRepository portfolioSummaries;

  @Override
  public Optional<BrokeragePortfolioContext> findById(Long portfolioId) {
    return portfolioSummaries
        .findById(portfolioId)
        .map(summary -> new BrokeragePortfolioContext(portfolioId, summary.getBaseCurrency()));
  }
}
