package com.smartbox.investory.integrations.infrastructure.integration.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IntegrationSecretCipherTest {
  @Test
  void encryptsAndDecryptsWithoutStoringPlaintext() {
    IntegrationSecretCipher cipher = new IntegrationSecretCipher("test-master-key");

    String encrypted = cipher.encrypt("api-secret");

    assertEquals("api-secret", cipher.decrypt(encrypted));
    org.junit.jupiter.api.Assertions.assertFalse(encrypted.contains("api-secret"));
    assertEquals("sha256-v1", cipher.keyVersion());
  }

  @Test
  void rejectsMissingMasterKey() {
    IntegrationSecretCipher cipher = new IntegrationSecretCipher("");

    assertThrows(IllegalStateException.class, () -> cipher.encrypt("secret"));
  }
}
