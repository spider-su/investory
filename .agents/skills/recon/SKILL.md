---
name: recon
description: Investory reconciliation command. Use explicitly as $recon, $recon full, $recon golden, or $recon golden live.
---

# Investory Recon

Diagnostic validation only.

Do not modify production code, migrations, database data, reconciliation tolerances,
snapshots, fixtures, or expected golden values.

Interpret the invocation as:

- `$recon` or `$recon basic` -> BASIC
- `$recon full` -> FULL
- `$recon golden` -> GOLDEN
- `$recon golden live` -> GOLDEN_LIVE

If the requested mode is unclear, show the four supported forms and stop.

Use the current repository implementation and documentation as the source of truth.
Prefer existing reconciliation tooling, views, services, tests, and configured tolerances
over inventing new accounting rules.

## BASIC

Goal: quickly determine whether the current Investory state is reconciled well enough
to continue to the deterministic golden check.

Inspect only enough repository context to discover the available reconciliation checks.
Do not perform broad architecture analysis.

Run the existing lightweight/current-state checks that are reasonably available.

Check at minimum, where implemented:

- import/readiness failures
- non-zero reconciliation residuals
- cash/account inconsistencies
- position inconsistencies
- account/portfolio total inconsistencies
- FX `STALE` or `MISSING_RATE`
- missing or materially stale valuation prices

Use Investory's configured tolerances. Do not invent new tolerances.

Do not deeply investigate every failure in BASIC mode.

### BASIC output

# Recon

Status: `PASS | WARN | FAIL | NOT VERIFIED`

| Check | Status | Residual | Note |
|---|---|---:|---|

Only include checks actually executed.

Then output exactly one next step:

- `Next: SAFE FOR GOLDEN`
- `Next: RUN $recon full`
- `Next: FIX ENVIRONMENT / MISSING DATA`

Keep the report short.

## FULL

Read `references/full.md` and follow it.

## GOLDEN

Read `references/golden.md` and execute the GOLDEN section.

## GOLDEN_LIVE

Read `references/golden.md` and execute the GOLDEN LIVE section.
