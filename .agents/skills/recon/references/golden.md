# Golden validation

## GOLDEN

Run Investory's deterministic golden rebuild using the existing golden test
implementation.

Discover the current test command from the repository rather than assuming a stale
Maven/Gradle invocation.

The golden check must remain:

- isolated from the user's current database
- based on checked-in deterministic fixtures
- independent of live market-data providers
- independent of live FX providers
- read-only with respect to production/user data

Use the existing golden reconciliation test, currently represented by
`GoldenRebuildIT`, unless the repository has replaced it with a newer authoritative
golden entry point.

Do not update fixtures, hashes, snapshots, expected totals, tolerances, or assertions
to make the test pass.

If it fails, report the first meaningful contract failures and their likely subsystem.
Do not automatically switch to FULL mode.

### Output

# Golden

Status: `PASS | FAIL | NOT RUN`

Report:

- test/entry point used
- duration if available
- failed contracts
- relevant evidence

If successful, end with:

`GOLDEN REBUILD: PASS`

---

## GOLDEN LIVE

Goal: validate the current real Investory dataset as strongly as practical using the
current reconciliation contract, broker source files, local database, and independent
reconciliation mechanisms.

This is not the deterministic fixture golden test.

Never compare live account values against fixture-specific account IDs or hard-coded
fixture totals.

Do not mutate, rebuild, clean, reseed, or otherwise change the live database.

### Procedure

1. Locate the current reconciliation contract and tooling.
2. Identify the current local database configuration without exposing secrets.
3. Locate broker source files when configured/available.
4. Run existing live/L3 reconciliation tooling, including `ReconRunner` when applicable.
5. Run independent reconciliation views/audits applicable to the current data.
6. Check economic truth where implemented:
   - equity change versus external flows and investment result
   - internal transfers remain portfolio-neutral where required
   - no cash classification creates unexplained profit
   - canonical price and FX rules are respected
   - stale/missing valuation inputs fail closed
7. Verify reporting totals against lower-level projection where tooling exists.

Report unsupported or unimplemented checkpoints as `NOT VERIFIED`.
Never manufacture validation.

Do not perform broad forensic analysis unless needed to identify what failed.
Use `$recon full` for detailed root-cause analysis.

### Output

# Golden Live

Status: `PASS | WARN | FAIL | NOT VERIFIED`

## Pipeline

| Checkpoint | Status | Difference | Note |
|---|---|---:|---|

Use the repository's current checkpoint names where available.

## Failures

Show concrete account/symbol/date/value evidence for each failure.

## Coverage

State which checkpoints were actually verified and which remain unavailable or
unimplemented.

## Verdict

Output exactly one:

- `LIVE GOLDEN PASS`
- `LIVE GOLDEN PASS WITH WARNINGS`
- `LIVE GOLDEN FAIL`
- `LIVE GOLDEN INCOMPLETE`
