# Migration notes

The replacement is staged so the existing `modules/accounting` implementation stays functional
and remains the reference/oracle during migration.

| Stage | Scope | Status |
| --- | --- | --- |
| 1 | domain foundation | complete |
| 2 | pure calculators and versioned rules | complete for the supported POC cases |
| 3 | adapters from legacy Accounting DB facts | future |
| 4 | full parity and discrepancy classification | future |
| 5+ | persistence, migration, checkers, integrations, and application cutover | future |

Stage 2 deliberately owns normalized calculator inputs rather than importing accounting DTOs or
fixtures. The test fixture `HappyInvestorStage2Fixture` is adapted from the existing
`HappyInvestorAccounting2026Facts` JSON and Jan-Aug reference rows; its provenance is recorded in
the source file. This verifies selected values without creating a production dependency on
`accounting` or `test-support`.

Known limitations before Stage 3:

- no source-document classification or legacy database adapter;
- no FX-rate acquisition or evidence persistence;
- no reconciliation, filing lifecycle, REST/UI, or application cutover;
- negative corrections must be normalized into the supplied VAT correction or revenue facts;
- the implemented ZUS and statutory rules are the explicit 2026 POC scenarios, not a general
  future-year rule engine.
