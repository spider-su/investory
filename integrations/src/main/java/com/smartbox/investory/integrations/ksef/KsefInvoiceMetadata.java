package com.smartbox.investory.integrations.ksef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Transport-neutral extraction of KSeF document numbers from invoice metadata pages. */
public final class KsefInvoiceMetadata {
  private static final ObjectMapper JSON = new ObjectMapper();

  private KsefInvoiceMetadata() {}

  public static List<String> extractKsefNumbers(String metadataJson) {
    if (metadataJson == null || metadataJson.isBlank()) return List.of();
    try {
      JsonNode root = JSON.readTree(metadataJson);
      List<String> numbers = new ArrayList<>();
      for (JsonNode invoice : root.path("invoices")) {
        String number = invoice.path("ksefNumber").asText("").trim();
        if (!number.isBlank()) numbers.add(number);
      }
      return numbers;
    } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("KSeF returned invalid invoice metadata", exception);
    }
  }
}
