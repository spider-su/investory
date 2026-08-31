# Golden verification gate

## Result

The reduced real-broker corpus now has a clean, deterministic verification gate:

```text
GOLDEN REBUILD: READY
checks: 17 PASS, 0 FAIL, 0 SKIP
```

The clean Testcontainers database applies the current migration chain, imports the IBKR and XTB
fixtures, rebuilds reporting, and runs the reconciliation error scan. No live market or FX calls
are used.

## Coverage added

- IBKR C1 now compares source and ledger by operation, currency, business date, row count, and
  signed `Net Amount`.
- Changed-window/partially-overlapping import input gets a new checksum and a new import batch;
  the prior batch remains unchanged.
- The corpus README defines the minimal-fixture growth rule: each confirmed accounting/import defect
  adds one smallest deterministic fixture and semantic checkpoint.
- The full private broker archive is documented as a separate clean-database pre-release check,
  outside public CI.

## Verification

```text
ImportHistoryOrchestratorServiceTest: 14 passed
GoldenRebuildIT: 2 tests, 0 failures, 0 errors
Golden duration: about 152 seconds on the local clean-container run
```

The golden helper scripts and CI golden job disable Maven incremental compilation for reproducible
local/CI source discovery.

## Remaining release action

Repository branch protection must require the existing golden job before merge/release. This is a
repository-host configuration step, not a source-code change. The full private archive remains a
release-operator check and is not committed or uploaded to public CI.
