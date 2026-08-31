---
name: review
description: Review recent Investory changes for correctness and regression risk. Use explicitly as $review, $review full, or $review migration.
---

# Investory Review

Review changes; do not implement fixes unless separately requested.

Interpret the invocation as:

- `$review` or `$review basic` -> BASIC
- `$review full` -> FULL
- `$review migration` -> MIGRATION

If a diff, branch, commit, or PR is specified, review that scope.
Otherwise review the current/recent change available in the working context.

Prioritize correctness over style.

## BASIC

Goal: decide whether the change plausibly solves its stated problem without introducing
an obvious regression.

Inspect:

- changed behavior versus stated goal
- correctness-sensitive logic
- tests added/updated
- missing high-value test cases
- accidental semantic weakening
- obvious unrelated changes

For Investory financial logic pay particular attention to:

- money/quantity precision
- FX and price provenance/status
- import readiness
- projection/accounting semantics
- reconciliation behavior

Do not perform broad repository redesign.

### BASIC output

# Review

Status: `PASS | PASS WITH CONCERNS | FAIL | NOT VERIFIED`

## Findings

List findings by severity:

- `BLOCKER`
- `MAJOR`
- `MINOR`

If none, state `No material findings.`

## Tests

State what passed, failed, timed out, or was not run.

## Verdict

Output exactly one:

- `READY FOR RECON`
- `FIX BEFORE RECON`
- `NEEDS MORE VERIFICATION`

## FULL

Read `references/full.md` and follow it.

## MIGRATION

Read `references/migration.md` and follow it.
