package com.smartbox.investory.ryczalt.infrastructure.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.ryczalt.application.onboarding.CompanyLookupProvider;
import com.smartbox.investory.ryczalt.application.onboarding.CompanyLookupResult;
import com.smartbox.investory.ryczalt.application.onboarding.NipValidator;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
@EnableConfigurationProperties(VatWhiteListCompanyLookupClient.Properties.class)
public class VatWhiteListCompanyLookupClient implements CompanyLookupProvider {
  private final RestClient client;
  private final ObjectMapper mapper;

  public VatWhiteListCompanyLookupClient(
      Properties properties, ObjectProvider<ObjectMapper> mapper) {
    var requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(8));
    this.client =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    this.mapper = mapper.getIfAvailable(ObjectMapper::new);
  }

  @Override
  public CompanyLookupResult lookup(String rawNip) {
    String nip = NipValidator.normalize(rawNip);
    try {
      String body =
          client
              .get()
              .uri("/api/search/nip/{nip}?date={date}", nip, LocalDate.now())
              .retrieve()
              .onStatus(HttpStatusCode::isError, (request, response) -> {})
              .body(String.class);
      JsonNode root = mapper.readTree(body);
      JsonNode subject = root.path("result").path("subject");
      if (subject.isMissingNode() || subject.isNull()) {
        JsonNode subjects = root.path("result").path("subjects");
        if (subjects.isArray() && !subjects.isEmpty()) subject = subjects.get(0);
      }
      if (subject.isMissingNode() || subject.isNull() || subject.path("nip").asText().isBlank())
        return new CompanyLookupResult(
            nip,
            null,
            null,
            "NOT_FOUND",
            null,
            "VAT_WHITE_LIST",
            Instant.now(),
            null,
            null,
            null,
            java.util.List.of(),
            java.util.List.of("company_not_found"),
            "NOT_FOUND");
      String address =
          firstNonBlank(
              subject.path("workingAddress").asText(), subject.path("residenceAddress").asText());
      return new CompanyLookupResult(
          subject.path("nip").asText(nip),
          subject.path("name").asText(null),
          subject.path("regon").asText(null),
          subject.path("statusVat").asText(null),
          address,
          "VAT_WHITE_LIST",
          Instant.now(),
          null,
          null,
          null,
          java.util.List.of("VAT_WHITE_LIST"),
          java.util.List.of(),
          "PARTIAL");
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "company_lookup_unavailable", e);
    }
  }

  private static String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  @ConfigurationProperties("app.ryczalt.company-lookup.vat-white-list")
  public record Properties(String baseUrl) {}
}
