# Lifecycle and corrections

Periods use `OPEN -> DIRTY -> CALCULATED -> PAID -> FROZEN`. A relevant input change moves a
non-frozen period to `DIRTY` and invalidates only dependent calculation types through
`CalculationInvalidationPolicy`.

`FROZEN` is load-only. Canonical fact writes and invalidation fail with
`FrozenPeriodMutationException`. Reopening requires a reason and writes an audit event; a later
calculation creates a new revision instead of deleting the frozen result.

`RyczaltCorrectionService` records the original period, affected entity, reason, actor, and optional
correction period. It does not rewrite the original facts. Payment matching, filing, integrations,
and UI/REST cutover remain outside this stage.

Payment matches and obligation settlement are separate from calculation state. A frozen period cannot
be matched, unmatched, or have its obligation status changed. Reopen/correction is required first.

The REST bridge passes lock/reopen operations to this lifecycle service for Ryczalt-backed periods.
Legacy-backed periods remain delegated until their facts are migrated and verified.
