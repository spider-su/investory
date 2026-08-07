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
| C7 | secondary adapters | Ghostfolio, Yahoo export, Telegram, and other adapters agree when they expose the same metric. |

## Economic-truth checks

Internal consistency is insufficient. At minimum validate:

```text
period investment result ~= change in equity - external flows
```

with internal transfers/conversions handled according to portfolio scope.

Also check:

- no cash classification creates unexplained profit;
- open-position value uses the canonical current/historical pricing rule;
- result-only positions are not valued at full notional;
- broker truth is used as an independent oracle where the broker export provides it;
- missing/stale FX follows `docs/domain/fx-normalization.md`;
- dashboard and adapter totals trace back to the same reporting lineage.

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

## Current tooling

- `tools/ReconRunner.java` provides developer reconciliation support.
- Shared deterministic portfolio fixtures live under
  `src/test/java/com/example/demo/testsupport/portfolio`; read the package-local `README.md`.
- `src/test/manual/api.http` remains a manual API smoke surface.

Do not copy one developer's local account totals, machine paths, database addresses, or a one-time PASS
result into this document.

## Known gaps

Track implementation gaps in `ROADMAP.md`, not as a growing historical diary here. At the time of this
documentation reorganization, notable gaps include fuller IBKR source-to-ledger reconciliation,
automated dashboard checkpoint coverage, and making the complete golden pipeline a reliable CI gate.

When those ship, update tests/tooling, remove the roadmap item, and record completion in
`CHANGELOG.md`.
