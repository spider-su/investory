package com.smartbox.investory.investment.imports;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImportOriginLookupService {
  private final JdbcTemplate jdbcTemplate;

  public Optional<ImportOrigin> findCashOperationOrigin(long id) {
    return find("cash_operations", id);
  }

  public Optional<ImportOrigin> findPositionOrigin(long id) {
    return find("positions", id);
  }

  private Optional<ImportOrigin> find(String table, long id) {
    String sql =
        """
        SELECT c.id, '%s', h.provider, h.id, h.attempt_no, h.status, h.file_name,
               h.file_sha256, h.finished_at, r.section_name, r.sheet_name,
               r.archive_member_name, r.source_row_number, r.source_record_id,
               r.source_row_occurrence, r.raw_text, r.raw_values::text
          FROM investory.%s c
          JOIN investory.import_history h ON h.id = c.import_history_id
          JOIN investory.import_source_rows r ON r.id = c.import_source_row_id
          JOIN investory.import_source_files f ON f.id = r.source_file_id
         WHERE c.id = ?
        """
            .formatted(table, table);
    return jdbcTemplate.query(
        sql,
        rs -> {
          if (!rs.next()) return Optional.empty();
          return Optional.of(
              new ImportOrigin(
                  rs.getLong(1),
                  rs.getString(2),
                  rs.getString(3),
                  rs.getLong(4),
                  rs.getInt(5),
                  rs.getString(6),
                  rs.getString(7),
                  rs.getString(8),
                  rs.getObject(9, java.time.OffsetDateTime.class) == null
                      ? null
                      : rs.getObject(9, java.time.OffsetDateTime.class).toZonedDateTime(),
                  rs.getString(10),
                  rs.getString(11),
                  rs.getString(12),
                  (Integer) rs.getObject(13),
                  rs.getString(14),
                  (Integer) rs.getObject(15),
                  rs.getString(16),
                  rs.getString(17)));
        });
  }
}
