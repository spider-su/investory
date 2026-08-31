package com.smartbox.investory.integration.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IntegrationSecretCipher {
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_SIZE = 12;
  private static final int TAG_BITS = 128;
  private static final String KEY_VERSION = "sha256-v1";

  private final String masterKey;

  public IntegrationSecretCipher(@Value("${app.integrations.master-key:}") String masterKey) {
    this.masterKey = masterKey;
  }

  public String keyVersion() {
    return KEY_VERSION;
  }

  public String encrypt(String plaintext) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      byte[] iv = new byte[IV_SIZE];
      new SecureRandom().nextBytes(iv);
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot encrypt integration secret", exception);
    }
  }

  public String decrypt(String ciphertext) {
    try {
      byte[] encoded = Base64.getDecoder().decode(ciphertext);
      byte[] iv = java.util.Arrays.copyOf(encoded, IV_SIZE);
      byte[] payload = java.util.Arrays.copyOfRange(encoded, IV_SIZE, encoded.length);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot decrypt integration secret", exception);
    }
  }

  private SecretKeySpec key() {
    if (masterKey == null || masterKey.isBlank()) {
      throw new IllegalStateException("INVESTORY_INTEGRATION_MASTER_KEY is not configured");
    }
    try {
      return new SecretKeySpec(
          MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8)),
          "AES");
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot derive integration secret key", exception);
    }
  }
}
