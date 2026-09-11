package com.smartbox.investory.poc.accounting;

import com.smartbox.investory.integrations.ai.openai.OpenAiIntegrationPlugin;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class AccountingInvoiceRecognitionService {
  private static final long MAX_BYTES = 12L * 1024L * 1024L;
  private static final String RESPONSES_PATH = "/v1/responses";

  private final IntegrationConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final PluginConfig environmentFallback;

  public AccountingInvoiceRecognitionService(
      IntegrationConfigurationService configurationService,
      ObjectMapper objectMapper,
      @Value("${app.openai.base-url:https://api.openai.com}") String baseUrl,
      @Value("${app.openai.enabled:false}") boolean enabled,
      @Value("${app.openai.api-key:}") String apiKey,
      @Value("${app.openai.model:gpt-5-mini}") String model) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    Map<String, String> fallback = new LinkedHashMap<>();
    if (enabled && apiKey != null && !apiKey.isBlank()) {
      fallback.put("apiKey", apiKey);
      fallback.put("baseUrl", baseUrl);
      fallback.put("model", model);
    }
    this.environmentFallback = new PluginConfig(fallback);
  }

  public RecognizedInvoice recognize(String filename, String contentType, byte[] bytes) {
    validateDocument(filename, contentType, bytes);
    PluginConfig config =
        configurationService.resolveForRuntime(
            IntegrationType.AI, OpenAiIntegrationPlugin.ID, environmentFallback);
    String apiKey = config.value("apiKey").orElse("");
    if (apiKey.isBlank()) {
      throw new IllegalStateException(
          "OpenAI integration is not enabled. Configure it before invoice recognition.");
    }

    List<Map<String, Object>> content = new ArrayList<>();
    content.add(Map.of("type", "input_text", "text", extractionPrompt()));
    String mime = normalizedContentType(filename, contentType);
    String data = Base64.getEncoder().encodeToString(bytes);
    if ("application/pdf".equals(mime)) {
      Map<String, Object> file = new LinkedHashMap<>();
      file.put("type", "input_file");
      file.put("filename", safeFilename(filename, "invoice.pdf"));
      file.put("file_data", "data:application/pdf;base64," + data);
      content.add(file);
    } else {
      content.add(
          Map.of(
              "type", "input_image",
              "image_url", "data:" + mime + ";base64," + data,
              "detail", "high"));
    }

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("model", config.value("model").orElse("gpt-5-mini"));
    request.put("input", List.of(Map.of("role", "user", "content", content)));
    request.put("max_output_tokens", 1200);
    request.put("store", false);

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
      throw new IllegalStateException("Invoice recognition returned an empty response");
    }
    String output = extractOutputText(response);
    if (output.isBlank()) {
      throw new IllegalStateException("Invoice recognition returned no structured result");
    }
    return parseRecognizedInvoice(output);
  }

  private RecognizedInvoice parseRecognizedInvoice(String output) {
    try {
      String json = stripCodeFence(output.trim());
      JsonNode node = objectMapper.readTree(json);
      return new RecognizedInvoice(
          localDate(node, "invoiceDate"),
          text(node, "reference"),
          text(node, "supplier"),
          normalizedCategory(text(node, "category")),
          defaultText(text(node, "currency"), "PLN").toUpperCase(Locale.ROOT),
          decimal(node, "netAmount"),
          decimal(node, "vatAmount"),
          decimal(node, "grossAmount"),
          text(node, "note"));
    } catch (Exception exception) {
      log.warn("Could not parse invoice recognition payload: {}", abbreviate(output, 500));
      throw new IllegalStateException("AI result could not be parsed as an invoice", exception);
    }
  }

  private String extractionPrompt() {
    return """
        Read this supplier invoice or receipt and return ONLY one JSON object, without markdown.
        Do not invent missing values. Use null when a value is not visible or cannot be established.
        Preserve decimal amounts exactly as printed.

        JSON fields:
        {
          "invoiceDate": "yyyy-MM-dd or null",
          "reference": "invoice/document number or null",
          "supplier": "supplier name or null",
          "category": "VEHICLE_FUEL | ACCOUNTING_SERVICE | BUSINESS_SERVICE | EQUIPMENT | OTHER",
          "currency": "ISO currency code, usually PLN",
          "netAmount": "decimal string or null",
          "vatAmount": "decimal string or null",
          "grossAmount": "decimal string or null",
          "note": "short extraction note, including uncertainty or multiple VAT rates"
        }

        Category guidance: fuel station fuel -> VEHICLE_FUEL; bookkeeping/accounting -> ACCOUNTING_SERVICE;
        general business service -> BUSINESS_SERVICE; computer/electronics/equipment -> EQUIPMENT;
        anything else -> OTHER. Extraction is pre-accounting assistance only; do not infer tax deductibility.
        """;
  }

  private void validateDocument(String filename, String contentType, byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("Invoice file is empty");
    }
    if (bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("Invoice file is too large; maximum size is 12 MB");
    }
    normalizedContentType(filename, contentType);
  }

  private String normalizedContentType(String filename, String contentType) {
    String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    if (type.equals("application/pdf")
        || type.equals("image/jpeg")
        || type.equals("image/png")
        || type.equals("image/webp")) {
      return type;
    }
    String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".webp")) return "image/webp";
    throw new IllegalArgumentException("Supported invoice formats are PDF, JPG, PNG and WEBP");
  }

  private String extractOutputText(JsonNode response) {
    StringBuilder text = new StringBuilder();
    for (JsonNode output : response.path("output")) {
      for (JsonNode content : output.path("content")) {
        if ("output_text".equals(content.path("type").asString())) {
          String part = content.path("text").asString("");
          if (!part.isBlank()) {
            if (!text.isEmpty()) text.append('\n');
            text.append(part);
          }
        }
      }
    }
    return text.toString();
  }

  private String stripCodeFence(String value) {
    if (!value.startsWith("```")) return value;
    int firstNewline = value.indexOf('\n');
    int lastFence = value.lastIndexOf("```");
    return firstNewline >= 0 && lastFence > firstNewline
        ? value.substring(firstNewline + 1, lastFence).trim()
        : value;
  }

  private String normalizedCategory(String value) {
    if (value == null) return "OTHER";
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "VEHICLE_FUEL", "ACCOUNTING_SERVICE", "BUSINESS_SERVICE", "EQUIPMENT" ->
          value.trim().toUpperCase(Locale.ROOT);
      default -> "OTHER";
    };
  }

  private LocalDate localDate(JsonNode node, String field) {
    String value = text(node, field);
    return value == null ? null : LocalDate.parse(value);
  }

  private BigDecimal decimal(JsonNode node, String field) {
    String value = text(node, field);
    return value == null ? null : new BigDecimal(value.replace(',', '.'));
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) return null;
    String text = value.asString("").trim();
    return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
  }

  private String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String safeFilename(String filename, String fallback) {
    if (filename == null || filename.isBlank()) return fallback;
    return filename.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private String abbreviate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max) + "...";
  }

  public record RecognizedInvoice(
      LocalDate invoiceDate,
      String reference,
      String supplier,
      String category,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      String note) {}
}
