package com.smartbox.investory.accounting;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Read-only SOAP client for the ZUS EWD PobierzPotwierdzenie operation. */
public final class RealZusDraConfirmationClient {
  private static final String SOAP = "http://www.w3.org/2003/05/soap-envelope";
  private static final String EWD = "naws.zus.pl";

  private final URI endpoint;
  private final HttpClient httpClient;
  private final String producer;
  private final String software;
  private final String version;

  public RealZusDraConfirmationClient(
      URI endpoint,
      Path pkcs12Keystore,
      char[] keystorePassword,
      char[] keyPassword,
      String producer,
      String software,
      String version) {
    this(
        endpoint,
        createHttpClient(pkcs12Keystore, keystorePassword, keyPassword),
        producer,
        software,
        version);
  }

  /** Creates a read-only client trusting the supplied ZUS PEM certificate. */
  public RealZusDraConfirmationClient(
      URI endpoint,
      Path zusServerCertificatePem,
      String producer,
      String software,
      String version) {
    this(
        endpoint,
        createHttpClientWithTrustedCertificate(zusServerCertificatePem),
        producer,
        software,
        version);
  }

  RealZusDraConfirmationClient(
      URI endpoint, HttpClient httpClient, String producer, String software, String version) {
    this.endpoint = endpoint;
    this.httpClient = httpClient;
    this.producer = producer;
    this.software = software;
    this.version = version;
  }

  public ZusDraConfirmation fetch(String submissionId) {
    if (submissionId == null || submissionId.isBlank())
      throw new IllegalArgumentException("submissionId must not be blank");
    byte[] requestBody = request(submissionId);
    try {
      HttpResponse<byte[]> response =
          httpClient.send(
              HttpRequest.newBuilder(endpoint)
                  .header("Content-Type", "application/soap+xml; charset=utf-8")
                  .header("Accept", "application/soap+xml")
                  .header("Action", "naws.zus.pl/PobierzPotwierdzenie")
                  .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                  .build(),
              HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() / 100 != 2)
        throw new IllegalStateException("ZUS_EWD_HTTP_" + response.statusCode());
      return parse(submissionId, response.body());
    } catch (IOException | InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ZUS_EWD_CONFIRMATION_READ_FAILED", exception);
    }
  }

  private byte[] request(String submissionId) {
    String messageId = "urn:uuid:" + UUID.randomUUID();
    String to = escape(endpoint.toString());
    String id = escape(submissionId);
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
    return ("""
        <?xml version="1.0" encoding="UTF-8"?>
        <s:Envelope xmlns:s="%s" xmlns:a="http://www.w3.org/2005/08/addressing" xmlns="%s">
          <s:Header>
            <a:Action s:mustUnderstand="1">naws.zus.pl/PobierzPotwierdzenie</a:Action>
            <a:MessageID>%s</a:MessageID>
            <a:ReplyTo><a:Address>http://www.w3.org/2005/08/addressing/anonymous</a:Address></a:ReplyTo>
            <a:To s:mustUnderstand="1">%s</a:To>
          </s:Header>
          <s:Body>
            <PobierzPotwierdzenie>
              <strIdentyfikator>%s</strIdentyfikator>
              <strNazwaProducenta>%s</strNazwaProducenta>
              <strNazwaOprogramowania>%s</strNazwaOprogramowania>
              <strWersjaOprogramowania>%s</strWersjaOprogramowania>
              <DataWpisu>%s</DataWpisu>
              <uiWielkoscPrzesylki>0</uiWielkoscPrzesylki>
            </PobierzPotwierdzenie>
          </s:Body>
        </s:Envelope>
        """
            .formatted(
                SOAP,
                EWD,
                messageId,
                to,
                id,
                escape(producer),
                escape(software),
                escape(version),
                now))
        .getBytes(StandardCharsets.UTF_8);
  }

  private ZusDraConfirmation parse(String submissionId, byte[] soap) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      var document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(soap));
      Element fault = first(document.getDocumentElement(), "Fault");
      if (fault != null)
        throw new IllegalStateException("ZUS_EWD_SOAP_FAULT: " + fault.getTextContent());
      Element response = first(document.getDocumentElement(), "PobierzPotwierdzenieResponse");
      if (response == null)
        throw new IllegalStateException("ZUS_EWD_CONFIRMATION_RESPONSE_MISSING");
      String payloadText = value(response, "byPrzesylka");
      byte[] payload =
          payloadText == null || payloadText.isBlank()
              ? new byte[0]
              : Base64.getDecoder().decode(payloadText);
      String written = value(response, "DataWpisu");
      Instant writtenAt = written == null || written.isBlank() ? null : Instant.parse(written);
      return new ZusDraConfirmation(
          submissionId,
          value(response, "strIdZadania"),
          value(response, "strTyp"),
          writtenAt,
          payload,
          sha256(payload),
          soap,
          Instant.now());
    } catch (Exception exception) {
      if (exception instanceof IllegalStateException state) throw state;
      throw new IllegalStateException("ZUS_EWD_CONFIRMATION_PARSE_FAILED", exception);
    }
  }

  private static Element first(Element parent, String localName) {
    var nodes = parent.getElementsByTagNameNS("*", localName);
    return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
  }

  private static String value(Element parent, String localName) {
    Element element = first(parent, localName);
    return element == null ? null : element.getTextContent();
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static HttpClient createHttpClient(Path file, char[] storePassword, char[] keyPassword) {
    try {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      try (var input = Files.newInputStream(file)) {
        keyStore.load(input, storePassword);
      }
      var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keys.init(keyStore, keyPassword);
      SSLContext ssl = SSLContext.getInstance("TLS");
      ssl.init(keys.getKeyManagers(), null, null);
      return HttpClient.newBuilder().sslContext(ssl).build();
    } catch (Exception exception) {
      throw new IllegalStateException("ZUS_EWD_KEYSTORE_INITIALIZATION_FAILED", exception);
    }
  }

  private static HttpClient createHttpClientWithTrustedCertificate(Path certificateFile) {
    try (var input = Files.newInputStream(certificateFile)) {
      var certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);
      trustStore.setCertificateEntry("zus-ewd", certificate);
      var trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trusts.init(trustStore);
      SSLContext ssl = SSLContext.getInstance("TLS");
      ssl.init(null, trusts.getTrustManagers(), null);
      return HttpClient.newBuilder().sslContext(ssl).build();
    } catch (Exception exception) {
      throw new IllegalStateException("ZUS_EWD_CERTIFICATE_INITIALIZATION_FAILED", exception);
    }
  }
}
