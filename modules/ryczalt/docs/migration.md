# Migration notes

The replacement is staged so the existing `modules/accounting` implementation stays functional
and remains the reference/oracle during migration.

| Stage | Scope | Status |
| --- | --- | --- |
| 1 | foundation/domain | DONE |
| 2 | calculators/rules/fixtures | DONE |
| 3 | new persistence + migration | CURRENT |
| 4 | systematic parity/corrections | future |
| 5 | checkers/payment lifecycle | future |
| 6 | native integrations | future |
| 7 | API/application cutover | future |
| 8 | remove Accounting | future |

Stage 3 uses `RyczaltMigrationService` for a controlled, one-way import. It reads legacy tables only
while the import is explicitly run; `RyczaltPersistenceAdapter` never reads them. The old
`accounting` module remains independently available for comparison.

Stage 2 deliberately owns normalized calculator inputs rather than importing accounting DTOs or
fixtures. The test fixture `HappyInvestorStage2Fixture` is adapted from the existing
`HappyInvestorAccounting2026Facts` JSON and Jan-Aug reference rows; its provenance is recorded in
the source file. This verifies selected values without creating a production dependency on
`accounting` or `test-support`.

Stage 3 limitations and explicit non-goals:

- no source-document classification or legacy database adapter;
- no FX-rate acquisition or evidence persistence;
- no reconciliation, filing lifecycle, REST/UI, or application cutover;
- negative corrections must be normalized into the supplied VAT correction or revenue facts;
- the implemented ZUS and statutory rules are the explicit 2026 POC scenarios, not a general
  future-year rule engine.

Stage 3 intentionally does not migrate `accounting_reference_*` or other comparison-only tables.
Legacy booked PLN and ryczałt-rate fields are copied to canonical invoices. The current legacy POC
does not contain a complete historical FX quote for every booked value, so no FX rate is invented;
the booked PLN value remains the reproducible historical fact and the FX table is ready for explicit
future facts.
