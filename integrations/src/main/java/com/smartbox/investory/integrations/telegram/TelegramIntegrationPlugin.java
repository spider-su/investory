package com.smartbox.investory.integrations.telegram;

import com.smartbox.investory.integrations.management.api.model.*;
import com.smartbox.investory.integrations.management.model.*;
import com.smartbox.investory.integrations.management.spi.TestableIntegrationPlugin;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TelegramIntegrationPlugin implements TestableIntegrationPlugin {
  public static final String ID = "telegram";
  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

  public String id() {
    return ID;
  }

  public IntegrationType type() {
    return IntegrationType.NOTIFICATION;
  }

  public PluginDescriptor descriptor() {
    return new PluginDescriptor(
        ID,
        "Telegram",
        type(),
        List.of(
            PluginFieldDescriptor.requiredSecret("botToken"),
            new PluginFieldDescriptor(
                "botUsername",
                PluginFieldType.STRING,
                true,
                null,
                List.of(),
                "Bot username",
                null,
                null,
                null,
                null),
            new PluginFieldDescriptor(
                "chatId",
                PluginFieldType.STRING,
                true,
                null,
                List.of(),
                "Chat ID",
                null,
                null,
                null,
                null)),
        List.of());
  }

  public ValidationResult validate(PluginConfig config) {
    return config.value("botToken").isPresent()
            && config.value("botUsername").isPresent()
            && config.value("chatId").isPresent()
        ? ValidationResult.success()
        : ValidationResult.invalid("Telegram requires bot token, username, and chat ID");
  }

  @Override
  public ConnectionTestResult testConnection(PluginConfig config) {
    try {
      RestClient.builder()
          .requestFactory(requestFactory())
          .baseUrl("https://api.telegram.org")
          .build()
          .get()
          .uri("/bot" + config.value("botToken").orElseThrow() + "/getMe")
          .retrieve()
          .toBodilessEntity();
      return new ConnectionTestResult(true, true, "Telegram connection succeeded");
    } catch (RuntimeException exception) {
      return new ConnectionTestResult(true, false, "Telegram connection failed");
    }
  }

  private static JdkClientHttpRequestFactory requestFactory() {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(TEST_TIMEOUT).build());
    factory.setReadTimeout(TEST_TIMEOUT);
    return factory;
  }
}
