# Migration review

Review Flyway/database changes with emphasis on both fresh installs and already-migrated
databases.

Inspect the migration together with affected schema/JPA/repository/service code.

Check:

- migration ordering/version
- clean-database behavior
- upgrade behavior from the previous released schema
- existing data compatibility
- null/default/backfill behavior
- constraints/indexes
- enum/type compatibility
- JPA validation compatibility
- trigger/function/view dependencies
- transaction/locking risk
- deterministic behavior
- rollback/recovery implications where relevant

For data migrations, verify that the transformation does not silently reinterpret
financial history.

If a migration changes a semantic boundary or configuration invariant, verify both:

- the seed/bootstrap state for a fresh database
- the transition of existing databases

Run or identify the relevant Flyway/PostgreSQL integration tests where practical.

A timeout is not a pass. Report it as incomplete verification.

## Output

# Migration Review

Status: `PASS | PASS WITH CONCERNS | FAIL | NOT VERIFIED`

## Findings

For each finding:

- severity
- migration/file
- fresh-DB impact
- existing-DB impact
- evidence
- recommended fix

## Verification

State:

- Flyway migration result
- schema/JPA validation result
- relevant integration tests
- anything not verified

## Verdict

Output exactly one:

- `MIGRATION READY`
- `FIX MIGRATION`
- `NEEDS MORE VERIFICATION`

Do not change the migration.
