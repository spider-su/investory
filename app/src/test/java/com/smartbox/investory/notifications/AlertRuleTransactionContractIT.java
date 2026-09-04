package com.smartbox.investory.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.integrations.notifications.application.AlertRule;
import com.smartbox.investory.integrations.notifications.application.AlertRuleTransactionRunner;
import com.smartbox.investory.integrations.notifications.persistence.AlertRuleStateRepository;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AlertRuleTransactionContractIT extends FastDatabaseTest {

  @Autowired private AlertRuleTransactionRunner runner;
  @Autowired private AlertRuleStateRepository states;
  @MockitoBean private NotificationEventPublisher publisher;

  private String goodCode;

  @AfterEach
  void cleanUp() {
    if (goodCode != null) {
      states.deleteById(goodCode);
    }
  }

  @Test
  void transactionMarkingPersistenceFailureDoesNotRollBackAnUnrelatedRule() {
    String brokenCode = "B".repeat(65);
    goodCode = "ISOLATED_" + UUID.randomUUID();
    AlertRule brokenRule =
        new AlertRule() {
          @Override
          public String code() {
            return brokenCode;
          }

          @Override
          public Optional<String> evaluate() {
            return Optional.of("broken persistence row");
          }
        };
    AlertRule goodRule =
        new AlertRule() {
          @Override
          public String code() {
            return goodCode;
          }

          @Override
          public Optional<String> evaluate() {
            return Optional.of("independent rule");
          }
        };

    assertThatThrownBy(() -> runner.run(brokenRule)).isInstanceOf(DataAccessException.class);

    runner.run(goodRule);

    assertThat(states.findAll())
        .anyMatch(state -> state.isActive() && state.getRuleCode().equals(goodCode));
  }
}
