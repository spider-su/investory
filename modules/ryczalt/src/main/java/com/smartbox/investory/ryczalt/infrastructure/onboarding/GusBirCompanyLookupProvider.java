package com.smartbox.investory.ryczalt.infrastructure.onboarding;

import com.smartbox.investory.ryczalt.application.onboarding.CompanyLookupProvider;
import com.smartbox.investory.ryczalt.application.onboarding.CompanyLookupResult;
import com.smartbox.investory.ryczalt.application.onboarding.NipValidator;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Narrow GUS BIR SOAP adapter. It is inactive until a server-side BIR key is configured. */
@Component
@EnableConfigurationProperties(GusBirCompanyLookupProvider.Properties.class)
public class GusBirCompanyLookupProvider implements CompanyLookupProvider {
  private final Properties properties;
  private final RestClient client;

  public GusBirCompanyLookupProvider(Properties properties) {
    this.properties = properties;
    var requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(8));
    this.client =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
  }

  @Override
  public CompanyLookupResult lookup(String rawNip) {
    String nip = NipValidator.normalize(rawNip);
    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
      return unavailable(nip, "gus_api_key_not_configured");
    }
    try {
      String sid =
          call(
              "Zaloguj",
              "<pKluczUzytkownika>" + xml(properties.apiKey()) + "</pKluczUzytkownika>",
              null);
      String body = "<pParametryWyszukiwania><Nip>" + nip + "</Nip></pParametryWyszukiwania>";
      String result = call("DaneSzukajPodmioty", body, sid);
      String companyName = field(result, "Nazwa");
      if (companyName == null) {
        return new CompanyLookupResult(
            nip,
            null,
            null,
            null,
            null,
            "GUS_BIR",
            Instant.now(),
            null,
            null,
            null,
            List.of("GUS_BIR"),
            List.of("company_not_found"),
            "NOT_FOUND");
      }
      String type = field(result, "TypPodmiotu");
      return new CompanyLookupResult(
          nip,
          companyName,
          field(result, "Regon"),
          null,
          address(result),
          "GUS_BIR",
          Instant.now(),
          type == null ? null : type.toLowerCase().contains("fizycz") ? "JDG" : "OTHER",
          "ACTIVE",
          parseDate(field(result, "DataRozpoczeciaDzialalnosci")),
          List.of("GUS_BIR"),
          List.of(),
          "PARTIAL");
    } catch (RuntimeException exception) {
      return unavailable(nip, "gus_provider_unavailable");
    }
  }

  private String call(String action, String body, String sid) {
    String envelope =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:bir=\"http://CIS/BIR/PUBL/2014/07\">"
            + "<soap:Header/> <soap:Body><bir:"
            + action
            + ">"
            + body
            + "</bir:"
            + action
            + "></soap:Body></soap:Envelope>";
    String response =
        client
            .post()
            .header("Content-Type", "application/soap+xml; charset=utf-8")
            .header("sid", sid == null ? "" : sid)
            .body(envelope)
            .retrieve()
            .body(String.class);
    String value = field(response, action + "Result");
    return value == null ? response : value;
  }

  private static CompanyLookupResult unavailable(String nip, String warning) {
    return new CompanyLookupResult(
        nip,
        null,
        null,
        null,
        null,
        "GUS_BIR",
        Instant.now(),
        null,
        null,
        null,
        List.of("GUS_BIR"),
        List.of(warning),
        "UNAVAILABLE");
  }

  private static String address(String xml) {
    String street = field(xml, "Ulica");
    String number = field(xml, "NrNieruchomosci");
    String apartment = field(xml, "NrLokalu");
    String postal = field(xml, "KodPocztowy");
    String city = field(xml, "Miejscowosc");
    return join(street, number, apartment, postal, city);
  }

  private static String join(String... values) {
    return java.util.Arrays.stream(values)
        .filter(v -> v != null && !v.isBlank())
        .reduce((a, b) -> a + ", " + b)
        .orElse(null);
  }

  private static String field(String xml, String name) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setNamespaceAware(true);
      var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      NodeList nodes = document.getElementsByTagNameNS("*", name);
      if (nodes.getLength() == 0) nodes = document.getElementsByTagName(name);
      if (nodes.getLength() == 0) return null;
      String value = nodes.item(0).getTextContent();
      return value == null || value.isBlank() ? null : value.trim();
    } catch (Exception exception) {
      return null;
    }
  }

  private static LocalDate parseDate(String value) {
    try {
      return value == null ? null : LocalDate.parse(value);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static String xml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  @ConfigurationProperties("app.ryczalt.company-lookup.gus")
  public record Properties(String baseUrl, String apiKey) {
    public Properties {
      baseUrl =
          baseUrl == null || baseUrl.isBlank()
              ? "https://wyszukiwarkaregont.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc"
              : baseUrl;
    }
  }
}
