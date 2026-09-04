package com.smartbox.investory.integrations.notifications.application;

import static org.mockito.Mockito.*;

import com.smartbox.investory.integrations.notifications.persistence.AlertRuleStateEntity;
import com.smartbox.investory.integrations.notifications.persistence.AlertRuleStateRepository;
import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader;
import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader.PortfolioOperationsSnapshot;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
  @Mock private PortfolioOperationsReader investment;
  @Mock private NotificationEventPublisher publisher;
  @Mock private AlertRuleStateRepository alertStates;
  @Mock private AlertRule firingRule;
  @Mock private AlertRule throwingRule;
  private NotificationProperties properties;
  private NotificationService service;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.setEnabled(true);
    service =
        new NotificationService(
            investment,
            List.of(),
            properties,
            publisher,
            new AlertRuleTransactionRunner(publisher, alertStates, fixedClock()),
            fixedClock());
  }

  @Test
  void disabledNotificationsDoNotReadOrPublish() {
    properties.setEnabled(false);
    service.sendDailyDigest();
    verifyNoInteractions(investment, publisher);
  }

  @Test
  void dailyDigestUsesStablePeriodFingerprintAndOutbox() {
    when(investment.portfolio()).thenReturn(portfolio(12345, 678, 100, 578, 50, 12.5));
    service.sendDailyDigest();
    ArgumentCaptor<NotificationCandidate> candidate =
        ArgumentCaptor.forClass(NotificationCandidate.class);
    verify(publisher).publish(candidate.capture());
    org.assertj.core.api.Assertions.assertThat(candidate.getValue().fingerprint())
        .isEqualTo("DAILY_DIGEST:2026-08-25");
    org.assertj.core.api.Assertions.assertThat(candidate.getValue().payload().get("message"))
        .contains("12,345", "USD");
  }

  @Test
  void alertPublishesCandidateAndContinuesAfterRuleFailure() {
    when(firingRule.code()).thenReturn("FIRING");
    when(firingRule.evaluateObservations())
        .thenReturn(List.of(new AlertObservation("", "warning text")));
    when(throwingRule.code()).thenReturn("BROKEN");
    when(throwingRule.evaluateObservations()).thenThrow(new RuntimeException("rule broke"));
    service =
        new NotificationService(
            investment,
            List.of(firingRule, throwingRule),
            properties,
            publisher,
            new AlertRuleTransactionRunner(publisher, alertStates, fixedClock()),
            fixedClock());
    service.runAlerts();
    verify(publisher)
        .publish(argThat(candidate -> candidate.fingerprint().startsWith("ALERT:FIRING:")));
  }

  @Test
  void alertFingerprintFollowsIncidentTransitionsNotRenderedMessage() {
    AlertRuleStateEntity state = new AlertRuleStateEntity();
    state.setRuleCode("CONCENTRATION");
    when(alertStates.findById("CONCENTRATION")).thenReturn(Optional.of(state));
    when(alertStates.findAllByRuleCodeOrRuleCodeStartingWith("CONCENTRATION", "CONCENTRATION:"))
        .thenReturn(List.of(state));
    when(firingRule.code()).thenReturn("CONCENTRATION");
    when(firingRule.evaluateObservations())
        .thenReturn(List.of(new AlertObservation("", "NVDA 31.0%")))
        .thenReturn(List.of(new AlertObservation("", "NVDA 31.3%")))
        .thenReturn(List.of())
        .thenReturn(List.of(new AlertObservation("", "NVDA 31.0%")));
    service =
        new NotificationService(
            investment,
            List.of(firingRule),
            properties,
            publisher,
            new AlertRuleTransactionRunner(publisher, alertStates, fixedClock()),
            fixedClock());
    service.runAlerts();
    service.runAlerts();
    service.runAlerts();
    service.runAlerts();

    ArgumentCaptor<NotificationCandidate> candidates =
        ArgumentCaptor.forClass(NotificationCandidate.class);
    verify(publisher, times(2)).publish(candidates.capture());
    org.assertj.core.api.Assertions.assertThat(candidates.getAllValues())
        .extracting(NotificationCandidate::fingerprint)
        .containsExactly("ALERT:CONCENTRATION:1:ACTIVE", "ALERT:CONCENTRATION:2:ACTIVE");
  }

  @Test
  void concentrationIncidentsTransitionIndependentlyPerSymbol() {
    Map<String, AlertRuleStateEntity> states = new HashMap<>();
    when(alertStates.findById(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(states.get(invocation.getArgument(0))));
    when(alertStates.findAllByRuleCodeOrRuleCodeStartingWith(anyString(), anyString()))
        .thenAnswer(invocation -> List.copyOf(states.values()));
    doAnswer(
            invocation -> {
              AlertRuleStateEntity state = invocation.getArgument(0);
              states.put(state.getRuleCode(), state);
              return state;
            })
        .when(alertStates)
        .save(any(AlertRuleStateEntity.class));

    when(firingRule.code()).thenReturn("CONCENTRATION");
    when(firingRule.evaluateObservations())
        .thenReturn(
            List.of(new AlertObservation("NVDA", "NVDA 31.0%")),
            List.of(
                new AlertObservation("NVDA", "NVDA 31.2%"),
                new AlertObservation("TSLA", "TSLA 26.0%")),
            List.of(new AlertObservation("TSLA", "TSLA 27.0%")));
    service =
        new NotificationService(
            investment,
            List.of(firingRule),
            properties,
            publisher,
            new AlertRuleTransactionRunner(publisher, alertStates, fixedClock()),
            fixedClock());

    service.runAlerts();
    service.runAlerts();
    service.runAlerts();

    ArgumentCaptor<NotificationCandidate> candidates =
        ArgumentCaptor.forClass(NotificationCandidate.class);
    verify(publisher, times(2)).publish(candidates.capture());
    org.assertj.core.api.Assertions.assertThat(candidates.getAllValues())
        .extracting(NotificationCandidate::fingerprint)
        .containsExactly("ALERT:CONCENTRATION:NVDA:1:ACTIVE", "ALERT:CONCENTRATION:TSLA:1:ACTIVE");
    org.assertj.core.api.Assertions.assertThat(states.get("CONCENTRATION:NVDA").isActive())
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(states.get("CONCENTRATION:TSLA").isActive())
        .isTrue();
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC);
  }

  private static PortfolioOperationsSnapshot portfolio(
      double balance,
      double total,
      double unrealized,
      double realized,
      double dividends,
      double tax) {
    return new PortfolioOperationsSnapshot(
        "USD",
        BigDecimal.valueOf(balance),
        BigDecimal.valueOf(total),
        BigDecimal.valueOf(unrealized),
        BigDecimal.valueOf(realized),
        BigDecimal.valueOf(dividends),
        BigDecimal.valueOf(tax));
  }
}
