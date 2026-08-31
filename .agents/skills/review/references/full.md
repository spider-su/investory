# Full change review

Perform a deeper correctness-oriented review of the requested diff/branch/commit/PR.

Understand the intended change first, then inspect enough surrounding code to evaluate
its behavior in context.

Review:

- correctness
- domain invariants
- failure behavior
- transaction boundaries
- cache invalidation
- concurrency where relevant
- DB query amplification/N+1 where introduced
- compatibility with existing data
- tests and test quality
- documentation/contracts changed by the implementation

For Investory, preserve fail-closed behavior for missing/stale financial inputs unless
the domain contract explicitly says otherwise.

Do not focus on naming/formatting unless it causes ambiguity or defects.

For every finding provide:

- severity: `BLOCKER | MAJOR | MINOR`
- file/location
- problem
- concrete failure scenario
- recommended correction

Separate proven defects from speculative risks.

## Output

# Full Review

Status: `PASS | PASS WITH CONCERNS | FAIL | NOT VERIFIED`

## Findings

Ordered by severity.

## Test Coverage

Identify:

- tests that directly prove the change
- missing regression tests
- integration/database coverage when required

## Compatibility

State impact on:

- clean database
- existing database/data
- existing imports/projections
- reconciliation/golden tests

Only include applicable items.

## Verdict

Output exactly one:

- `READY FOR RECON`
- `FIX BEFORE RECON`
- `NEEDS MORE VERIFICATION`

Do not modify code.
