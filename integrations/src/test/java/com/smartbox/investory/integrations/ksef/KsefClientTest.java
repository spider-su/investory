package com.smartbox.investory.integrations.ksef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.time.Instant;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.Test;

class KsefClientTest {

  @Test
  void encryptTokenUsesKsefTokenTimestampPayloadAndRsaOaepSha256() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    Instant challengeTimestamp = Instant.parse("2026-09-11T12:00:00Z");

    byte[] encrypted =
        KsefClient.encryptToken(keyPair.getPublic(), "secret-token", challengeTimestamp);

    OAEPParameterSpec oaep =
        new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), oaep);
    String plaintext = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);

    assertEquals("secret-token|1789128000000", plaintext);
  }

  @Test
  void rejectsUnknownEnvironment() {
    assertThrows(IllegalArgumentException.class, () -> KsefEnvironment.parse("unknown"));
  }

  @Test
  void mapsOfficialKsefEnvironments() {
    assertEquals("https://api-test.ksef.mf.gov.pl/v2", KsefEnvironment.TEST.baseUrl());
    assertEquals("https://api-demo.ksef.mf.gov.pl/v2", KsefEnvironment.DEMO.baseUrl());
    assertEquals("https://api.ksef.mf.gov.pl/v2", KsefEnvironment.PRODUCTION.baseUrl());
  }
}
