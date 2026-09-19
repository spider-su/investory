# Ryczalt persistence

Stage 3 adds a separate relational model under the `investory` schema. Runtime Ryczalt loading uses
only these tables:

```text
Profile
  └── ryczalt_period
        ├── ryczalt_invoice
        ├── ryczalt_transaction
        ├── ryczalt_obligation
        └── ryczalt_calculation

Historical/supporting data:
  ├── ryczalt_fx_rate
  └── ryczalt_source_reference
```

## Tables and ownership

`ryczalt_period` is one profile-owned `YearMonth` and lifecycle status. Its unique key is
`(profile_id, period_year, period_month)`. Invoices have explicit `INCOME` or `COST` direction;
transactions and obligations point to the period and repeat `profile_id` so repository queries and
database constraints cannot accidentally cross profile boundaries.

`ryczalt_calculation` stores one current/stale/frozen result per profile, period, and calculation
type (`RYCZALT`, `VAT`, `ZUS`). `result_json` is an explicit JSON document produced by the application,
not Java serialization. `rule_version`, `calculator_version`, `calculated_at`, and
`input_fingerprint` make the stored result auditable. Fingerprint generation is deliberately left to
the calculation orchestration stage; Stage 3 persists the field but does not invent an entity-
serialization fingerprint.

`ryczalt_fx_rate` stores provider, currency, date, positive `NUMERIC(19,8)` rate, and fetch time.
`ryczalt_source_reference` preserves the legacy source table and row identity without adding
provider-specific fields to canonical domain objects.

## Precision

Money is stored as `NUMERIC(19,4)`, tax rates as `NUMERIC(7,4)`, and FX rates as `NUMERIC(19,8)`.
Storage precision is not statutory rounding. Stage-2 `RoundingPolicy` remains authoritative for
2-decimal contribution/deduction operations and whole-PLN tax/VAT settlement.

## JPA boundary

JPA entities and repositories live under `persistence`; canonical records remain in `domain` and
are assembled by `RyczaltDomainMapper` through `RyczaltPersistenceAdapter`. Loading does not run
calculators. Saving is explicit and does not rely on aggregate-wide cascade magic.

## Frozen periods

The schema retains calculated and frozen timestamps plus persisted calculation results. A later
orchestration/checker stage must prevent silent recalculation or mutation of frozen periods. Payment
matching and reopening workflow are intentionally outside Stage 3.
