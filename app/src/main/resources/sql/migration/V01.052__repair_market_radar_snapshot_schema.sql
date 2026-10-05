ALTER TABLE IF EXISTS investory.market_radar_snapshot
    ADD COLUMN IF NOT EXISTS id bigserial,
    ADD COLUMN IF NOT EXISTS symbol varchar(64),
    ADD COLUMN IF NOT EXISTS observed_on date,
    ADD COLUMN IF NOT EXISTS state varchar(32),
    ADD COLUMN IF NOT EXISTS close_price double precision,
    ADD COLUMN IF NOT EXISTS return_20d double precision,
    ADD COLUMN IF NOT EXISTS return_60d double precision,
    ADD COLUMN IF NOT EXISTS relative_volume_20d double precision,
    ADD COLUMN IF NOT EXISTS distance_sma50 double precision,
    ADD COLUMN IF NOT EXISTS rsi14 double precision,
    ADD COLUMN IF NOT EXISTS reasons text,
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone default current_timestamp;

DO $$
BEGIN
    IF to_regclass('investory.market_radar_snapshot') IS NOT NULL THEN
        ALTER TABLE investory.market_radar_snapshot
            ALTER COLUMN symbol SET NOT NULL,
            ALTER COLUMN observed_on SET NOT NULL,
            ALTER COLUMN state SET NOT NULL,
            ALTER COLUMN close_price SET NOT NULL,
            ALTER COLUMN created_at SET NOT NULL;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conrelid = 'investory.market_radar_snapshot'::regclass
              AND conname = 'uk_market_radar_snapshot_symbol_date'
        ) THEN
            ALTER TABLE investory.market_radar_snapshot
                ADD CONSTRAINT uk_market_radar_snapshot_symbol_date
                UNIQUE (symbol, observed_on);
        END IF;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_market_radar_snapshot_observed
    ON investory.market_radar_snapshot (observed_on DESC);
