package com.smartbox.investory.accounting;

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
    return jdbcTemplate.query(
        "SELECT id FROM investory.accounting_source_evidence WHERE source_type = ? AND external_reference = ?",
        rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(),
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
    jdbcTemplate.update(
        """
        INSERT INTO investory.accounting_source_evidence
          (source_type, external_reference, original_filename, content_type, received_at,
           document_date, content_hash, payload, processing_status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED')
        ON CONFLICT (source_type, external_reference) DO NOTHING
        """,
        type.name(),
        externalReference,
        originalFilename,
        contentType,
        Timestamp.from(receivedAt),
        documentDate,
        contentHash,
        payload);
    return findId(type, externalReference).orElseThrow();
  }

  public java.util.List<AccountingSourceEvidenceService.SourceOutcome> outcomes(LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT external_reference, processing_status, processing_error
          FROM investory.accounting_source_evidence
         WHERE document_date = ? OR document_date IS NULL
         ORDER BY id
        """,
        (rs, rowNum) ->
            new AccountingSourceEvidenceService.SourceOutcome(
                rs.getString("external_reference"),
                rs.getString("processing_status"),
                rs.getString("processing_error")),
        period);
  }

  public java.util.List<AccountingSourceEvidenceService.SourceOutcome> bankOutcomes(
      LocalDate period) {
    return jdbcTemplate.query(
        """
        SELECT external_reference, processing_status, processing_error
          FROM investory.accounting_source_evidence
         WHERE source_type = 'BANK' AND (document_date = ? OR document_date IS NULL)
         ORDER BY id
        """,
        (rs, rowNum) ->
            new AccountingSourceEvidenceService.SourceOutcome(
                rs.getString("external_reference"),
                rs.getString("processing_status"),
                rs.getString("processing_error")),
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
}
