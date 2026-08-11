package com.example.demo.services.openai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
public class OpenAiChatService {

  private static final String RESPONSES_PATH = "/v1/responses";
  private static final String DISABLED_MESSAGE =
      "AI replies are not configured. Set OPENAI_API_KEY and enable OPENAI_ENABLED.";
  private static final String FAILURE_MESSAGE =
      "The AI service is temporarily unavailable. Please try again later.";

  private final RestClient restClient;
  private final boolean enabled;
  private final String apiKey;
  private final String model;
  private final String instructions;
  private final int maxOutputTokens;
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
      PortfolioContextService portfolioContextService) {
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    this.enabled = enabled;
    this.apiKey = apiKey;
    this.model = model;
    this.maxOutputTokens = maxOutputTokens;
    this.instructions = instructions;
    this.portfolioContextService = portfolioContextService;
  }

  public String reply(String chatId, String userMessage) {
    if (!enabled || apiKey == null || apiKey.isBlank()) {
      return DISABLED_MESSAGE;
    }
    if (userMessage == null || userMessage.isBlank()) {
      return "Please send a text question.";
    }

    String normalizedMessage = userMessage.trim();
    String input =
        buildInput(normalizedMessage, portfolioContextService.loadIfRelevant(normalizedMessage));

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("model", model);
    request.put("instructions", instructions);
    request.put("input", input);
    request.put("max_output_tokens", maxOutputTokens);
    request.put("store", true);

    String previousResponseId = previousResponseIds.get(chatId);
    if (previousResponseId != null) {
      request.put("previous_response_id", previousResponseId);
    }

    try {
      JsonNode response =
          restClient
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
