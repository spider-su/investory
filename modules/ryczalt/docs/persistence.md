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

`ryczalt_calculation` stores a revision history and one current result per profile, period, and calculation
type (`RYCZALT`, `VAT`, `ZUS`). `result_json` is an explicit JSON document produced by the application,
not Java serialization. `rule_version`, `calculator_version`, `calculated_at`, and
`input_fingerprint` make the stored result auditable. Stage 4 fingerprints semantic inputs and
retains prior revisions when a calculation changes.

`ryczalt_fx_rate` stores provider, currency, date, positive `NUMERIC(19,8)` rate, and fetch time.
`ryczalt_source_reference` preserves the legacy source table and row identity without adding
provider-specific fields to canonical domain objects.

`ryczalt_payment_match` stores positive payment allocations, match type, and creation time. Composite
profile/entity foreign keys prevent an obligation from one profile being matched to a transaction
from another. A unique obligation/transaction pair makes automatic settlement idempotent.

REST/application code must use the Ryczalt application port rather than repositories. The current
legacy adapter may read through `AccountingUserApi`, but it must not bypass that public API or expose
legacy snapshots to the new domain.

## Precision

Money is stored as `NUMERIC(19,4)`, tax rates as `NUMERIC(7,4)`, and FX rates as `NUMERIC(19,8)`.
Storage precision is not statutory rounding. Stage-2 `RoundingPolicy` remains authoritative for
2-decimal contribution/deduction operations and whole-PLN tax/VAT settlement.

## JPA boundary

JPA entities and repositories live under `persistence`; canonical records remain in `domain` and
are assembled by `RyczaltDomainMapper` through `RyczaltPersistenceAdapter`. Loading does not run
calculators. Saving is explicit and does not rely on aggregate-wide cascade magic.

## Frozen periods

The schema retains calculated and frozen timestamps plus persisted calculation results. Frozen writes
fail at the persistence boundary. Reopening and correction are explicit, reasoned, and audited.
