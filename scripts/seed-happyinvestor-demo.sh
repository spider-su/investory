#!/usr/bin/env bash
set -euo pipefail

# Seed the documented HappyInvestor scenario into a disposable/demo database.
#
# This intentionally uses the fixture's canonical user ID, account IDs, and a
# configurable portfolio ID. It therefore replaces/repoints the selected
# migration/sample demo data and must not be run against real user data.
#
# Usage:
#   DB_USERNAME=postgres PGPASSWORD=postgres \
#     HAPPYINVESTOR_PORTFOLIO_ID=2 \
#     ./scripts/seed-happyinvestor-demo.sh --yes
#
# Connection variables follow libpq conventions. Defaults are suitable for the
# local README setup and can be overridden with PGHOST, PGPORT, PGDATABASE and
# PGUSER (or DB_HOST, DB_PORT, DB_NAME and DB_USERNAME).

usage() {
  echo "Usage: $0 --yes" >&2
  echo >&2
  echo "Seeds the canonical HappyInvestor fixture into the configured PostgreSQL database." >&2
  echo "This replaces the sample user and selected portfolio with fixture user ID 1." >&2
}

if [[ "${1:-}" != "--yes" || $# -ne 1 ]]; then
  usage
  exit 2
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/.." && pwd)"
common_data_file="$repo_dir/test-support/src/main/resources/db/snapshot/happyinvestor-common.sql"
broker_data_file="$repo_dir/test-support/src/main/resources/db/snapshot/happyinvestor-broker.sql"
portfolio_id="${HAPPYINVESTOR_PORTFOLIO_ID:-2}"
user_id="${HAPPYINVESTOR_USER_ID:-2}"
ibkr_account_id="${HAPPYINVESTOR_IBKR_ACCOUNT_ID:-91000001}"
xtb_usd_account_id="${HAPPYINVESTOR_XTB_USD_ACCOUNT_ID:-91000002}"
xtb_pln_account_id="${HAPPYINVESTOR_XTB_PLN_ACCOUNT_ID:-91000003}"
xtb_eur_account_id="${HAPPYINVESTOR_XTB_EUR_ACCOUNT_ID:-91000004}"

for configured_id in "$portfolio_id" "$user_id" "$ibkr_account_id" "$xtb_usd_account_id" \
  "$xtb_pln_account_id" "$xtb_eur_account_id"; do
  [[ "$configured_id" =~ ^[1-9][0-9]*$ ]] || {
    echo "HappyInvestor IDs must be positive integers" >&2
    exit 2
  }
done

[[ -f "$common_data_file" ]] || { echo "Fixture not found: $common_data_file" >&2; exit 1; }
[[ -f "$broker_data_file" ]] || { echo "Fixture not found: $broker_data_file" >&2; exit 1; }

temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
parameterized_common_data_file="$temporary_dir/happyinvestor-common.sql"
parameterized_broker_data_file="$temporary_dir/happyinvestor-broker.sql"

canonical_asset_id() {
  local symbol="$1"
  local result
  result="$(psql_demo -Atqc "SELECT id FROM investory.assets WHERE symbol = '${symbol}' ORDER BY id")"
  if [[ "$(printf '%s\n' "$result" | sed '/^$/d' | wc -l)" -ne 1 ]]; then
    echo "Expected exactly one canonical asset for ${symbol}; found: ${result:-none}" >&2
    exit 1
  fi
  printf '%s' "$result"
}

# The shared fixture is also consumed by tests. Parameterize only this seed
# invocation so the demo can use a different ID set when needed.
PORTFOLIO_ID="$portfolio_id" USER_ID="$user_id" IBKR_ACCOUNT_ID="$ibkr_account_id" \
XTB_USD_ACCOUNT_ID="$xtb_usd_account_id" XTB_PLN_ACCOUNT_ID="$xtb_pln_account_id" \
XTB_EUR_ACCOUNT_ID="$xtb_eur_account_id" perl -0pe '
  s/(VALUES \()2(, \x27happy\.investor\x27)/${1}$ENV{USER_ID}$2/;
  s/(VALUES \()2(, \x27Happy Investor Portfolio\x27.*?, )2\)/${1}$ENV{PORTFOLIO_ID}$2$ENV{USER_ID})/s;
  s/\(2, 2, \x27OWNER\x27\)/($ENV{USER_ID}, $ENV{PORTFOLIO_ID}, \x27OWNER\x27)/g;
  s/portfolio_id = 2/portfolio_id = $ENV{PORTFOLIO_ID}/g;
  s/\((940[1-6]|9201|9301), 2,/($1, $ENV{PORTFOLIO_ID},/g;
  s/(\(\s*9301,\s*)2,/$1$ENV{PORTFOLIO_ID},/g;
  s/91000001/$ENV{IBKR_ACCOUNT_ID}/g;
  s/91000002/$ENV{XTB_USD_ACCOUNT_ID}/g;
  s/91000003/$ENV{XTB_PLN_ACCOUNT_ID}/g;
  s/91000004/$ENV{XTB_EUR_ACCOUNT_ID}/g;
' "$common_data_file" > "$parameterized_common_data_file"

IBKR_ACCOUNT_ID="$ibkr_account_id" XTB_USD_ACCOUNT_ID="$xtb_usd_account_id" \
XTB_PLN_ACCOUNT_ID="$xtb_pln_account_id" XTB_EUR_ACCOUNT_ID="$xtb_eur_account_id" \
perl -0pe '
  s/91000001/$ENV{IBKR_ACCOUNT_ID}/g;
  s/91000002/$ENV{XTB_USD_ACCOUNT_ID}/g;
  s/91000003/$ENV{XTB_PLN_ACCOUNT_ID}/g;
  s/91000004/$ENV{XTB_EUR_ACCOUNT_ID}/g;
' "$broker_data_file" > "$parameterized_broker_data_file"

export PGHOST="${PGHOST:-${DB_HOST:-localhost}}"
export PGPORT="${PGPORT:-${DB_PORT:-5432}}"
export PGDATABASE="${PGDATABASE:-${DB_NAME:-investory}}"
export PGUSER="${PGUSER:-${DB_USERNAME:-postgres}}"

psql_demo() {
  psql --set=ON_ERROR_STOP=1 "$@"
}

echo "Seeding HappyInvestor demo in ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE} (portfolio ${portfolio_id})"

# Fail before changing anything when this is not a Flyway-initialized database.
psql_demo -v portfolio_id="$portfolio_id" -v user_id="$user_id" \
  -v ibkr_account_id="$ibkr_account_id" -v xtb_usd_account_id="$xtb_usd_account_id" \
  -v xtb_pln_account_id="$xtb_pln_account_id" -v xtb_eur_account_id="$xtb_eur_account_id" <<SQL
DO \$\$
BEGIN
  IF to_regclass('investory.app_users') IS NULL
     OR to_regclass('investory.portfolios') IS NULL THEN
    RAISE EXCEPTION 'investory schema is missing; run Flyway first';
  END IF;

  IF EXISTS (SELECT 1 FROM investory.app_users
             WHERE id = ${user_id} AND username <> 'happy.investor') THEN
    RAISE EXCEPTION 'configured HappyInvestor user ID is occupied';
  END IF;

  IF EXISTS (SELECT 1 FROM investory.portfolios
             WHERE id = ${portfolio_id} AND user_id <> ${user_id}) THEN
    RAISE EXCEPTION 'configured HappyInvestor portfolio ID is owned by another user';
  END IF;

  IF EXISTS (SELECT 1 FROM investory.accounts
             WHERE id IN (${ibkr_account_id}, ${xtb_usd_account_id}, ${xtb_pln_account_id}, ${xtb_eur_account_id})
               AND portfolio_id <> ${portfolio_id}) THEN
    RAISE EXCEPTION 'configured HappyInvestor account ID is occupied';
  END IF;

  IF EXISTS (SELECT 1 FROM investory.accounts
             WHERE portfolio_id = ${portfolio_id}
               AND provider = 'IBKR' AND external_account_id = '90000001'
               AND id <> ${ibkr_account_id})
     OR EXISTS (SELECT 1 FROM investory.accounts
               WHERE portfolio_id = ${portfolio_id}
                 AND provider = 'XTB' AND external_account_id IN ('90000002', '90000003', '90000009')
                 AND id NOT IN (${xtb_usd_account_id}, ${xtb_pln_account_id}, ${xtb_eur_account_id})) THEN
    RAISE EXCEPTION 'canonical HappyInvestor external account ID is already used';
  END IF;

END
\$\$;
SQL

# Asset IDs are global and may differ between databases. Resolve them by the
# canonical symbol before rendering the broker overlay; never assume the
# fixture's snapshot IDs are free in a demo database.
aapl_asset_id="$(canonical_asset_id AAPL.US)"
tsla_asset_id="$(canonical_asset_id TSLA.US)"
vwra_asset_id="$(canonical_asset_id VWRA.UK)"
nvda_asset_id="$(canonical_asset_id NVDA.US)"
googl_asset_id="$(canonical_asset_id GOOGL.US)"
msft_asset_id="$(canonical_asset_id MSFT.US)"
natgas_asset_id="$(canonical_asset_id NATGAS)"
old_treasury_asset_id="$(canonical_asset_id US91282CKB62)"
new_treasury_asset_id="$(canonical_asset_id US91282CRC72)"

export AAPL_ASSET_ID="$aapl_asset_id" TSLA_ASSET_ID="$tsla_asset_id" \
  VWRA_ASSET_ID="$vwra_asset_id" NVDA_ASSET_ID="$nvda_asset_id" \
  GOOGL_ASSET_ID="$googl_asset_id" MSFT_ASSET_ID="$msft_asset_id" \
  NATGAS_ASSET_ID="$natgas_asset_id" OLD_TREASURY_ASSET_ID="$old_treasury_asset_id" \
  NEW_TREASURY_ASSET_ID="$new_treasury_asset_id"

AAPL_ASSET_ID="$aapl_asset_id" TSLA_ASSET_ID="$tsla_asset_id" VWRA_ASSET_ID="$vwra_asset_id" \
NVDA_ASSET_ID="$nvda_asset_id" GOOGL_ASSET_ID="$googl_asset_id" MSFT_ASSET_ID="$msft_asset_id" \
NATGAS_ASSET_ID="$natgas_asset_id" OLD_TREASURY_ASSET_ID="$old_treasury_asset_id" \
NEW_TREASURY_ASSET_ID="$new_treasury_asset_id" perl -0pi -e '
  s/(\(\s*\d+,[^,]+,\s*)1(,\s*\x27AAPL\.US\x27)/$1$ENV{AAPL_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*)1001(,\s*\x27TSLA\.US\x27)/$1$ENV{TSLA_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*)1151(,\s*\x27VWRA\.UK\x27)/$1$ENV{VWRA_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*)651(,\s*\x27NVDA\.US\x27)/$1$ENV{NVDA_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*)251(,\s*\x27GOOGL\.US\x27)/$1$ENV{GOOGL_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*)451(,\s*\x27MSFT\.US\x27)/$1$ENV{MSFT_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*)501(,\s*\x27NATGAS\x27)/$1$ENV{NATGAS_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*)1201(,\s*\x27US91282CKB62\x27)/$1$ENV{OLD_TREASURY_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*)1251(,\s*\x27US91282CRC72\x27)/$1$ENV{NEW_TREASURY_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*\x27[^\x27]+\x27,\s*)1201(,\s*\x27US91282CKB62\x27)/$1$ENV{OLD_TREASURY_ASSET_ID}$2/g;
  s/(\(\s*\d+,[^,]+,\s*\x27[^\x27]+\x27,\s*)1251(,\s*\x27US91282CRC72\x27)/$1$ENV{NEW_TREASURY_ASSET_ID}$2/g;
' "$parameterized_broker_data_file"

psql_demo -v common_data_file="$parameterized_common_data_file" -v broker_data_file="$parameterized_broker_data_file" \
  -v portfolio_id="$portfolio_id" -v user_id="$user_id" \
  -v ibkr_account_id="$ibkr_account_id" -v xtb_usd_account_id="$xtb_usd_account_id" \
  -v xtb_pln_account_id="$xtb_pln_account_id" -v xtb_eur_account_id="$xtb_eur_account_id" <<'SQL'
BEGIN;
SET LOCAL search_path TO investory, public;

INSERT INTO app_users (id, username, display_name, birth_date, active, role, updated_at)
VALUES (:user_id, 'happy.investor', 'Happy Investor', DATE '1984-01-01', true, 'PROFILE_OWNER', now())
ON CONFLICT (id) DO UPDATE SET username = EXCLUDED.username,
    display_name = EXCLUDED.display_name, birth_date = EXCLUDED.birth_date,
    active = EXCLUDED.active, role = EXCLUDED.role, updated_at = EXCLUDED.updated_at;

INSERT INTO portfolios (id, name, base_currency, local_currency, owner, user_id)
VALUES (:portfolio_id, 'Happy Investor Portfolio', 'PLN', 'PLN', 'Happy Investor', :user_id)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name,
    base_currency = EXCLUDED.base_currency, local_currency = EXCLUDED.local_currency,
    owner = EXCLUDED.owner, user_id = EXCLUDED.user_id;

-- The broker overlay uses these canonical account IDs. Repointing/upserting
-- them gives a newly created target portfolio the complete broker fixture.
INSERT INTO accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only)
VALUES
    (:ibkr_account_id, '90000001', 'USD', 'IBKR', 'IBKR USD investment account', 'Happy Investor', :portfolio_id, false),
    (:xtb_usd_account_id, '90000002', 'USD', 'XTB', 'XTB USD investment account', 'Happy Investor', :portfolio_id, false),
    (:xtb_pln_account_id, '90000003', 'PLN', 'XTB', 'XTB PLN investment account', 'Happy Investor', :portfolio_id, false),
    (:xtb_eur_account_id, '90000009', 'EUR', 'XTB', 'XTB EUR cash-only account', 'Happy Investor', :portfolio_id, true)
ON CONFLICT (id) DO UPDATE SET external_account_id = EXCLUDED.external_account_id,
    currency = EXCLUDED.currency, provider = EXCLUDED.provider, name = EXCLUDED.name,
    owner = EXCLUDED.owner, portfolio_id = EXCLUDED.portfolio_id, cash_only = EXCLUDED.cash_only;

UPDATE app_users
SET username = 'happy.investor',
    display_name = 'Happy Investor',
    birth_date = DATE '1984-01-01',
    role = 'PROFILE_OWNER',
    updated_at = now()
WHERE id = :user_id;

-- The migration sample may use a different primary key for the same unique
-- portfolio/year pair. Remove only that conflicting sample row so the fixture
-- can restore its canonical planning-year ID below.
DELETE FROM retirement_planning_years
WHERE portfolio_id = :portfolio_id
  AND planning_year = 2025
  AND id <> 9301;

\ir :common_data_file
\ir :broker_data_file

-- Preserve the quote convention when an observed bond price is carried forward.
-- 98.81 is 98.81% of par, not 98.81 currency units per face unit.
UPDATE asset_price_history aph
SET quality_class = 'STALE_CARRY_FORWARD_PERCENT_OF_PAR'
FROM assets asset
WHERE asset.id = aph.asset_id
  AND asset.asset_type = 'BOND'
  AND aph.source = 'CARRY_FORWARD'
  AND aph.quality_class = 'STALE_CARRY_FORWARD';

-- Existing demo databases may already contain the same position IDs linked to
-- a different global asset catalog. Reconcile the foreign key by the
-- canonical imported symbol after the idempotent overlay is applied.
UPDATE positions pos
SET asset_id = asset.id
FROM assets asset
JOIN accounts account ON account.id IN (:ibkr_account_id, :xtb_usd_account_id, :xtb_pln_account_id, :xtb_eur_account_id)
WHERE pos.account_id = account.id
  AND asset.symbol = pos.source_asset_symbol;

INSERT INTO profile_memberships (user_id, profile_id, role)
VALUES (:user_id, :portfolio_id, 'OWNER')
ON CONFLICT (user_id, profile_id) DO UPDATE SET role = EXCLUDED.role;

COMMIT;
SQL

# The fixture changes source rows used by materialized read models. Populate
# them in the same dependency order used by the generated test snapshot.
psql_demo <<'SQL'
SELECT investory.refresh_app_views();
SELECT investory.refresh_reconstructed_position_daily();
SELECT investory.refresh_reconstructed_account_market_daily();
SELECT investory.refresh_reconstructed_cash_daily();
SELECT investory.refresh_account_daily_reconciliation();
-- Application reporting views consume the rebuilt account_daily/projection rows;
-- refresh them again after the projection stages, not only before them.
SELECT investory.refresh_app_views();
SELECT investory.refresh_reconciliation_reporting_views();
SQL

psql_demo -v user_id="$user_id" -v portfolio_id="$portfolio_id" <<'SQL'
SELECT u.id, u.username, u.display_name, p.id AS portfolio_id, p.name AS portfolio_name,
       (SELECT count(*) FROM investory.accounts a WHERE a.portfolio_id = p.id) AS account_count,
       (SELECT count(*) FROM investory.positions pos
        JOIN investory.accounts a ON a.id = pos.account_id
        WHERE a.portfolio_id = p.id) AS position_count
FROM investory.app_users u
JOIN investory.portfolios p ON p.user_id = u.id
WHERE u.id = :user_id AND p.id = :portfolio_id;

SELECT CASE WHEN count(*) = 0 THEN 'CANONICAL_ASSET_LINKS_OK'
            ELSE 'CANONICAL_ASSET_LINKS_FAILED' END AS fixture_identity,
       count(*) AS mismatched_positions
FROM investory.positions pos
JOIN investory.accounts a ON a.id = pos.account_id
JOIN investory.assets asset ON asset.id = pos.asset_id
WHERE a.portfolio_id = :portfolio_id
  AND asset.symbol <> pos.source_asset_symbol;

SQL

fixture_mismatch_count="$(psql_demo -Atqc "
  SELECT count(*)
  FROM investory.positions pos
  JOIN investory.accounts a ON a.id = pos.account_id
  JOIN investory.assets asset ON asset.id = pos.asset_id
  WHERE a.portfolio_id = ${portfolio_id}
    AND asset.symbol <> pos.source_asset_symbol;
")"
if [[ "$fixture_mismatch_count" != "0" ]]; then
  echo "HappyInvestor fixture identity mismatch in portfolio ${portfolio_id}: ${fixture_mismatch_count} positions" >&2
  exit 1
fi

echo "HappyInvestor demo seed complete."
