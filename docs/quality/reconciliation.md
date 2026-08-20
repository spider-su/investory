# Reconciliation and pipeline validation

Goal: validate the Investory pipeline stage by stage and catch economically wrong results, not only
internally consistent ones.

This is the current validation contract. Historical investigation notes live under `docs/archive/`
and are not authoritative.

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
| C7 | secondary adapters | Exports, notifications, and other adapters agree when they expose the same metric. |

## Economic-truth checks

Internal consistency is insufficient. At minimum validate:

```text
period investment result ~= change in equity - external flows
```

with internal transfers/conversions handled according to portfolio scope.

Also check:

- no cash classification creates unexplained profit;
- account funding flow, performance flow, and portfolio flow remain distinct in daily performance
  diagnostics;
- open-position value uses the canonical current/historical pricing rule;
- result-only positions are not valued at full notional;
- broker truth is used as an independent oracle where the broker export provides it;
- missing/stale FX follows `docs/domain/fx-normalization.md`;
- dashboard and adapter totals trace back to the same reporting lineage.

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

The read-only application report now connects C0, C1, C2, and C5 to existing persisted import,
ledger, position, account-daily, and reporting evidence. C6 and C7 remain explicitly
`NOT_CHECKED`; QUICK mode also cannot prove external archive completeness without an archive
manifest. An empty result in an unexecuted checkpoint is not a pass. A report is `RECONCILED` only
after every required checkpoint has executed and passed. An executed failure produces
`UNRECONCILED`.

The application uses a reusable typed check engine. Checks execute in C0-C7 order, aggregate
uncapped counts while retaining capped detail rows, and report mode, execution time, and data-as-of
date. Check execution errors fail closed with an explicit issue. `BLOCKED` is only a presentation
status after the earliest original failure; it never replaces the original status.

Checkpoint and issue statuses are typed as `PASS`, `FAIL`, `REVIEW`, `BLOCKED`, and `NOT_CHECKED`.
Issues retain both original and effective status. Later executed failures/reviews are displayed as
`BLOCKED` after an earlier failure, without changing original status or aggregate counts.
`firstFailingCheckpoint` always uses original failures. Displayed issue rows are capped for page
performance; checkpoint totals and failure/review counts come from uncapped aggregate queries.

### Current valuation as-of status

`reporting_account_statistics_vs_daily_reconciliation` is a current valuation reconciliation.
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
- `reporting_import_provenance_issues` checks missing/wrong canonical links, orphan evidence, duplicate
  source identities, and source checksum drift. New orchestrated broker imports must produce no
  missing-link errors; legacy/manual rows remain explicitly nullable.
- Shared deterministic portfolio fixtures live under
  `test-support/src/main/java/com/smartbox/investory/testsupport/portfolio`; read
  `test-support/src/main/java/com/smartbox/investory/testsupport/README.md`.
- `test-support/test/manual/api.http` remains a manual API smoke surface.

Do not copy one developer's local account totals, machine paths, database addresses, or a one-time PASS
result into this document.

## Known gaps

Track implementation gaps in `ROADMAP.md`, not as a growing historical diary here. The reduced golden
corpus covers the supported IBKR C1 dimensions; full private-archive verification remains a release
check outside public CI.

The remaining release gap is enforcing the dedicated golden result as a repository
branch-protection/release requirement. Full private-archive verification also remains an operator
check outside public CI.
