SET search_path TO investory, public;

CREATE OR REPLACE VIEW investory.recon_v_import_provenance_issues AS
WITH latest_attempt AS (
    SELECT DISTINCT ON (provider, file_sha256)
           id, provider, file_sha256
    FROM investory.import_history
    ORDER BY provider, file_sha256, attempt_no DESC, id DESC
),
latest_source_rows AS (
    SELECT r.*
    FROM investory.import_source_rows r
    JOIN latest_attempt a ON a.id = r.import_history_id
),
canonical_source_row_ids AS MATERIALIZED (
    SELECT c.import_source_row_id
    FROM investory.cash_operations c
    WHERE c.import_source_row_id IS NOT NULL
    UNION
    SELECT p.import_source_row_id
    FROM investory.positions p
    WHERE p.import_source_row_id IS NOT NULL
),
canonical_logical_rows AS MATERIALIZED (
    SELECT DISTINCT source.provider, source.logical_row_sha256
    FROM canonical_source_row_ids linked
    JOIN investory.import_source_rows source ON source.id = linked.import_source_row_id
    WHERE source.logical_row_sha256 IS NOT NULL
)
SELECT 'CASH_OPERATION_MISSING_IMPORT'::text AS issue_code, c.id::text AS financial_row_id,
       c.import_history_id, c.import_source_row_id, 'cash_operations'::text AS financial_table
FROM investory.cash_operations c
WHERE c.import_history_id IS NULL AND c.import_source_row_id IS NOT NULL
UNION ALL
SELECT 'CASH_OPERATION_MISSING_SOURCE_ROW', c.id::text, c.import_history_id,
       c.import_source_row_id, 'cash_operations'
FROM investory.cash_operations c
WHERE c.import_history_id IS NOT NULL AND c.import_source_row_id IS NULL
UNION ALL
SELECT 'POSITION_MISSING_IMPORT', p.id::text, p.import_history_id,
       p.import_source_row_id, 'positions'
FROM investory.positions p
WHERE p.import_history_id IS NULL AND p.import_source_row_id IS NOT NULL
UNION ALL
SELECT 'POSITION_MISSING_SOURCE_ROW', p.id::text, p.import_history_id,
       p.import_source_row_id, 'positions'
FROM investory.positions p
WHERE p.import_history_id IS NOT NULL AND p.import_source_row_id IS NULL
UNION ALL
SELECT 'SOURCE_ROW_WRONG_IMPORT', r.id::text, r.import_history_id,
       r.id, 'import_source_rows'
FROM latest_source_rows r
JOIN investory.import_source_files f ON f.id = r.source_file_id
JOIN investory.import_history h ON h.id = r.import_history_id
WHERE f.provider <> h.provider OR f.file_sha256 <> h.file_sha256
UNION ALL
SELECT 'ORPHAN_SOURCE_ROW', r.id::text, r.import_history_id,
       r.id, 'import_source_rows'
FROM latest_source_rows r
LEFT JOIN investory.cash_operations c ON c.import_source_row_id = r.id
LEFT JOIN investory.positions p ON p.import_source_row_id = r.id
LEFT JOIN canonical_logical_rows linked
       ON linked.provider = r.provider
      AND linked.logical_row_sha256 = r.logical_row_sha256
WHERE c.id IS NULL AND p.id IS NULL
  AND (r.logical_row_sha256 IS NULL OR linked.provider IS NULL)
UNION ALL
SELECT 'CANONICAL_ROW_WRONG_IMPORT', c.id::text, c.import_history_id,
       c.import_source_row_id, 'cash_operations'
FROM investory.cash_operations c
JOIN investory.import_source_rows r ON r.id = c.import_source_row_id
WHERE c.import_history_id <> r.import_history_id
UNION ALL
SELECT 'CANONICAL_POSITION_WRONG_IMPORT', p.id::text, p.import_history_id,
       p.import_source_row_id, 'positions'
FROM investory.positions p
JOIN investory.import_source_rows r ON r.id = p.import_source_row_id
WHERE p.import_history_id <> r.import_history_id
UNION ALL
SELECT 'SOURCE_FILE_CHECKSUM_MISMATCH', f.id::text, f.import_history_id,
       NULL::bigint, 'import_source_files'
FROM investory.import_source_files f
JOIN investory.import_history h ON h.id = f.import_history_id
WHERE f.file_sha256 <> h.file_sha256
UNION ALL
SELECT 'DUPLICATE_SOURCE_IDENTITY',
       string_agg(r.id::text, ',' ORDER BY r.id),
       min(r.import_history_id), NULL, 'import_source_rows'
FROM latest_source_rows r
WHERE r.source_record_id IS NOT NULL
GROUP BY r.provider, r.archive_member_name, r.section_name, r.sheet_name,
         r.source_record_id, r.source_row_occurrence, r.raw_values
HAVING count(*) > 1;

COMMENT ON VIEW investory.recon_v_import_provenance_issues IS
    'Import provenance diagnostics. Source observations are accounted for by directly linked or equivalent canonical logical rows; linked identities are precomputed for bounded reconciliation queries.';
