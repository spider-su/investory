package com.smartbox.investory.integrations.notifications.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.notifications.persistence.AlertRuleStateEntity;
import com.smartbox.investory.integrations.notifications.persistence.AlertRuleStateRepository;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertRuleTransactionRunnerTest {

  @Test
  void loadsOnlyStatesBelongingToTheRuleFromRepository() {
    NotificationEventPublisher publisher = mock(NotificationEventPublisher.class);
    AlertRuleStateRepository states = mock(AlertRuleStateRepository.class);
    AlertRuleTransactionRunner runner =
        new AlertRuleTransactionRunner(
            publisher, states, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    AlertRuleStateEntity state = new AlertRuleStateEntity();
    state.setRuleCode("DRAWDOWN:portfolio-1");
    state.setActive(true);
    when(states.findAllByRuleCodeOrRuleCodeStartingWith("DRAWDOWN", "DRAWDOWN:"))
        .thenReturn(List.of(state));

    runner.run(new TestRule());

    verify(states).findAllByRuleCodeOrRuleCodeStartingWith("DRAWDOWN", "DRAWDOWN:");
    verify(states).save(state);
  }

  private static final class TestRule implements AlertRule {
    @Override
    public String code() {
      return "DRAWDOWN";
    }

    @Override
    public java.util.Optional<String> evaluate() {
      return java.util.Optional.empty();
    }
  }
}
