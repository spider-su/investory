package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.api.operations.ImportOperationsReader;
import com.smartbox.investory.investment.api.operations.PortfolioExposureReader;
import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.performance.PortfolioMetricsService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InvestmentOperationalReadService
    implements PortfolioOperationsReader, ImportOperationsReader, PortfolioExposureReader {
  private final PortfolioMetricsService portfolios;
  private final ImportRepository imports;
  private final PositionRepository positions;
  private final CurrencyRateService currencyRates;

  @Override
  public PortfolioOperationsSnapshot portfolio() {
    return portfolio(1L);
  }

  public PortfolioOperationsSnapshot portfolio(Long portfolioId) {
    var value = portfolios.calculateTotalProfitLoss(portfolioId);
    return new PortfolioOperationsSnapshot(
        value.getBaseCurrency().name(),
        decimal(value.getBalance()),
        decimal(value.getTotalProfit()),
        decimal(value.getUnrealizedProfit()),
        decimal(value.getRealizedProfit()),
        decimal(value.getDividends()),
        decimal(value.getCapitalGainsTax()));
  }

  @Override
  public java.util.Optional<ImportOperationsSnapshot> latestImport() {
    return imports
        .findFirstByOrderByIdDesc()
        .map(
            batch ->
                new ImportOperationsSnapshot(
                    batch.getId(),
                    com.smartbox.investory.investment.api.importing.ImportBroker.valueOf(
                        batch.getBroker().name()),
                    batch.getStatus().name(),
                    batch.getStartedAt(),
                    batch.getFinishedAt()));
  }

  @Override
  public List<SymbolExposure> symbolExposures() {
    Map<String, Double> values = new HashMap<>();
    positions
        .findOpen()
        .forEach(
            position -> {
              if (position.getSymbol() == null) return;
              double price =
                  position.getMarketPrice() != null
                      ? position.getMarketPrice().doubleValue()
                      : position.getOpenPrice() == null
                          ? 0.0
                          : position.getOpenPrice().doubleValue();
              double nativeValue = Math.abs(position.signedQuantity() * price);
              double base =
                  currencyRates.convertToBaseCurrency(
                      nativeValue, CurrencyType.USD, position.getPriceCurrency());
              values.merge(position.getSymbol(), base, Double::sum);
            });
    return values.entrySet().stream()
        .map(entry -> new SymbolExposure(entry.getKey(), decimal(entry.getValue()), "USD"))
        .toList();
  }

  private static BigDecimal decimal(double value) {
    return BigDecimal.valueOf(value);
  }
}
