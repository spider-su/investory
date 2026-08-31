package com.smartbox.investory.integrations.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.notifications.infrastructure.DrawdownAlertStateEntity;
import com.smartbox.investory.integrations.notifications.infrastructure.DrawdownAlertStateRepository;
import com.smartbox.investory.investment.accounting.PortfolioService;
import com.smartbox.investory.investment.accounting.model.Portfolio;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DrawdownAlertRuleTest {

  @Mock private PortfolioService portfolioService;
  @Mock private DrawdownAlertStateRepository stateRepository;

  private NotificationProperties properties;
  private DrawdownAlertRule rule;
  private DrawdownAlertStateEntity state;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.setDrawdownThresholdPct(10.0);
    properties.setDrawdownCooldownHours(24);
    state = new DrawdownAlertStateEntity();
    lenient()
        .when(stateRepository.findById(DrawdownAlertStateEntity.SINGLETON_ID))
        .thenReturn(Optional.of(state));
    rule =
        new DrawdownAlertRule(
            portfolioService,
            properties,
            stateRepository,
            Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void code_isStable() {
    org.junit.jupiter.api.Assertions.assertEquals("DRAWDOWN", rule.code());
  }

  @Test
  void evaluate_isQuietBeforePeakIsEstablished() {
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolioWithBalance(1000.0));

    Optional<String> result = rule.evaluate();

    assertFalse(result.isPresent());
  }

  @Test
  void evaluate_firesWhenBalanceDropsBelowThreshold() {
    when(portfolioService.calculateTotalProfitLoss())
        .thenReturn(portfolioWithBalance(1000.0)) // peak
        .thenReturn(portfolioWithBalance(850.0)); // -15%

    rule.evaluate(); // records peak
    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
    assertTrue(result.get().contains("15"));
  }

  @Test
  void evaluate_doesNotFireForSmallDrop() {
    when(portfolioService.calculateTotalProfitLoss())
        .thenReturn(portfolioWithBalance(1000.0))
        .thenReturn(portfolioWithBalance(950.0)); // -5%

    rule.evaluate();
    Optional<String> result = rule.evaluate();

    assertFalse(result.isPresent());
  }

  @Test
  void evaluate_updatesPeakUpward() {
    when(portfolioService.calculateTotalProfitLoss())
        .thenReturn(portfolioWithBalance(1000.0))
        .thenReturn(portfolioWithBalance(1200.0))
        .thenReturn(portfolioWithBalance(1080.0)); // -10% from new peak

    rule.evaluate();
    rule.evaluate(); // raises peak to 1200
    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
  }

  @Test
  void evaluate_usesPersistedPeakAfterRuleIsRecreated() {
    state.setPeakEquity(1000.0);
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolioWithBalance(850.0));

    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
  }

  @Test
  void evaluate_suppressesRepeatedAlertUntilCooldownExpires() {
    state.setPeakEquity(1000.0);
    state.setLastAlertAt(java.time.ZonedDateTime.parse("2026-08-13T13:00:00Z"));
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolioWithBalance(850.0));

    assertFalse(rule.evaluate().isPresent());

    state.setLastAlertAt(java.time.ZonedDateTime.parse("2026-08-13T12:00:00Z"));
    assertTrue(rule.evaluate().isPresent());
  }

  private static Portfolio portfolioWithBalance(double balance) {
    Portfolio p = new Portfolio();
    p.setBaseCurrency(CurrencyType.USD);
    p.setBalance(balance);
    return p;
  }
}
