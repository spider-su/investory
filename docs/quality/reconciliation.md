# Reconciliation and pipeline validation

Goal: validate the Investory pipeline stage by stage and catch economically wrong results, not only
internally consistent ones.

This is the current validation contract. Historical investigation notes live under `docs/archive/`
and are not authoritative.

For live-data acceptance, establish portfolio identity from current ownership and broker import
provenance. The deterministic Happy Investor portfolio (fixture identity `portfolio_id = 2`) is
synthetic test data and has no broker-import provenance by design; its rows are not evidence about
real portfolios. Validate that story through its source fixture, generated snapshot, and fixture
contracts instead. See the [Happy Investor fixture guide](../../test-support/src/main/java/com/smartbox/investory/testsupport/happyinvestor/README.md).

## Pipeline checkpoints

```text
files
  -> import
  -> positions / cash ledger
  -> prices + FX
  -> account_daily
  -> reporting views/materialized views
  -> dashboard
  -> secondary adapters
```

| ID | Boundary | Core invariant |
| --- | --- | --- |
| C0 | files -> import history | Every supplied source file is accounted for; completed imports have no unexplained failed rows. |
| C1 | files -> cash ledger | Counts, amounts, operation classes, currencies, and date coverage reconcile to the source model. |
| C2 | ledger -> positions | Open/closed quantities, direction, settlement model, and broker reconstruction are correct. |
| C3 | prices + FX | Required valuation inputs exist and satisfy canonical price/FX contracts. |
| C4 | `account_daily` | Equity, cash, market value, flows, and investment result reconcile for each account/date. |
| C5 | reporting layers | Monthly/portfolio summaries equal the lower-level projection and pass economic-truth checks. |
| C6 | dashboard | Displayed values equal their documented reporting sources within rounding. |
| C7 | secondary adapters | The current implementation checks the Yahoo export adapter; future adapters must add their own explicit evidence. |

## Economic-truth checks

Internal consistency is insufficient. At minimum validate:

```text
period investment result ~= change in equity - external flows
```

with internal transfers/conversions handled according to portfolio scope.

Also check:

- no cash classification creates unexplained profit;
- result-only settlement compares the stored position result net of separately recorded swap and
  commission with matching `CLOSE_TRADE` plus `ROLLOVER` ledger cash;
- fixed-income `BOND_REDEMPTION` settlement cash is matched to the related closed cash-settled
  position and is never treated as an external contribution;
- account funding flow, performance flow, and portfolio flow remain distinct in daily performance
  diagnostics;
- open-position value uses the canonical current/historical pricing rule;
- trade-settlement carrying value uses the normalized daily price selected for the prior valuation
  date, including its selected price currency; raw interpolated observations with a future right
  endpoint must not be selected for that date;
- result-only positions are not valued at full notional;
- broker truth is used as an independent oracle where the broker export provides it;
- missing/stale FX follows `docs/domain/fx-normalization.md`;
- dashboard and adapter totals trace back to the same reporting lineage.

Temporal anomaly evidence is a review layer over C3 and C4. `recon_v_temporal_anomaly`
combines gap-aware FX, observed asset-price, and flow-adjusted account checks. Its `ERROR`
rows indicate strong structural evidence (for example, reciprocal FX inconsistency or an
isolated price spike); they do not authorize historical data repair. Large movements across
long observation gaps are not classified as short-period spikes, and account movement checks
remove deposits, withdrawals, income, expenses, and realized profit before applying the
unexplained-movement threshold.

Manual price-anomaly dispositions are recorded in
`investory.reconciliation_price_anomaly_reviews` with the exact observation fingerprint,
rationale, and evidence URL. Confirmed market moves and corrected source prices remain visible
in the underlying price history. A manually accepted alternate-listing issue records the
reviewer-approved exception without changing source prices. All three dispositions are omitted
from the combined active temporal-anomaly view. A changed observation no longer matches its
disposition and returns to active review.

Account movement dispositions are append-only records in
`investory.reconciliation_account_movement_reviews`. The fingerprint covers the prior/current
`account_daily` values used by the anomaly. `EXPLAINED` and `CORRECTED` dispositions suppress only
the exact matching event; `STILL_UNDER_REVIEW` remains active. A later disposition supersedes the
prior decision for that event without deleting its audit history. When the event inputs change,
the fingerprint changes and the movement returns to active review until reviewed again. Every disposition
requires a rationale and evidence reference; it does not modify accounting facts.

Known non-accounting price-source conditions are evidence-quality classifications, not valuation
failures: trade observations, interpolated prices, alternate listings, and stale carry-forward
prices remain visible for traceability. Trade-observation and stale-carry-forward selections are
informational when the position is otherwise valuatable; missing price, missing FX, zero-price,
and impossible quantity/value combinations remain errors. Manual weekly prices and corporate-action
resets remain reviewable continuity signals.

Price temporal checks compare only quote-quality observations: exact listing closes, verified
alternate listings, scaled exact listings, percent-of-par closes, and manually accepted prices.
Trade-derived and stale carry-forward rows remain available in price history and valuation, but do
not create market-price movement or currency-switch findings. Corporate-action price adjustments
remain reviewable when present in eligible quote history.

## Numeric comparison contract

Reconciliation parameters are stored in `investory.reconciliation_parameters` and read by the
reconciliation views. Monetary status decisions use full precision:

```text
difference = actual - expected
effective_tolerance = max(absolute_tolerance,
                          relative_tolerance * max(abs(expected), abs(actual)))
PASS when abs(difference) <= effective_tolerance
```

`reconciliation_reporting_scale` controls display rounding only; rounded values must never decide
PASS/FAIL. Generic numeric tolerances are separate from named domain anomaly thresholds such as
carrying-value, market-bridge, reorganization, and price-jump rules.

The current default monetary policy is:

- C4 market-value/equity comparison: absolute tolerance `400` or relative tolerance `0.02`.
- C4 unrealized-profit comparison: uses the same market-value tolerance scale.
- C4 cash comparison: absolute tolerance `5` with the shared relative tolerance.
- C6 dashboard-fallback unrealized comparison: absolute tolerance `100` with the shared relative
  tolerance.
- C1 account-flow comparison includes internal-transfer legs; portfolio-flow reporting remains
  external-flow scoped.

These values are configuration data, not display rounding. A deployment that changes them must
update `investory.reconciliation_parameters` through the approved migration/configuration path
and refresh dependent reconciliation materialized views.

Classify reconciliation constants before changing them:

- numeric precision/tolerance belongs in `reconciliation_parameters` and uses the shared comparison
  rule (for example, monetary and quantity tolerances);
- domain anomaly thresholds stay separately named because they describe suspicious economic events,
  not insignificant numeric noise (for example, sale-vs-carrying-value outliers over 20%);
- display limits and data-quality ages are operational/business rules, not numeric comparison
  tolerances.

## Regression classes

Keep explicit regression coverage for defect classes already observed in this project:

- incomplete source-file import;
- cross-account currency conversion misclassified as investment flow/profit;
- inception-period double counting;
- inconsistent current price sources across projection and allocation surfaces;
- stale or missing FX treated as valid converted value.

## Automation levels

- **L1 fast**: parser, classifier, and calculation unit/slice tests.
- **L2 integration**: PostgreSQL-backed import/projection/reporting tests using deterministic fixtures.
- **L3 reconciliation**: developer/pre-release comparison against broker files and a local database.

The testing environment and migration/snapshot split are defined in
`docs/development/testing.md`.

Reconciliation has a separate SQL boundary. Production calculations live in
`V01.005__portfolio_views.sql`; independent reconstruction and diagnostics live in
`V01.006__reconciliation_views.sql`; persisted audit tables, triggers, and reports live in
`V01.007__persisted_system_audit.sql`. Normal reporting refresh uses an explicit production-MV order
and never refreshes the trade-settlement reconciliation MV. Reconciliation refresh is explicit/on
demand. Estimated FX is usable for authoritative conversion; stale and missing FX fail closed.

Account quality has three distinct states:

- `RECONCILED` — no true accounting or required-data-availability failure.
- `REVIEW` — an explicit semantic, as-of, or no-validation-snapshot review without a confirmed
  accounting failure.
- `UNRECONCILED` — a true material reconciliation failure or missing required valuation input.

Semantic review rows remain visible. They are not suppressed or converted into accounting failures
solely to improve quality counts.

### Application reconciliation report coverage

The application endpoint evaluates current database/current-valuation evidence for the requested
portfolio. Its `portfolioId` is an ownership and calculation boundary, not navigation-only context.
Administrative system-wide diagnostics are separate operational workflows and are not the normal UI
report source.
`GOLDEN` and full `ARCHIVE` reconciliation are release workflows implemented by `GoldenRebuildIT`
and the private archive tooling, not application REST modes.

The read-only application report connects C0, C1, C2, C5, and C6 to persisted import, ledger,
position, reporting, and dashboard-fallback evidence. C7 compares the persisted Yahoo export
snapshot with the current payload for the requested portfolio. The adapter rebuilds the payload,
then compares its row count and fingerprint with `yahoo_export_state`: a missing snapshot is
`REVIEW`, a stale snapshot is `FAIL`, and a matching snapshot is `PASS`. Without a valid portfolio
context the adapter cannot prove freshness and fails closed. The current-state report still cannot
prove external archive completeness without an archive manifest. An empty result in an unexecuted
checkpoint is not a pass. A report is `RECONCILED` only after every required checkpoint has
executed and passed. An executed failure produces `UNRECONCILED`.

C0 evaluates incomplete imports only on the latest `(provider, file_sha256)` attempt; superseded
failed attempts remain immutable audit evidence but do not permanently fail a successful reprocess.
For source-row provenance, an observation is accounted for when it directly links to a canonical
cash operation or position, or when the same provider-scoped `logical_row_sha256` is linked by an
equivalent observation from another overlapping file. This hash includes the source section, sheet,
broker record ID, occurrence, and normalized row content while excluding file and import identity.
For XTB cash operations, a changed row-content hash can still resolve to a canonical cash operation
when the provider, account scope, cash-operation sheet, broker operation ID, and occurrence match.
The canonical operation ID must also equal the broker operation ID. A broker ID by itself is not
enough because IDs can repeat across accounts. Rows with no directly or equivalently linked
canonical fact remain C0 failures.
C1 reconstructs cash-flow differences from `account_daily` and `normalized_cash_operation_flows`.
Flow components use account-level flow amounts converted to portfolio base currency so internal
transfer legs are included in the same units as `account_daily`; same-currency cash-delta comparison
is limited to accounts whose native currency matches portfolio base currency. Operation timestamps
use the explicit Europe/Warsaw reporting date, independent of PostgreSQL session timezone, and
`reconciliation_values_match` at full precision. Rounded reporting columns are evidence for display
only.

The application uses a reusable typed check engine. Checks execute in C0-C7 order. Database checks
use one bounded query per checkpoint with windowed uncapped counts and at most 250 detail rows.
Repository-backed C3/C4 checks use the same windowed-count pattern. Reports include execution time
and current valuation date. Check execution errors fail closed with an explicit issue.
`BLOCKED` is only a presentation
status after the earliest original failure; it never replaces the original status.

Checkpoint and issue statuses are typed as `PASS`, `FAIL`, `REVIEW`, `BLOCKED`, and `NOT_CHECKED`.
Issues retain both original and effective status. Later executed failures/reviews are displayed as
`BLOCKED` after an earlier failure, without changing original status or aggregate counts.
`firstFailingCheckpoint` always uses original failures. Displayed issue rows are capped for page
performance; checkpoint totals and failure/review counts come from uncapped aggregate queries.

### Current valuation as-of status

`recon_v_account_statistics_vs_daily` is a current valuation reconciliation.
`VALUATION_ASOF_DIFFERENCE` intentionally includes a latest `account_daily` snapshot that is not
`CURRENT_DATE`: Friday/weekend, holiday, pre-close, and refresh-lag views require review but are
not monetary mismatches. Do not reinterpret it as a latest-completed-market-session reconciliation
without an explicit contract change and regression coverage.

## Retirement planning boundary

Retirement simulation and planning are outside accounting reconciliation. They read the canonical portfolio
and long-term-asset inputs but never write accounting facts. Their current financial and lifecycle contracts
are [`docs/domain/retirement-simulation.md`](../domain/retirement-simulation.md) and
[`docs/domain/planning-timeline.md`](../domain/planning-timeline.md).

## Current tooling

- `tools/ReconRunner.java` enforces C0 completeness for all supplied IBKR/XTB files, implements XTB C1 checks, and provides the developer-facing aggregate IBKR cash conservation check.
- `GoldenRebuildIT` starts a fresh Testcontainers PostgreSQL database, applies Flyway, imports the
  reduced broker corpus, loads deterministic FX data, rebuilds projections, and emits a JSON
  `READY` or `NOT_READY` report. Its IBKR C1 contract checks operation, currency, business date,
  row count, and signed Net Amount. The dedicated CI workflow job runs it with no live market/FX step.
- `recon_v_import_provenance_issues` checks missing/wrong canonical links, orphan evidence, duplicate
  source identities, and source checksum drift. New orchestrated broker imports must produce no
  missing-link errors; legacy/manual rows remain explicitly nullable.
- Shared deterministic portfolio fixtures live under
  `test-support/src/main/java/com/smartbox/investory/testsupport`; read the package-local `README.md`.
- `app/src/test/http/api.http` remains a manual API smoke surface.

Do not copy one developer's local account totals, machine paths, database addresses, or a one-time PASS
result into this document.

## Known gaps

Track implementation gaps in `ROADMAP.md`, not as a growing historical diary here. The reduced golden
corpus covers the supported IBKR C1 dimensions; full private-archive verification remains a release
check outside public CI.

The remaining release gap is enforcing the dedicated golden result as a repository
branch-protection/release requirement. Full private-archive verification also remains an operator
check outside public CI.
