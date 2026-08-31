package com.smartbox.investory.integrations.ai.openai;

import com.smartbox.investory.integrations.management.api.model.*;
import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.model.*;
import com.smartbox.investory.integrations.management.spi.TestableIntegrationPlugin;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiIntegrationPlugin implements TestableIntegrationPlugin {
  public static final String ID = "openai";
  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

  public String id() {
    return ID;
  }

  public IntegrationType type() {
    return IntegrationType.AI;
  }

  public PluginDescriptor descriptor() {
    return new PluginDescriptor(
        ID,
        "OpenAI",
        type(),
        List.of(
            PluginFieldDescriptor.requiredSecret("apiKey"),
            new PluginFieldDescriptor(
                "baseUrl",
                PluginFieldType.URL,
                false,
                "https://api.openai.com",
                List.of(),
                "API URL",
                "OpenAI-compatible API base URL",
                null,
                null,
                null),
            new PluginFieldDescriptor(
                "model",
                PluginFieldType.STRING,
                false,
                "gpt-5-mini",
                List.of(),
                "Model",
                null,
                null,
                null,
                null),
            new PluginFieldDescriptor(
                "maxOutputTokens",
                PluginFieldType.INTEGER,
                false,
                "1200",
                List.of(),
                "Max output tokens",
                null,
                1,
                100000,
                null),
            new PluginFieldDescriptor(
                "instructions",
                PluginFieldType.STRING,
                false,
                "",
                List.of(),
                "Instructions",
                null,
                null,
                null,
                null)),
        List.of());
  }

  public ValidationResult validate(PluginConfig config) {
    return config.value("apiKey").isPresent()
        ? ValidationResult.success()
        : ValidationResult.invalid("Missing required configuration: apiKey");
  }

  @Override
  public ConnectionTestResult testConnection(PluginConfig config) {
    try {
      RestClient.builder()
          .requestFactory(requestFactory())
          .baseUrl(config.value("baseUrl").orElse("https://api.openai.com"))
          .build()
          .get()
          .uri("/v1/models")
          .header("Authorization", "Bearer " + config.value("apiKey").orElseThrow())
          .retrieve()
          .toBodilessEntity();
      return new ConnectionTestResult(true, true, "OpenAI connection succeeded");
    } catch (RuntimeException exception) {
      return new ConnectionTestResult(true, false, "OpenAI connection failed");
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
