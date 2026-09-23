# Lifecycle and corrections

Periods use `OPEN -> DIRTY -> CALCULATED -> FROZEN`. `PAID` belongs to obligation settlement,
not the period lifecycle. A relevant input change moves a
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

The native REST endpoint `POST /api/profiles/{profileId}/accounting/periods/{month}/calculate`
runs the RYCZALT, VAT, ZUS, and obligation cycle for a month. It creates a missing period as
`OPEN`, persists all three current calculations, creates or refreshes obligations, then marks the
period `CALCULATED`. The request contains normalized PLN inputs; source-to-input aggregation remains
an application workflow to be completed before automatic monthly calculation.

The REST bridge passes freeze/reopen operations to this lifecycle service for native periods.
Legacy-backed periods remain delegated until their facts are migrated and verified.
