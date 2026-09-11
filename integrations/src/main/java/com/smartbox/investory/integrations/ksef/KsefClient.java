package com.smartbox.investory.integrations.ksef;

import com.smartbox.investory.shared.time.ApplicationTime;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/** Minimal KSeF 2.0 transport isolated from Investory accounting/domain code. */
@Component
public class KsefClient {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final String KSEF_TOKEN_ENCRYPTION = "KsefTokenEncryption";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final ApplicationTime applicationTime;

  @Autowired
  public KsefClient(ObjectMapper objectMapper, ApplicationTime applicationTime) {
    this(
        HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
        objectMapper.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(),
        applicationTime);
  }

  KsefClient(HttpClient httpClient, ObjectMapper objectMapper, ApplicationTime applicationTime) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.applicationTime = applicationTime;
  }

  public KsefAccess authenticateWithToken(KsefEnvironment environment, String nip, String ksefToken) {
    requireNip(nip);
    if (ksefToken == null || ksefToken.isBlank()) {
      throw new IllegalArgumentException("KSeF token is required");
    }

    String baseUrl = environment.baseUrl();
    AuthenticationChallenge challenge =
        readJson(post(baseUrl + "/auth/challenge", null, null), AuthenticationChallenge.class);
    PublicKeyCertificate publicKey = currentTokenEncryptionKey(baseUrl);
    String encryptedToken =
        Base64.getEncoder()
            .encodeToString(
                encryptToken(
                    publicKey(publicKey.certificate()), ksefToken, challenge.timestamp().toInstant()));

    Map<String, Object> body =
        Map.of(
            "challenge", challenge.challenge(),
            "contextIdentifier", Map.of("type", "Nip", "value", nip),
            "encryptedToken", encryptedToken,
            "publicKeyId", publicKey.publicKeyId());
    SignatureResponse init =
        readJson(
            post(baseUrl + "/auth/ksef-token", writeJson(body), null), SignatureResponse.class);

    if (init.authenticationToken() == null || init.authenticationToken().token() == null) {
      throw new KsefException("KSeF authentication did not return a temporary token");
    }

    String temporaryToken = init.authenticationToken().token();
    waitUntilAuthenticated(baseUrl, init.referenceNumber(), temporaryToken);
    AuthTokens tokens =
        readJson(
            post(baseUrl + "/auth/token/redeem", null, temporaryToken), AuthTokens.class);
    if (tokens.accessToken() == null || tokens.accessToken().token() == null) {
      throw new KsefException("KSeF authentication did not return an access token");
    }
    return new KsefAccess(
        tokens.accessToken().token(),
        tokens.accessToken().validUntil(),
        tokens.refreshToken() == null ? null : tokens.refreshToken().token(),
        tokens.refreshToken() == null ? null : tokens.refreshToken().validUntil());
  }

  /** Query incoming invoices (Subject2/buyer) by KSeF invoicing date. Returns the API JSON unchanged. */
  public String queryIncomingInvoices(
      KsefEnvironment environment,
      String accessToken,
      OffsetDateTime from,
      OffsetDateTime to,
      int pageOffset,
      int pageSize) {
    if (from == null || to == null || from.isAfter(to)) {
      throw new IllegalArgumentException("Invalid KSeF invoice date range");
    }
    if (pageOffset < 0 || pageSize < 1 || pageSize > 250) {
      throw new IllegalArgumentException("Invalid KSeF paging");
    }
    Map<String, Object> body =
        Map.of(
            "subjectType", "Subject2",
            "dateRange",
                Map.of("dateType", "Invoicing", "from", from.toString(), "to", to.toString()));
    String url =
        environment.baseUrl()
            + "/invoices/query/metadata?pageOffset="
            + pageOffset
            + "&pageSize="
            + pageSize;
    return post(url, writeJson(body), accessToken);
  }

  /** Download the canonical structured invoice (FA(3) XML) by KSeF number. */
  public String downloadInvoice(KsefEnvironment environment, String accessToken, String ksefNumber) {
    if (ksefNumber == null || ksefNumber.isBlank()) {
      throw new IllegalArgumentException("KSeF number is required");
    }
    return get(environment.baseUrl() + "/invoices/ksef/" + ksefNumber, accessToken, "application/xml");
  }

  PublicKeyCertificate currentTokenEncryptionKey(String baseUrl) {
    PublicKeyCertificate[] certificates =
        readJson(
            get(baseUrl + "/security/public-key-certificates", null, "application/json"),
            PublicKeyCertificate[].class);
    OffsetDateTime now = OffsetDateTime.ofInstant(applicationTime.now(), ZoneOffset.UTC);
    return Arrays.stream(certificates)
        .filter(c -> c.usage() != null && c.usage().contains(KSEF_TOKEN_ENCRYPTION))
        .filter(c -> c.validFrom() == null || !now.isBefore(c.validFrom()))
        .filter(c -> c.validTo() == null || now.isBefore(c.validTo()))
        .filter(c -> c.publicKeyId() != null && !c.publicKeyId().isBlank())
        .findFirst()
        .orElseThrow(() -> new KsefException("No active KSeF token-encryption public key"));
  }

  static byte[] encryptToken(PublicKey publicKey, String token, Instant challengeTimestamp) {
    try {
      byte[] content =
          (token + "|" + challengeTimestamp.toEpochMilli()).getBytes(StandardCharsets.UTF_8);
      OAEPParameterSpec oaep =
          new OAEPParameterSpec(
              "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
      Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaep);
      return cipher.doFinal(content);
    } catch (Exception e) {
      throw new KsefException("Failed to encrypt KSeF token", e);
    }
  }

  static PublicKey publicKey(String pem) {
    try {
      String cleaned =
          pem.replace("-----BEGIN CERTIFICATE-----", "")
              .replace("-----END CERTIFICATE-----", "")
              .replaceAll("\\s", "");
      byte[] bytes = Base64.getDecoder().decode(cleaned);
      X509Certificate certificate =
          (X509Certificate)
              CertificateFactory.getInstance("X.509")
                  .generateCertificate(new ByteArrayInputStream(bytes));
      return certificate.getPublicKey();
    } catch (Exception e) {
      throw new KsefException("Failed to parse KSeF public certificate", e);
    }
  }

  private void waitUntilAuthenticated(String baseUrl, String referenceNumber, String temporaryToken) {
    for (int attempt = 0; attempt < 30; attempt++) {
      AuthenticationStatus status =
          readJson(
              get(baseUrl + "/auth/" + referenceNumber, temporaryToken, "application/json"),
              AuthenticationStatus.class);
      int code = status.status() == null ? 0 : status.status().code();
      if (code == 200) return;
      if (code >= 400) throw new KsefException("KSeF authentication failed with status " + code);
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new KsefException("Interrupted while authenticating with KSeF", e);
      }
    }
    throw new KsefException("KSeF authentication did not complete");
  }

  private String get(String url, String bearerToken, String accept) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Accept", accept == null ? "application/json" : accept)
            .GET();
    bearer(builder, bearerToken);
    return send(builder.build());
  }

  private String post(String url, String body, String bearerToken) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).header("Accept", "application/json");
    if (body == null) {
      builder.POST(HttpRequest.BodyPublishers.noBody());
    } else {
      builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    }
    bearer(builder, bearerToken);
    return send(builder.build());
  }

  private void bearer(HttpRequest.Builder builder, String token) {
    if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
  }

  private String send(HttpRequest request) {
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new KsefException("KSeF returned HTTP " + response.statusCode());
      }
      return response.body();
    } catch (KsefException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new KsefException("Interrupted while calling KSeF", e);
    } catch (Exception e) {
      throw new KsefException("Failed to call KSeF", e);
    }
  }

  private <T> T readJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (Exception e) {
      throw new KsefException("Failed to parse KSeF response", e);
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new KsefException("Failed to create KSeF request", e);
    }
  }

  private void requireNip(String nip) {
    if (nip == null || !nip.matches("\\d{10}")) {
      throw new IllegalArgumentException("NIP must contain exactly 10 digits");
    }
  }

  public record KsefAccess(
      String accessToken,
      OffsetDateTime accessTokenValidUntil,
      String refreshToken,
      OffsetDateTime refreshTokenValidUntil) {}

  record AuthenticationChallenge(String challenge, OffsetDateTime timestamp) {}

  record TokenInfo(String token, OffsetDateTime validUntil) {}

  record SignatureResponse(String referenceNumber, TokenInfo authenticationToken) {}

  record AuthTokens(TokenInfo accessToken, TokenInfo refreshToken) {}

  record StatusInfo(int code, String description, List<String> details) {}

  record AuthenticationStatus(StatusInfo status) {}

  record PublicKeyCertificate(
      String certificate,
      String certificateId,
      String publicKeyId,
      OffsetDateTime validFrom,
      OffsetDateTime validTo,
      List<String> usage) {}

  public static class KsefException extends RuntimeException {
    public KsefException(String message) {
      super(message);
    }

    public KsefException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
