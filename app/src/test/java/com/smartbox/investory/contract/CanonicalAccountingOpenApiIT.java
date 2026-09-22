package com.smartbox.investory.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test-fast")
@TestPropertySource(
    properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=false"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CanonicalAccountingOpenApiIT extends FastDatabaseTest {
  @Autowired private MockMvc mvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void canonicalAccountingRoutesAndEnumsRemainPublished() throws Exception {
    JsonNode document =
        objectMapper.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode paths = document.path("paths");
    Stream.of(
            "/api/profiles/{profileId}/accounting/periods",
            "/api/profiles/{profileId}/accounting/periods/{month}",
            "/api/profiles/{profileId}/accounting/periods/{month}/invoices",
            "/api/profiles/{profileId}/accounting/invoices",
            "/api/profiles/{profileId}/accounting/invoices/recognize",
            "/api/profiles/{profileId}/accounting/invoices/candidates/{candidateKey}",
            "/api/profiles/{profileId}/accounting/payments",
            "/api/profiles/{profileId}/accounting/invoices/{invoiceId}/manual-paid",
            "/api/profiles/{profileId}/accounting/counterparties",
            "/api/profiles/{profileId}/accounting/periods/{month}/freeze",
            "/api/v1/auth/login",
            "/api/v1/auth/me")
        .forEach(path -> assertThat(paths.has(path)).as("OpenAPI route %s", path).isTrue());

    JsonNode schemas = document.path("components").path("schemas");
    assertThat(propertyEnumValuesContaining(schemas, "status", "OPEN", "FROZEN"))
        .containsExactlyInAnyOrder("OPEN", "FROZEN");
    assertThat(
            propertyEnumValuesContaining(
                schemas,
                "paymentStatus",
                "MATCHED",
                "PARTIALLY_MATCHED",
                "UNMATCHED",
                "MANUALLY_CONFIRMED",
                "NOT_REQUIRED"))
        .containsExactlyInAnyOrder(
            "MATCHED", "PARTIALLY_MATCHED", "UNMATCHED", "MANUALLY_CONFIRMED", "NOT_REQUIRED");
    assertThat(
            schemas
                .path("InvoiceResponse")
                .path("properties")
                .path("netAmount")
                .path("type")
                .asText())
        .isEqualTo("string");
  }

  private static java.util.List<String> propertyEnumValuesContaining(
      JsonNode schemas, String property, String... expected) {
    var expectedSet = java.util.Set.of(expected);
    var match =
        StreamSupport.stream(schemas.spliterator(), false)
            .map(schema -> schema.path("properties").path(property).path("enum"))
            .filter(JsonNode::isArray)
            .map(
                enumNode ->
                    StreamSupport.stream(enumNode.spliterator(), false)
                        .map(JsonNode::asText)
                        .toList())
            .filter(values -> values.containsAll(expectedSet))
            .findFirst();
    return match.orElseGet(java.util.List::of);
  }
}
