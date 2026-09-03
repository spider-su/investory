package com.smartbox.investory.integrations.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.operations.PortfolioExposureReader;
import com.smartbox.investory.investment.api.operations.PortfolioExposureReader.SymbolExposure;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Concentration Alert Rule")
class ConcentrationAlertRuleTest {

  @Mock private PortfolioExposureReader investment;

  private NotificationProperties properties;
  private ConcentrationAlertRule rule;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.setConcentrationThresholdPct(25.0);
    rule = new ConcentrationAlertRule(investment, properties);
  }

  @DisplayName("evaluate fires When Symbol Exceeds Threshold")
  @Test
  void evaluate_firesWhenSymbolExceedsThreshold() {
    when(investment.symbolExposures())
        .thenReturn(
            List.of(
                exposure("AAPL.US", 1000.0),
                exposure("MSFT.US", 100.0),
                exposure("SPY.US", 100.0)));

    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
    // AAPL is ~83% of total -> must trigger.
    assertTrue(result.get().contains("AAPL.US"));
  }

  @DisplayName("evaluate tracks each symbol as its own observation")
  @Test
  void evaluateTracksEachSymbolSeparately() {
    when(investment.symbolExposures())
        .thenReturn(
            List.of(exposure("NVDA", 600.0), exposure("TSLA", 300.0), exposure("MSFT", 100.0)));

    assertThat(rule.evaluateObservations())
        .extracting(AlertObservation::key)
        .containsExactly("NVDA", "TSLA");
  }

  @DisplayName("evaluate is Quiet For Balanced Portfolio")
  @Test
  void evaluate_isQuietForBalancedPortfolio() {
    when(investment.symbolExposures())
        .thenReturn(
            List.of(
                exposure("AAPL.US", 100.0),
                exposure("MSFT.US", 100.0),
                exposure("SPY.US", 100.0),
                exposure("TSLA.US", 100.0),
                exposure("BTC", 100.0)));

    assertFalse(rule.evaluate().isPresent());
  }

  @DisplayName("evaluate is Safe When Portfolio Is Empty")
  @Test
  void evaluate_isSafeWhenPortfolioIsEmpty() {
    when(investment.symbolExposures()).thenReturn(List.of());

    assertFalse(rule.evaluate().isPresent());
  }

  private static SymbolExposure exposure(String symbol, double value) {
    return new SymbolExposure(symbol, BigDecimal.valueOf(value), "USD");
  }
}
