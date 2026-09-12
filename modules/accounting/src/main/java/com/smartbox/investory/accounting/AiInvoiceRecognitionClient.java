package com.smartbox.investory.accounting;

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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
class AiInvoiceRecognitionClient {
  private static final long MAX_BYTES = 12L * 1024L * 1024L;
  private static final String RESPONSES_PATH = "/v1/responses";
  private static final String FILES_PATH = "/v1/files";

  private final IntegrationConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final PluginConfig environmentFallback;

  AiInvoiceRecognitionClient(
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

  AccountingInvoiceRecognitionService.RecognizedInvoice recognize(
      String filename, String contentType, byte[] bytes) {
    validateDocument(filename, contentType, bytes);
    PluginConfig config =
        configurationService.resolveForRuntime(
            IntegrationType.AI, OpenAiIntegrationPlugin.ID, environmentFallback);
    String apiKey = config.value("apiKey").orElse("");
    if (apiKey.isBlank()) {
      throw new IllegalStateException(
          "OpenAI integration is not enabled. Configure it before invoice recognition.");
    }

    String baseUrl = config.value("baseUrl").orElse("https://api.openai.com");
    RestClient client = RestClient.builder().baseUrl(baseUrl).build();
    String mime = normalizedContentType(filename, contentType);
    String uploadedFileId = null;

    try {
      List<Map<String, Object>> content = new ArrayList<>();
      content.add(Map.of("type", "input_text", "text", extractionPrompt()));

      if ("application/pdf".equals(mime)) {
        uploadedFileId = uploadPdf(client, apiKey, filename, bytes);
        content.add(Map.of("type", "input_file", "file_id", uploadedFileId));
      } else {
        String data = Base64.getEncoder().encodeToString(bytes);
        content.add(
            Map.of(
                "type", "input_image",
                "image_url", "data:" + mime + ";base64," + data,
                "detail", "high"));
      }

      Map<String, Object> request = new LinkedHashMap<>();
      request.put("model", config.value("model").orElse("gpt-5-mini"));
      request.put("input", List.of(Map.of("role", "user", "content", content)));
      request.put("max_output_tokens", 1400);
      request.put("store", false);

      JsonNode response =
          client
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
    } finally {
      if (uploadedFileId != null) {
        deleteUploadedFile(client, apiKey, uploadedFileId);
      }
    }
  }

  private String uploadPdf(RestClient client, String apiKey, String filename, byte[] bytes) {
    MultipartBodyBuilder body = new MultipartBodyBuilder();
    body.part("purpose", "user_data");
    body.part(
            "file",
            new ByteArrayResource(bytes) {
              @Override
              public String getFilename() {
                return safeFilename(filename, "invoice.pdf");
              }
            })
        .contentType(MediaType.APPLICATION_PDF);

    JsonNode uploaded =
        client
            .post()
            .uri(FILES_PATH)
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body.build())
            .retrieve()
            .body(JsonNode.class);

    String fileId = uploaded == null ? null : uploaded.path("id").asString(null);
    if (fileId == null || fileId.isBlank()) {
      throw new IllegalStateException("OpenAI file upload returned no file id");
    }
    return fileId;
  }

  private void deleteUploadedFile(RestClient client, String apiKey, String fileId) {
    try {
      client
          .delete()
          .uri(FILES_PATH + "/{fileId}", fileId)
          .header("Authorization", "Bearer " + apiKey)
          .retrieve()
          .toBodilessEntity();
    } catch (RuntimeException exception) {
      log.warn("Could not delete temporary OpenAI invoice file {}", fileId, exception);
    }
  }

  private AccountingInvoiceRecognitionService.RecognizedInvoice parseRecognizedInvoice(
      String output) {
    try {
      String json = stripCodeFence(output.trim());
      JsonNode node = objectMapper.readTree(json);
      LocalDate issueDate = localDate(node, "issueDate");
      if (issueDate == null) issueDate = localDate(node, "invoiceDate");
      return new AccountingInvoiceRecognitionService.RecognizedInvoice(
          normalizedDocumentType(text(node, "documentType")),
          issueDate,
          localDate(node, "saleDate"),
          localDate(node, "dueDate"),
          text(node, "reference"),
          text(node, "seller"),
          text(node, "buyer"),
          normalizedCategory(text(node, "category")),
          defaultText(text(node, "currency"), "PLN").toUpperCase(Locale.ROOT),
          decimal(node, "netAmount"),
          decimal(node, "vatAmount"),
          decimal(node, "grossAmount"),
          text(node, "note"),
          text(node, "sellerNip"),
          text(node, "buyerNip"),
          List.of(
              new AccountingInvoiceRecognitionService.FieldCandidate<>(
                  output,
                  AccountingInvoiceRecognitionService.ExtractionSource.AI,
                  "AI JSON response")));
    } catch (Exception exception) {
      log.warn("Could not parse invoice recognition payload: {}", abbreviate(output, 500));
      throw new IllegalStateException("AI result could not be parsed as an invoice", exception);
    }
  }

  private String extractionPrompt() {
    return """
        Read this invoice or receipt and return ONLY one JSON object, without markdown.
        Do not invent missing values. Use null when a value is not visible or cannot be established.
        Preserve monetary values exactly, but return decimal strings WITHOUT thousands separators,
        for example 7636.00 rather than 7,636.00 or 7 636,00.

        Extract seller/issuer and buyer/customer separately. Do not assume that the first company name
        is the supplier. Determine documentType from the document roles when possible; use UNKNOWN if
        direction cannot be established confidently. SALES_INVOICE means an outgoing/customer invoice;
        PURCHASE_INVOICE means a supplier/expense invoice. A correction/credit document is CREDIT_NOTE.

        JSON fields:
        {
          "documentType": "SALES_INVOICE | PURCHASE_INVOICE | CREDIT_NOTE | RECEIPT | UNKNOWN",
          "issueDate": "yyyy-MM-dd or null",
          "saleDate": "yyyy-MM-dd or null",
          "dueDate": "yyyy-MM-dd or null",
          "reference": "invoice/document number or null",
          "seller": "seller/issuer name or null",
          "sellerNip": "seller NIP or null",
          "buyer": "buyer/customer name or null",
          "buyerNip": "buyer NIP or null",
          "category": "VEHICLE_FUEL | ACCOUNTING_SERVICE | BUSINESS_SERVICE | EQUIPMENT | OTHER",
          "currency": "ISO currency code, usually PLN",
          "netAmount": "decimal string or null",
          "vatAmount": "decimal string or null",
          "grossAmount": "decimal string or null",
          "note": "short extraction note, including uncertainty, service description or VAT-rate details"
        }

        Category guidance: fuel station fuel -> VEHICLE_FUEL; bookkeeping/accounting -> ACCOUNTING_SERVICE;
        general software/consulting/business service -> BUSINESS_SERVICE; computer/electronics/equipment -> EQUIPMENT;
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

  private String normalizedDocumentType(String value) {
    if (value == null) return "UNKNOWN";
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "SALES_INVOICE", "PURCHASE_INVOICE", "CREDIT_NOTE", "RECEIPT" ->
          value.trim().toUpperCase(Locale.ROOT);
      default -> "UNKNOWN";
    };
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
    if (value == null) return null;
    String normalized = value.replace(" ", "").replace("\u00A0", "");
    int comma = normalized.lastIndexOf(',');
    int dot = normalized.lastIndexOf('.');
    if (comma >= 0 && dot >= 0) {
      if (comma > dot) {
        normalized = normalized.replace(".", "").replace(',', '.');
      } else {
        normalized = normalized.replace(",", "");
      }
    } else if (comma >= 0) {
      normalized = normalized.replace(',', '.');
    }
    return new BigDecimal(normalized);
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
}
