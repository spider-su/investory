package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repository-backed implementation of the brokerage portfolio context boundary. */
@Service
@Transactional(readOnly = true)
public class BrokeragePortfolioContextReadService implements PortfolioContextReader {
  private final PortfolioKpiSummaryRepository portfolioSummaries;

  public BrokeragePortfolioContextReadService(PortfolioKpiSummaryRepository portfolioSummaries) {
    this.portfolioSummaries = portfolioSummaries;
  }

  @Override
  public Optional<PortfolioContext> findById(Long portfolioId) {
    return portfolioSummaries
        .findCurrenciesByPortfolioId(portfolioId)
        .map(
            currencies ->
                new PortfolioContext(
                    portfolioId,
                    CurrencyType.valueOf(currencies.getBaseCurrency()),
                    CurrencyType.valueOf(currencies.getLocalCurrency())));
  }
}
