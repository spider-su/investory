---
name: import
description: Diagnose Investory broker-import health and performance. Use explicitly as $import, $import full, or $import perf.
---

# Investory Import

Diagnostic analysis only.

Do not modify production code, migrations, database data, source files, snapshots,
or expected test values unless the user separately asks for a fix.

Interpret the invocation as:

- `$import` or `$import basic` -> BASIC
- `$import full` -> FULL
- `$import perf` -> PERF

If the requested mode is unclear, show the supported forms and stop.

Use the current repository implementation, current runtime/database state, supplied
broker files, and supplied logs as applicable.

## BASIC

Goal: quickly determine whether the latest/current broker import is healthy and where
the pipeline stopped if it is not.

Check, where available:

- import history/status
- rows total/applied/failed
- parser/importer failure
- persistence failure
- derived-data refresh
- projection readiness
- reconciliation refresh/readiness
- obvious FX `STALE` / `MISSING_RATE`
- obvious missing/stale price inputs

Distinguish:

- source/parser failure
- broker data applied but derived pipeline not ready
- fully completed import

Do not perform broad root-cause analysis in BASIC mode.

### BASIC output

# Import

Status: `PASS | WARN | FAIL | NOT VERIFIED`

| Stage | Status | Note |
|---|---|---|

Only include stages actually checked.

Then output exactly one:

- `Next: RUN $recon`
- `Next: RUN $import full`
- `Next: FIX ENVIRONMENT / MISSING DATA`

Keep the report short.

## FULL

Read `references/full.md` and follow it.

## PERF

Read `references/perf.md` and follow it.
