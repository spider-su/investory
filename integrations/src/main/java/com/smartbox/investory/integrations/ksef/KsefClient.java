package com.smartbox.investory.integrations.ksef;

import com.smartbox.investory.shared.time.ApplicationTime;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;
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
import javax.crypto.KeyGenerator;
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

  public KsefAccess authenticateWithToken(
      KsefEnvironment environment, String nip, String ksefToken) {
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
                    publicKey(publicKey.certificate()),
                    ksefToken,
                    challenge.timestamp().toInstant()));

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
        readJson(post(baseUrl + "/auth/token/redeem", null, temporaryToken), AuthTokens.class);
    if (tokens.accessToken() == null || tokens.accessToken().token() == null) {
      throw new KsefException("KSeF authentication did not return an access token");
    }
    return new KsefAccess(
        tokens.accessToken().token(),
        tokens.accessToken().validUntil(),
        tokens.refreshToken() == null ? null : tokens.refreshToken().token(),
        tokens.refreshToken() == null ? null : tokens.refreshToken().validUntil());
  }

  /**
   * Query incoming invoices (Subject2/buyer) by KSeF invoicing date. Returns the API JSON
   * unchanged.
   */
  public String queryIncomingInvoices(
      KsefEnvironment environment,
      String accessToken,
      OffsetDateTime from,
      OffsetDateTime to,
      int pageOffset,
      int pageSize) {
    return queryInvoices(environment, accessToken, "Subject2", from, to, pageOffset, pageSize);
  }

  public String queryInvoices(
      KsefEnvironment environment,
      String accessToken,
      String subjectType,
      OffsetDateTime from,
      OffsetDateTime to,
      int pageOffset,
      int pageSize) {
    if (from == null || to == null || from.isAfter(to)) {
      throw new IllegalArgumentException("Invalid KSeF invoice date range");
    }
    if (!List.of("Subject1", "Subject2", "Subject3").contains(subjectType)) {
      throw new IllegalArgumentException("Invalid KSeF subject type");
    }
    if (pageOffset < 0 || pageSize < 1 || pageSize > 250) {
      throw new IllegalArgumentException("Invalid KSeF paging");
    }
    Map<String, Object> body =
        Map.of(
            "subjectType",
            subjectType,
            "dateRange",
            Map.of("dateType", "Issue", "from", from.toString(), "to", to.toString()));
    String url =
        environment.baseUrl()
            + "/invoices/query/metadata?pageOffset="
            + pageOffset
            + "&pageSize="
            + pageSize;
    return post(url, writeJson(body), accessToken);
  }

  /** Download the canonical structured invoice (FA(3) XML) by KSeF number. */
  public String downloadInvoice(
      KsefEnvironment environment, String accessToken, String ksefNumber) {
    if (ksefNumber == null || ksefNumber.isBlank()) {
      throw new IllegalArgumentException("KSeF number is required");
    }
    return get(
        environment.baseUrl() + "/invoices/ksef/" + ksefNumber, accessToken, "application/xml");
  }

  /** Return the current status of an online or batch session as the API JSON response. */
  public String getSessionStatus(
      KsefEnvironment environment, String accessToken, String sessionReferenceNumber) {
    requireReference(sessionReferenceNumber, "KSeF session reference number");
    return get(
        environment.baseUrl() + "/sessions/" + sessionReferenceNumber,
        accessToken,
        "application/json");
  }

  /** List historical online or batch sessions, returning the paginated API JSON unchanged. */
  public String listSessions(
      KsefEnvironment environment,
      String accessToken,
      String sessionType,
      int pageSize,
      String continuationToken) {
    if (!List.of("Online", "Batch").contains(sessionType)) {
      throw new IllegalArgumentException("Invalid KSeF session type");
    }
    if (pageSize < 1 || pageSize > 1000) {
      throw new IllegalArgumentException("Invalid KSeF session page size");
    }
    String url =
        environment.baseUrl() + "/sessions?sessionType=" + sessionType + "&pageSize=" + pageSize;
    if (continuationToken != null && !continuationToken.isBlank()) {
      url += "&continuationToken=" + continuationToken;
    }
    return get(url, accessToken, "application/json");
  }

  /** Download the signed UPO for an accepted invoice sent in a KSeF session. */
  public byte[] downloadSessionInvoiceUpoByKsefNumber(
      KsefEnvironment environment,
      String accessToken,
      String sessionReferenceNumber,
      String ksefNumber) {
    requireReference(sessionReferenceNumber, "KSeF session reference number");
    if (ksefNumber == null || ksefNumber.isBlank()) {
      throw new IllegalArgumentException("KSeF number is required");
    }
    return getBytes(
        environment.baseUrl()
            + "/sessions/"
            + sessionReferenceNumber
            + "/invoices/ksef/"
            + ksefNumber
            + "/upo",
        accessToken,
        "application/xml");
  }

  /** Download a collective UPO using the reference returned by the closed session status. */
  public byte[] downloadSessionUpo(
      KsefEnvironment environment,
      String accessToken,
      String sessionReferenceNumber,
      String upoReferenceNumber) {
    requireReference(sessionReferenceNumber, "KSeF session reference number");
    requireReference(upoReferenceNumber, "KSeF UPO reference number");
    return getBytes(
        environment.baseUrl()
            + "/sessions/"
            + sessionReferenceNumber
            + "/upo/"
            + upoReferenceNumber,
        accessToken,
        "application/xml");
  }

  public OnlineSession openOnlineSession(KsefEnvironment environment, String accessToken) {
    PublicKeyCertificate publicKey =
        currentPublicKey(environment.baseUrl(), "SymmetricKeyEncryption");
    byte[] key = generateAesKey();
    byte[] iv = new byte[16];
    new SecureRandom().nextBytes(iv);
    Map<String, Object> encryption =
        Map.of(
            "encryptedSymmetricKey",
                Base64.getEncoder()
                    .encodeToString(encryptRsa(publicKey(publicKey.certificate()), key)),
            "initializationVector", Base64.getEncoder().encodeToString(iv),
            "publicKeyId", publicKey.publicKeyId());
    Map<String, Object> body =
        Map.of(
            "formCode",
            Map.of("systemCode", "FA (3)", "schemaVersion", "1-0E", "value", "FA"),
            "encryption",
            encryption);
    OpenOnlineSessionResponse response =
        readJson(
            post(environment.baseUrl() + "/sessions/online", writeJson(body), accessToken),
            OpenOnlineSessionResponse.class);
    if (response.referenceNumber() == null || response.referenceNumber().isBlank()) {
      throw new KsefException("KSeF online session returned no reference number");
    }
    return new OnlineSession(response.referenceNumber(), key, iv);
  }

  public String sendOnlineInvoice(
      KsefEnvironment environment, String accessToken, OnlineSession session, byte[] invoice) {
    if (session == null || invoice == null || invoice.length == 0) {
      throw new IllegalArgumentException("KSeF online session and invoice are required");
    }
    byte[] encrypted = encryptAes(invoice, session.key(), session.iv());
    Map<String, Object> body =
        Map.of(
            "invoiceHash", sha256Base64(invoice),
            "invoiceSize", invoice.length,
            "encryptedInvoiceHash", sha256Base64(encrypted),
            "encryptedInvoiceSize", encrypted.length,
            "encryptedInvoiceContent", Base64.getEncoder().encodeToString(encrypted));
    String response =
        post(
            environment.baseUrl() + "/sessions/online/" + session.referenceNumber() + "/invoices",
            writeJson(body),
            accessToken);
    return readJson(response, SendInvoiceResponse.class).referenceNumber();
  }

  public void closeOnlineSession(
      KsefEnvironment environment, String accessToken, OnlineSession session) {
    if (session == null) throw new IllegalArgumentException("KSeF online session is required");
    post(
        environment.baseUrl() + "/sessions/online/" + session.referenceNumber() + "/close",
        null,
        accessToken);
  }

  PublicKeyCertificate currentTokenEncryptionKey(String baseUrl) {
    return currentPublicKey(baseUrl, KSEF_TOKEN_ENCRYPTION);
  }

  private PublicKeyCertificate currentPublicKey(String baseUrl, String requiredUsage) {
    PublicKeyCertificate[] certificates =
        readJson(
            get(baseUrl + "/security/public-key-certificates", null, "application/json"),
            PublicKeyCertificate[].class);
    OffsetDateTime now = OffsetDateTime.ofInstant(applicationTime.now(), ZoneOffset.UTC);
    return Arrays.stream(certificates)
        .filter(c -> c.usage() != null && c.usage().contains(requiredUsage))
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

  private static byte[] generateAesKey() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(256);
      return generator.generateKey().getEncoded();
    } catch (Exception e) {
      throw new KsefException("Failed to generate KSeF encryption key", e);
    }
  }

  private static byte[] encryptRsa(PublicKey publicKey, byte[] content) {
    try {
      OAEPParameterSpec oaep =
          new OAEPParameterSpec(
              "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
      Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaep);
      return cipher.doFinal(content);
    } catch (Exception e) {
      throw new KsefException("Failed to encrypt KSeF session key", e);
    }
  }

  private static byte[] encryptAes(byte[] content, byte[] key, byte[] iv) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new javax.crypto.spec.SecretKeySpec(key, "AES"),
          new javax.crypto.spec.IvParameterSpec(iv));
      return cipher.doFinal(content);
    } catch (Exception e) {
      throw new KsefException("Failed to encrypt KSeF invoice", e);
    }
  }

  private static String sha256Base64(byte[] content) {
    try {
      return Base64.getEncoder()
          .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(content));
    } catch (Exception e) {
      throw new KsefException("Failed to hash KSeF invoice", e);
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

  private void waitUntilAuthenticated(
      String baseUrl, String referenceNumber, String temporaryToken) {
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

  private byte[] getBytes(String url, String bearerToken, String accept) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Accept", accept == null ? "application/octet-stream" : accept)
            .GET();
    bearer(builder, bearerToken);
    return sendBytes(builder.build());
  }

  private String post(String url, String body, String bearerToken) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Accept", "application/json");
    if (body == null) {
      builder.POST(HttpRequest.BodyPublishers.noBody());
    } else {
      builder
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body));
    }
    bearer(builder, bearerToken);
    return send(builder.build());
  }

  private void bearer(HttpRequest.Builder builder, String token) {
    if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
  }

  private String send(HttpRequest request) {
    try {
      for (int attempt = 0; attempt < 5; attempt++) {
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 == 2) return response.body();
        if (response.statusCode() == 429 && attempt < 4) {
          sleepAfterRateLimit(response, attempt);
          continue;
        }
        throw new KsefException("KSeF returned HTTP " + response.statusCode());
      }
      throw new KsefException("KSeF request retry limit reached");
    } catch (KsefException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new KsefException("Interrupted while calling KSeF", e);
    } catch (Exception e) {
      throw new KsefException("Failed to call KSeF", e);
    }
  }

  private byte[] sendBytes(HttpRequest request) {
    try {
      for (int attempt = 0; attempt < 5; attempt++) {
        HttpResponse<byte[]> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 == 2) return response.body();
        if (response.statusCode() == 429 && attempt < 4) {
          sleepAfterRateLimit(response, attempt);
          continue;
        }
        throw new KsefException("KSeF returned HTTP " + response.statusCode());
      }
      throw new KsefException("KSeF request retry limit reached");
    } catch (KsefException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new KsefException("Interrupted while calling KSeF", e);
    } catch (Exception e) {
      throw new KsefException("Failed to call KSeF", e);
    }
  }

  private void sleepAfterRateLimit(HttpResponse<?> response, int attempt)
      throws InterruptedException {
    long seconds =
        response
            .headers()
            .firstValue("Retry-After")
            .flatMap(KsefClient::parseRetryAfter)
            .orElse((long) attempt + 1);
    Thread.sleep(Math.min(seconds, 60) * 1000L);
  }

  private static java.util.Optional<Long> parseRetryAfter(String value) {
    try {
      return java.util.Optional.of(Long.parseLong(value));
    } catch (NumberFormatException ignored) {
      return java.util.Optional.empty();
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

  private void requireReference(String reference, String label) {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
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

  record OpenOnlineSessionResponse(String referenceNumber) {}

  record SendInvoiceResponse(String referenceNumber) {}

  public record OnlineSession(String referenceNumber, byte[] key, byte[] iv) {}

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
