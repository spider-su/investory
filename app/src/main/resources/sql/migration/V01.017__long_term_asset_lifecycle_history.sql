SET search_path TO investory, public;

CREATE TABLE investory.long_term_asset_lifecycle_periods (
    id bigserial PRIMARY KEY,
    asset_id bigint NOT NULL REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    active_from date NOT NULL,
    active_to date,
    CHECK (active_to IS NULL OR active_to >= active_from)
);

INSERT INTO investory.long_term_asset_lifecycle_periods (asset_id, active_from, active_to)
SELECT id,
       COALESCE(acquisition_date, created_at::date),
       CASE
           WHEN active THEN NULL
           WHEN archived_at IS NOT NULL THEN archived_at - 1
           ELSE created_at::date
       END
FROM investory.long_term_assets;

CREATE INDEX ix_long_term_asset_lifecycle_periods_asset_dates
    ON investory.long_term_asset_lifecycle_periods(asset_id, active_from, active_to);

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE investory.long_term_asset_lifecycle_periods
    ADD CONSTRAINT ex_long_term_asset_lifecycle_period
    EXCLUDE USING gist (
        asset_id WITH =,
        daterange(active_from, COALESCE(active_to, 'infinity'::date), '[]') WITH &&
    );
