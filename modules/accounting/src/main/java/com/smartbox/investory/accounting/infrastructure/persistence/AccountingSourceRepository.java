package com.smartbox.investory.accounting.infrastructure.persistence;

import com.smartbox.investory.accounting.*;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountingSourceRepository {
  private final JdbcTemplate jdbcTemplate;

  public Optional<Long> findId(AccountingSourceType type, String externalReference) {
    return findId(1L, type, externalReference);
  }

  public Optional<Long> findId(
      long profileId, AccountingSourceType type, String externalReference) {
    return jdbcTemplate.query(
        "SELECT id FROM investory.accounting_source_evidence WHERE profile_id = ? AND source_type = ? AND external_reference = ?",
        rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(),
        profileId,
        type.name(),
        externalReference);
  }

  public Optional<SourceRow> findSource(
      long profileId, AccountingSourceType type, String externalReference) {
    return jdbcTemplate.query(
        """
        SELECT id, source_type, external_reference, original_filename, content_type,
               document_date, processing_status, processing_error, payload
          FROM investory.accounting_source_evidence
         WHERE profile_id = ? AND source_type = ? AND external_reference = ?
        """,
        rs ->
            rs.next()
                ? Optional.of(
                    new SourceRow(
                        rs.getLong("id"),
                        AccountingSourceType.valueOf(rs.getString("source_type")),
                        rs.getString("external_reference"),
                        rs.getString("original_filename"),
                        rs.getString("content_type"),
                        rs.getObject("document_date", LocalDate.class),
                        AccountingSourceStatus.valueOf(rs.getString("processing_status")),
                        rs.getString("processing_error"),
                        rs.getBytes("payload")))
                : Optional.empty(),
        profileId,
        type.name(),
        externalReference);
  }

  public long save(
      AccountingSourceType type,
      String externalReference,
      String originalFilename,
      String contentType,
      Instant receivedAt,
      LocalDate documentDate,
      byte[] contentHash,
      byte[] payload) {
    return save(
        1L,
        type,
        externalReference,
        originalFilename,
        contentType,
        receivedAt,
        documentDate,
        contentHash,
        payload);
  }

  public long save(
      long profileId,
      AccountingSourceType type,
      String externalReference,
      String originalFilename,
      String contentType,
      Instant receivedAt,
      LocalDate documentDate,
      byte[] contentHash,
      byte[] payload) {
    jdbcTemplate.update(
        """
        INSERT INTO investory.accounting_source_evidence
          (profile_id, source_type, external_reference, original_filename, content_type, received_at,
           document_date, content_hash, payload, processing_status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED')
        ON CONFLICT (profile_id, source_type, external_reference) DO NOTHING
        """,
        profileId,
        type.name(),
        externalReference,
        originalFilename,
        contentType,
        Timestamp.from(receivedAt),
        documentDate,
        contentHash,
        payload);
    return findId(profileId, type, externalReference).orElseThrow();
  }

  public java.util.List<AccountingSourceEvidenceService.SourceOutcome> outcomes(LocalDate period) {
    return outcomes(1L, period);
  }

  public java.util.List<AccountingSourceEvidenceService.SourceOutcome> outcomes(
      long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT external_reference, processing_status, processing_error
          FROM investory.accounting_source_evidence
         WHERE profile_id = ?
           AND COALESCE(
                 document_date,
                 CASE
                   WHEN source_type = 'KSEF' AND external_reference ~ '^[0-9]+-[0-9]{8}-'
                   THEN to_date(substring(external_reference from '^[0-9]+-([0-9]{8})-'), 'YYYYMMDD')
                 END) >= CAST(? AS date)
           AND COALESCE(
                 document_date,
                 CASE
                   WHEN source_type = 'KSEF' AND external_reference ~ '^[0-9]+-[0-9]{8}-'
                   THEN to_date(substring(external_reference from '^[0-9]+-([0-9]{8})-'), 'YYYYMMDD')
                 END) < (CAST(? AS date) + INTERVAL '1 month')
         ORDER BY id
        """,
        (rs, rowNum) ->
            new AccountingSourceEvidenceService.SourceOutcome(
                rs.getString("external_reference"),
                rs.getString("processing_status"),
                rs.getString("processing_error")),
        profileId,
        period,
        period);
  }

  public java.util.List<AccountingSourceEvidenceService.SourceOutcome> bankOutcomes(
      LocalDate period) {
    return bankOutcomes(1L, period);
  }

  public java.util.List<AccountingSourceEvidenceService.SourceOutcome> bankOutcomes(
      long profileId, LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT external_reference, processing_status, processing_error
          FROM investory.accounting_source_evidence
         WHERE profile_id = ? AND source_type = 'BANK'
           AND document_date >= ? AND document_date < (? + INTERVAL '1 month')
         ORDER BY id
        """,
        (rs, rowNum) ->
            new AccountingSourceEvidenceService.SourceOutcome(
                rs.getString("external_reference"),
                rs.getString("processing_status"),
                rs.getString("processing_error")),
        profileId,
        period,
        period);
  }

  public void updateStatus(long id, AccountingSourceStatus status, String error) {
    jdbcTemplate.update(
        "UPDATE investory.accounting_source_evidence SET processing_status = ?, processing_error = ? WHERE id = ?",
        status.name(),
        error,
        id);
  }

  public AccountingSourceStatus status(long id) {
    return jdbcTemplate.queryForObject(
        "SELECT processing_status FROM investory.accounting_source_evidence WHERE id = ?",
        (rs, rowNum) -> AccountingSourceStatus.valueOf(rs.getString(1)),
        id);
  }

  public record SourceRow(
      long id,
      AccountingSourceType type,
      String externalReference,
      String originalFilename,
      String contentType,
      LocalDate documentDate,
      AccountingSourceStatus status,
      String error,
      byte[] payload) {}
}
