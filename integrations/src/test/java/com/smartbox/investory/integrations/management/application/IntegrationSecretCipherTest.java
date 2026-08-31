package com.smartbox.investory.integrations.management.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Integration Secret Cipher")
class IntegrationSecretCipherTest {
  @DisplayName("encrypts And Decrypts Without Storing Plaintext")
  @Test
  void encryptsAndDecryptsWithoutStoringPlaintext() {
    IntegrationSecretCipher cipher = new IntegrationSecretCipher("test-master-key");

    String encrypted = cipher.encrypt("api-secret");

    assertEquals("api-secret", cipher.decrypt(encrypted));
    org.junit.jupiter.api.Assertions.assertFalse(encrypted.contains("api-secret"));
    assertEquals("sha256-v1", cipher.keyVersion());
  }

  @DisplayName("rejects Missing Master Key")
  @Test
  void rejectsMissingMasterKey() {
    IntegrationSecretCipher cipher = new IntegrationSecretCipher("");

    var exception = assertThrows(IllegalStateException.class, () -> cipher.encrypt("secret"));

    assertEquals("INVESTORY_INTEGRATION_MASTER_KEY is not configured", exception.getMessage());
  }
}
