package com.smartbox.investory.integrations.ai.openai;

import com.smartbox.investory.integrations.ai.AiChat;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.portfolio.PortfolioContextService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
public class OpenAiChatService implements AiChat {

  private static final String RESPONSES_PATH = "/v1/responses";
  private static final String DISABLED_MESSAGE =
      "AI replies are not configured. Configure and enable the OpenAI integration.";
  private static final String FAILURE_MESSAGE =
      "The AI service is temporarily unavailable. Please try again later.";

  private final IntegrationConfigurationService configurationService;
  private final String fallbackBaseUrl;
  private final boolean fallbackEnabled;
  private final String fallbackApiKey;
  private final String fallbackModel;
  private final String fallbackInstructions;
  private final int fallbackMaxOutputTokens;
  private final PortfolioContextService portfolioContextService;
  private final Map<String, String> previousResponseIds = new ConcurrentHashMap<>();

  public OpenAiChatService(
      @Value("${app.openai.base-url:https://api.openai.com}") String baseUrl,
      @Value("${app.openai.enabled:false}") boolean enabled,
      @Value("${app.openai.api-key:}") String apiKey,
      @Value("${app.openai.model:gpt-5-mini}") String model,
      @Value("${app.openai.max-output-tokens:1200}") int maxOutputTokens,
      @Value(
              "${app.openai.instructions:You are the Investory assistant. Give concise, factual answers about investing and portfolio management. Do not claim access to portfolio data unless it was included in the user's message. State uncertainty clearly. Do not provide personalized financial guarantees.}")
          String instructions,
      PortfolioContextService portfolioContextService,
      IntegrationConfigurationService configurationService) {
    this.fallbackBaseUrl = baseUrl;
    this.fallbackEnabled = enabled;
    this.fallbackApiKey = apiKey;
    this.fallbackModel = model;
    this.fallbackInstructions = instructions;
    this.fallbackMaxOutputTokens = maxOutputTokens;
    this.portfolioContextService = portfolioContextService;
    this.configurationService = configurationService;
  }

  public String reply(String chatId, String userMessage) {
    PluginConfig config = runtimeConfiguration();
    String apiKey = config.value("apiKey").orElse("");
    if (apiKey.isBlank()) {
      return DISABLED_MESSAGE;
    }
    if (userMessage == null || userMessage.isBlank()) {
      return "Please send a text question.";
    }

    String normalizedMessage = userMessage.trim();
    String input =
        buildInput(normalizedMessage, portfolioContextService.loadIfRelevant(normalizedMessage));

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("model", config.value("model").orElse("gpt-5-mini"));
    request.put("instructions", config.value("instructions").orElse(""));
    request.put("input", input);
    request.put(
        "max_output_tokens", config.value("maxOutputTokens").map(Integer::parseInt).orElse(1200));
    request.put("store", true);

    String previousResponseId = previousResponseIds.get(chatId);
    if (previousResponseId != null) {
      request.put("previous_response_id", previousResponseId);
    }

    try {
      JsonNode response =
          RestClient.builder()
              .baseUrl(config.value("baseUrl").orElse("https://api.openai.com"))
              .build()
              .post()
              .uri(RESPONSES_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .header("Authorization", "Bearer " + apiKey)
              .body(request)
              .retrieve()
              .body(JsonNode.class);

      if (response == null) {
        log.warn("OpenAI returned an empty response body");
        return FAILURE_MESSAGE;
      }

      String responseId = response.path("id").asString(null);
      if (responseId != null && !responseId.isBlank()) {
        previousResponseIds.put(chatId, responseId);
      }

      String outputText = extractOutputText(response);
      if (outputText == null || outputText.isBlank()) {
        log.warn("OpenAI response {} contained no output text", responseId);
        return FAILURE_MESSAGE;
      }
      return outputText.trim();
    } catch (HttpClientErrorException.TooManyRequests ex) {
      String responseBody = ex.getResponseBodyAsString();

      if (responseBody.contains("\"code\":\"insufficient_quota\"")
          || responseBody.contains("\"code\": \"insufficient_quota\"")) {
        log.warn("OpenAI API quota is unavailable: {}", responseBody);
        return "The AI service has no available API credit. Check OpenAI billing.";
      }

      log.warn("OpenAI API rate limit reached: {}", responseBody);
      return "The AI service is temporarily rate-limited. Please try again shortly.";
    } catch (RestClientResponseException e) {
      log.warn(
          "OpenAI request failed with status {}: {}",
          e.getStatusCode(),
          abbreviate(e.getResponseBodyAsString(), 500));
      return FAILURE_MESSAGE;
    } catch (RuntimeException e) {
      log.warn("OpenAI request failed", e);
      return FAILURE_MESSAGE;
    }
  }

  private PluginConfig runtimeConfiguration() {
    Map<String, String> fallback = new LinkedHashMap<>();
    if (fallbackEnabled && fallbackApiKey != null && !fallbackApiKey.isBlank()) {
      fallback.put("apiKey", fallbackApiKey);
      fallback.put("baseUrl", fallbackBaseUrl);
      fallback.put("model", fallbackModel);
      fallback.put("instructions", fallbackInstructions);
      fallback.put("maxOutputTokens", Integer.toString(fallbackMaxOutputTokens));
    }
    return configurationService.resolveForRuntime(
        IntegrationType.AI, OpenAiIntegrationPlugin.ID, new PluginConfig(fallback));
  }

  public void resetConversation(String chatId) {
    if (chatId != null) {
      previousResponseIds.remove(chatId);
    }
  }

  static String buildInput(String userMessage, String portfolioContext) {
    if (portfolioContext == null || portfolioContext.isBlank()) {
      return userMessage;
    }

    return """
                User question:
                %s

                Current Investory portfolio context:
                %s

                Use the portfolio context above as the source of truth for account-specific facts.
                Do not invent values that are absent from the context.
                If the context does not contain enough information, state exactly what is missing.
                """
        .formatted(userMessage, portfolioContext);
  }

  static String extractOutputText(JsonNode response) {
    StringBuilder text = new StringBuilder();
    for (JsonNode output : response.path("output")) {
      for (JsonNode content : output.path("content")) {
        if ("output_text".equals(content.path("type").asString())) {
          String part = content.path("text").asString("");
          if (!part.isBlank()) {
            if (!text.isEmpty()) {
              text.append('\n');
            }
            text.append(part);
          }
        }
      }
    }
    return text.toString();
  }

  private static String abbreviate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...";
  }
}
