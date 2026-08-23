package com.smartbox.investory.integrations.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.OpenedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPositionRepository;
import com.smartbox.investory.investment.market.fx.CurrencyRateService;
import com.smartbox.investory.testsupport.portfolio.PortfolioBuilders;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConcentrationAlertRuleTest {

  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private CurrencyRateService currencyRateService;

  private NotificationProperties properties;
  private ConcentrationAlertRule rule;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.setConcentrationThresholdPct(25.0);
    rule = new ConcentrationAlertRule(openedPositionRepository, currencyRateService, properties);
    // Identity FX conversion for simplicity. lenient() because the empty-portfolio test skips
    // conversion.
    org.mockito.Mockito.lenient()
        .when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
  }

  @Test
  void evaluate_firesWhenSymbolExceedsThreshold() {
    when(openedPositionRepository.findAll())
        .thenReturn(
            List.of(
                position(PortfolioTestData.AAPL, 10.0, 100.0), // 1000
                position(PortfolioTestData.MSFT, 1.0, 100.0), // 100
                position(PortfolioTestData.SPY, 1.0, 100.0) // 100
                ));

    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
    // AAPL is ~83% of total -> must trigger.
    assertTrue(result.get().contains("AAPL.US"));
  }

  @Test
  void evaluate_isQuietForBalancedPortfolio() {
    when(openedPositionRepository.findAll())
        .thenReturn(
            List.of(
                position(PortfolioTestData.AAPL, 1.0, 100.0),
                position(PortfolioTestData.MSFT, 1.0, 100.0),
                position(PortfolioTestData.SPY, 1.0, 100.0),
                position(PortfolioTestData.TSLA, 1.0, 100.0),
                position(PortfolioTestData.BTC, 1.0, 100.0)));

    assertFalse(rule.evaluate().isPresent());
  }

  @Test
  void evaluate_isSafeWhenPortfolioIsEmpty() {
    when(openedPositionRepository.findAll()).thenReturn(List.of());

    assertFalse(rule.evaluate().isPresent());
  }

  private static OpenedPosition position(
      PortfolioTestData.AssetDefinition asset, double volume, double price) {
    return PortfolioBuilders.openPosition(asset)
        .quantity(volume)
        .price(price)
        .marketPrice(price)
        .build();
  }
}
