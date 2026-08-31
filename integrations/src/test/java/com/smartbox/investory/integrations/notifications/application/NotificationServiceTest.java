package com.smartbox.investory.integrations.notifications.application;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader;
import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader.PortfolioOperationsSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification Service")
class NotificationServiceTest {

  @Mock private ObjectProvider<NotificationDeliveryChannel> channelProvider;
  @Mock private NotificationDeliveryChannel channel;
  @Mock private PortfolioOperationsReader investment;
  @Mock private AlertRule firingRule;
  @Mock private AlertRule silentRule;
  @Mock private AlertRule throwingRule;

  private NotificationProperties properties;
  private NotificationService service;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.setEnabled(true);
  }

  @DisplayName("send Daily Digest skips When Notifications Disabled")
  @Test
  void sendDailyDigest_skipsWhenNotificationsDisabled() {
    properties.setEnabled(false);
    service = new NotificationService(channelProvider, investment, List.of(), properties);

    service.sendDailyDigest();

    verifyNoInteractions(investment, channelProvider, channel);
  }

  @DisplayName("send Daily Digest builds And Sends Message When Channel Available")
  @Test
  void sendDailyDigest_buildsAndSendsMessageWhenChannelAvailable() {
    PortfolioOperationsSnapshot portfolio = portfolio(12345, 678, 100, 578, 50, 12.5);
    when(investment.portfolio()).thenReturn(portfolio);
    when(channelProvider.orderedStream()).thenReturn(java.util.stream.Stream.of(channel));

    service = new NotificationService(channelProvider, investment, List.of(), properties);
    service.sendDailyDigest();

    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    verify(channel).send(messageCaptor.capture());
    String message = messageCaptor.getValue();
    org.junit.jupiter.api.Assertions.assertTrue(message.contains("Daily digest"));
    org.junit.jupiter.api.Assertions.assertTrue(message.contains("12,345"));
    org.junit.jupiter.api.Assertions.assertTrue(message.contains("USD"));
  }

  @DisplayName("send Daily Digest logs When Channel Unavailable")
  @Test
  void sendDailyDigest_logsWhenChannelUnavailable() {
    PortfolioOperationsSnapshot portfolio = portfolio(0, 0, 0, 0, 0, 0);
    when(investment.portfolio()).thenReturn(portfolio);
    when(channelProvider.orderedStream()).thenReturn(java.util.stream.Stream.empty());

    service = new NotificationService(channelProvider, investment, List.of(), properties);
    // Just assert it does not throw when no delivery channel is enabled.
    service.sendDailyDigest();
  }

  @DisplayName("send Daily Digest swallows Exceptions")
  @Test
  void sendDailyDigest_swallowsExceptions() {
    when(investment.portfolio()).thenThrow(new RuntimeException("boom"));
    service = new NotificationService(channelProvider, investment, List.of(), properties);

    // Must not propagate; scheduler keeps running.
    service.sendDailyDigest();
    verify(channel, never()).send(anyString());
  }

  @DisplayName("run Alerts sends Only Fired Rules And Continues After Rule Failure")
  @Test
  void runAlerts_sendsOnlyFiredRulesAndContinuesAfterRuleFailure() {
    // firingRule and throwingRule both need a code() — it's logged. silentRule never fires
    // so its code() is unused; stub it lenient to avoid strict-stubbing complaints.
    org.mockito.Mockito.lenient().when(firingRule.code()).thenReturn("FIRING");
    when(firingRule.evaluate()).thenReturn(Optional.of("warning text"));
    when(silentRule.evaluate()).thenReturn(Optional.empty());
    when(throwingRule.code()).thenReturn("BROKEN");
    when(throwingRule.evaluate()).thenThrow(new RuntimeException("rule broke"));
    when(channelProvider.orderedStream()).thenReturn(java.util.stream.Stream.of(channel));

    service =
        new NotificationService(
            channelProvider, investment, List.of(firingRule, silentRule, throwingRule), properties);
    service.runAlerts();

    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    verify(channel, times(1)).send(messageCaptor.capture());
    org.junit.jupiter.api.Assertions.assertTrue(messageCaptor.getValue().contains("warning text"));
  }

  @DisplayName("run Alerts skips When Disabled")
  @Test
  void runAlerts_skipsWhenDisabled() {
    properties.setEnabled(false);
    service = new NotificationService(channelProvider, investment, List.of(firingRule), properties);

    service.runAlerts();

    verifyNoInteractions(firingRule);
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
