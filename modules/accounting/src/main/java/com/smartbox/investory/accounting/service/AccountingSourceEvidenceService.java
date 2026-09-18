package com.smartbox.investory.accounting.service;

import com.smartbox.investory.accounting.*;
import com.smartbox.investory.accounting.infrastructure.persistence.*;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingSourceRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingSourceEvidenceService {
  private final AccountingSourceRepository repository;

  public long receiveUpload(long profileId, String filename, String contentType, byte[] payload) {
    byte[] hash = sha256(payload);
    return repository.save(
        profileId,
        AccountingSourceType.UPLOAD,
        "sha256:" + hex(hash),
        filename,
        contentType,
        Instant.now(),
        null,
        hash,
        payload);
  }

  public long receiveKsef(
      long profileId, String ksefNumber, LocalDate documentDate, byte[] payload) {
    byte[] hash = sha256(payload);
    return repository.save(
        profileId,
        AccountingSourceType.KSEF,
        ksefNumber,
        null,
        "application/xml",
        Instant.now(),
        documentDate,
        hash,
        payload);
  }

  public long receiveBank(
      long profileId, String filename, String contentType, byte[] payload, LocalDate documentDate) {
    byte[] hash = sha256(payload);
    return repository.save(
        profileId,
        AccountingSourceType.BANK,
        "sha256:" + hex(hash),
        filename,
        contentType,
        Instant.now(),
        documentDate,
        hash,
        payload);
  }

  public void status(long id, AccountingSourceStatus status, String error) {
    repository.updateStatus(id, status, error);
  }

  /** Reopens immutable failed evidence for an explicit reprocessing attempt. */
  public void retry(long id) {
    if (status(id) != AccountingSourceStatus.FAILED) {
      throw new IllegalStateException("Only failed source evidence can be retried");
    }
    repository.updateStatus(id, AccountingSourceStatus.RECEIVED, null);
  }

  public AccountingSourceStatus status(long id) {
    return repository.status(id);
  }

  public java.util.Optional<Long> findId(
      long profileId, AccountingSourceType type, String externalReference) {
    return repository.findId(profileId, type, externalReference);
  }

  public java.util.Optional<AccountingSourceRepository.SourceRow> findSource(
      long profileId, AccountingSourceType type, String externalReference) {
    return repository.findSource(profileId, type, externalReference);
  }

  public java.util.List<SourceOutcome> outcomes(long profileId, LocalDate period) {
    return repository.outcomes(profileId, period);
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
