-- Aggregator kept for backward compatibility and manual use. The authoritative sources are the
-- two overlays: happyinvestor-common.sql (broker-agnostic) and happyinvestor-broker.sql (imported
-- ledger). scripts/update-test-db-snapshot.sh applies the overlays directly; this file lets a
-- single 'psql -f canonical-data.sql' still load the full canonical story.
\ir happyinvestor-common.sql
\ir happyinvestor-broker.sql
