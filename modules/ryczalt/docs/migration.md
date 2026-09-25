# Migration notes

The replacement is staged so the existing `modules/accounting` implementation stays functional
and remains the reference/oracle during migration.

| Stage | Scope | Status |
| --- | --- | --- |
| 1 | foundation/domain | DONE |
| 2 | calculators/rules/fixtures | DONE |
| 3 | new persistence + migration | DONE |
| 4 | systematic parity/corrections/lifecycle | DONE |
| 5 | checkers/payment lifecycle | DONE |
| 6 | native integrations | IN PROGRESS |
| 7 | API/application cutover bridge | IN PROGRESS |
| 8 | remove Accounting | future |

The one-way legacy import utility and old aggregate persistence adapter have been removed. Native
query services read only `ryczalt_*` tables. The old `accounting` module and historical tables
remain only for planned cleanup.

The authoritative historical calculation source is
`accounting_calculation_snapshot.payload`, not `accounting_poc_obligation` or a newly executed
calculator. `ryczalt_calculation` receives the `ryczalt`, `vat`, and `zus` snapshot sections with
`input_fingerprint` equal to `calculation_hash`; `ryczalt_obligation` receives the persisted
`calculatedTax`, `calculatedVat`, and `totalZus` values and points to the corresponding native
calculation. Snapshot provenance is recorded as `ACCOUNTING_CALCULATION_SNAPSHOT`. Tax-input rows
remain calculation inputs and never become obligations. Payment matches, FX rates, corrections, and
audit events are intentionally not fabricated during migration.

The strict `AccountingToRyczaltMigrationReconciliationIT` Testcontainers test derives expected
cardinalities from the source rows, performs source-to-native and native-to-source anti-joins, and
compares persisted monetary values with numeric equality. Run it with:

```text
./mvnw -pl app -am -Dit.test=AccountingToRyczaltMigrationReconciliationIT verify
```

Stage 2 deliberately owns normalized calculator inputs rather than importing accounting DTOs or
fixtures. The calculator-level fixture `February2026CalculatorFixture` is a small normalized
February example. The complete operational story is owned by
`test-support/.../happyinvestor/ryczalt`; this verifies selected values without creating a
production dependency on `accounting` or `test-support`.

Stage 4 keeps the importer and old accounting module independent. `ParityReport` and
`ParityDifference` provide the diagnostic result contract for comparing revenue/cost, booked PLN,
rate buckets, deductions, tax, VAT, ZUS, and obligations across certified periods. The repository-
wide old-versus-new execution is currently blocked by the pre-existing accounting compilation error
documented in the handoff; no parity result is reported as green until that path runs.

Stage 4 limitations and explicit non-goals:

- no source-document classification or legacy database adapter;
- no FX-rate acquisition or evidence persistence;
- no reconciliation, filing lifecycle, REST/UI, or application cutover;
- negative corrections must be normalized into the supplied VAT correction or revenue facts;
- the implemented ZUS and statutory rules are the explicit 2026 POC scenarios, not a general
  future-year rule engine.

Stage 5 adds settlement only over canonical persisted obligations and transactions. Native NBP
historical FX acquisition is now available through `FxRateSourcePort` and `RyczaltFxRateService`.
Bank, eZUS, KSeF, filing, and external verification integrations remain incomplete.

The NBP path is deliberately source-first and idempotent: the service looks up a persisted rate on
or before the policy-selected prior business day, calls NBP only when no fact exists, persists the
effective date/rate/provider reference using `BigDecimal`, and never overwrites an existing fact.
No calculator calls NBP.

The Stage 7 application boundary is split cleanly. `RyczaltAccountingApi` and
`RyczaltAccountingFacade` own native query and lifecycle operations in the Ryczalt module.
`RyczaltAccountingRestController` calls that API only. Legacy app controllers, adapters, and bridge
have been removed. `modules/ryczalt` no longer depends on `modules/accounting`; historical legacy
tables remain only for the later database cleanup.

The current endpoint and dependency inventory is maintained in
`modules/ryczalt/docs/cutover-audit.md`. It distinguishes historical migration SQL from normal
runtime dependencies and is the source for the next implementation backlog.

Stage 3 intentionally does not migrate `accounting_reference_*` or other comparison-only tables.
Legacy booked PLN and ryczałt-rate fields are copied to canonical invoices. The current legacy POC
does not contain a complete historical FX quote for every booked value, so no FX rate is invented;
the booked PLN value remains the reproducible historical fact and the FX table is ready for explicit
future facts.
