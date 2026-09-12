package com.smartbox.investory.accounting;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingSourceEvidenceService {
  private final AccountingSourceRepository repository;

  public long receiveUpload(String filename, String contentType, byte[] payload) {
    byte[] hash = sha256(payload);
    return repository.save(
        AccountingSourceType.UPLOAD,
        "sha256:" + hex(hash),
        filename,
        contentType,
        Instant.now(),
        null,
        hash,
        payload);
  }

  public long receiveKsef(String ksefNumber, LocalDate documentDate, byte[] payload) {
    byte[] hash = sha256(payload);
    return repository.save(
        AccountingSourceType.KSEF,
        ksefNumber,
        null,
        "application/xml",
        Instant.now(),
        documentDate,
        hash,
        payload);
  }

  public void status(long id, AccountingSourceStatus status, String error) {
    repository.updateStatus(id, status, error);
  }

  public AccountingSourceStatus status(long id) {
    return repository.status(id);
  }

  public java.util.Optional<Long> findId(AccountingSourceType type, String externalReference) {
    return repository.findId(type, externalReference);
  }

  public java.util.List<SourceOutcome> outcomes(LocalDate period) {
    return repository.outcomes(period);
  }

  public record SourceOutcome(String reference, String status, String error) {}

  private byte[] sha256(byte[] payload) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(payload == null ? new byte[0] : payload);
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) result.append(String.format("%02x", value));
    return result.toString();
  }
}
