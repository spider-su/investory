-- Each import attempt owns its evidence file row, even when the payload checksum repeats.
-- Source rows can then use the composite FK to prove file and import provenance agree.
DROP INDEX investory.ux_import_source_files_provider_sha256;
