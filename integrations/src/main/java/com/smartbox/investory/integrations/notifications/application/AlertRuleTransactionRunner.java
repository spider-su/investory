package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.AlertRuleStateEntity;
import com.smartbox.investory.integrations.notifications.persistence.AlertRuleStateRepository;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs one alert rule and its state transition in an isolated transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleTransactionRunner {

  private final NotificationEventPublisher publisher;
  private final AlertRuleStateRepository alertStates;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void run(AlertRule rule) {
    String code = rule.code();
    Set<String> activeKeys = new HashSet<>();
    for (AlertObservation observation : rule.evaluateObservations()) {
      String stateKey = stateKey(code, observation.key());
      activeKeys.add(stateKey);
      evaluateActive(stateKey, code, observation.message());
    }
    alertStates.findAllByRuleCodeOrRuleCodeStartingWith(code, code + ":").stream()
        .filter(state -> !activeKeys.contains(state.getRuleCode()))
        .filter(AlertRuleStateEntity::isActive)
        .forEach(this::evaluateNormal);
  }

  private void evaluateActive(String stateKey, String code, String message) {
    AlertRuleStateEntity state = alertStates.findById(stateKey).orElseGet(() -> newState(stateKey));
    if (!state.isActive()) {
      state.setActive(true);
      state.setIncidentSequence(state.getIncidentSequence() + 1);
      alertStates.save(state);
      log.info("Alert transitioned NORMAL -> ACTIVE: {}", stateKey);
      publishAlert(stateKey, code, state.getIncidentSequence(), message);
    }
  }

  private void evaluateNormal(AlertRuleStateEntity state) {
    state.setActive(false);
    alertStates.save(state);
    log.info("Alert transitioned ACTIVE -> NORMAL: {}", state.getRuleCode());
  }

  private static AlertRuleStateEntity newState(String code) {
    AlertRuleStateEntity state = new AlertRuleStateEntity();
    state.setRuleCode(code);
    return state;
  }

  private void publishAlert(String stateKey, String code, long incidentSequence, String message) {
    publisher.publish(
        new NotificationCandidate(
            NotificationEventType.THRESHOLD_ALERT,
            NotificationSeverity.WARNING,
            null,
            "ALERT_RULE",
            stateKey,
            "ALERT:" + stateKey + ":" + incidentSequence + ":ACTIVE",
            "Threshold alert: " + code,
            Map.of("message", message, "rule", code, "incidentKey", stateKey),
            clock.instant()));
  }

  private static String stateKey(String code, String observationKey) {
    return observationKey == null || observationKey.isBlank() ? code : code + ":" + observationKey;
  }
}
