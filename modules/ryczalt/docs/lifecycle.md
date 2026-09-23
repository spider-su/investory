# Lifecycle and corrections

Public periods use `OPEN | FROZEN`. Internal transitional states still include `DIRTY` and
`CALCULATED` for persisted compatibility; `PAID` belongs to obligation settlement, not the public
period lifecycle. A relevant input change moves a
non-frozen period to `DIRTY` and invalidates only dependent calculation types through
`CalculationInvalidationPolicy`.

`FROZEN` is load-only. Canonical fact writes and invalidation fail with
`FrozenPeriodMutationException`. Reopening requires a reason and writes an audit event; a later
calculation creates a new revision instead of deleting the frozen result.

`RyczaltCorrectionService` records the original period, affected entity, reason, actor, and optional correction period. It does not rewrite the original facts. Filing/JPK remains outside the native accounting scope.

Payment matches and obligation settlement are separate from calculation state. A frozen period cannot
be matched, unmatched, or have its obligation status changed. Reopen/correction is required first.

The native REST endpoint `POST /api/profiles/{profileId}/accounting/periods/{month}/calculate`
runs the RYCZALT, VAT, ZUS, and obligation cycle for a month. It creates a missing period as
`OPEN`, aggregates approved persisted invoices and saved month settings, persists all three current
calculations, creates or refreshes obligations, marks the period calculated internally, and settles
compatible transactions already imported for the month.

Native REST exposes freeze/reopen through this lifecycle service. There is no legacy Accounting fallback in the active accounting path.
