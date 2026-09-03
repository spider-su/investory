package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repository-backed implementation of the brokerage portfolio context boundary. */
@Service
@Transactional(readOnly = true)
public class BrokeragePortfolioContextReadService implements PortfolioContextReader {
  private final PortfolioKpiSummaryRepository portfolioSummaries;
  private final JdbcTemplate jdbcTemplate;

  public BrokeragePortfolioContextReadService(PortfolioKpiSummaryRepository portfolioSummaries) {
    this(portfolioSummaries, null);
  }

  @Autowired
  public BrokeragePortfolioContextReadService(
      PortfolioKpiSummaryRepository portfolioSummaries, JdbcTemplate jdbcTemplate) {
    this.portfolioSummaries = portfolioSummaries;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<PortfolioContext> findById(Long portfolioId) {
    if (jdbcTemplate != null) {
      return jdbcTemplate.query(
          "SELECT base_currency, local_currency FROM investory.portfolios WHERE id = ?",
          rs ->
              rs.next()
                  ? Optional.of(
                      new PortfolioContext(
                          portfolioId,
                          CurrencyType.valueOf(rs.getString("base_currency")),
                          CurrencyType.valueOf(rs.getString("local_currency"))))
                  : Optional.empty(),
          portfolioId);
    }
    return portfolioSummaries
        .findById(portfolioId)
        .map(
            summary ->
                new PortfolioContext(
                    portfolioId, summary.getBaseCurrency(), localCurrency(portfolioId)));
  }

  private CurrencyType localCurrency(Long portfolioId) {
    if (jdbcTemplate == null) return CurrencyType.PLN;
    return jdbcTemplate.query(
        "SELECT local_currency FROM investory.portfolios WHERE id = ?",
        rs -> rs.next() ? CurrencyType.valueOf(rs.getString(1)) : CurrencyType.PLN,
        portfolioId);
  }
}
