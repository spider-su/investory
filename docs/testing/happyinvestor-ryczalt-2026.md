# Happy Investor Ryczalt 2026 fixture

This is a synthetic operational fixture for the independent native Ryczalt path. It covers one
continuous business story from January through July 2026.

- The JDG is active, VAT-registered, and taxed at 12% for every month.
- The qualifying UoP runs from 2026-01-01 through 2026-02-28 only.
- January and February therefore use the UoP insurance reason; March through July use normal JDG
  compulsory social insurance.
- Every operational month has `salesCorrections = 0`; the July correction from historical
  migration evidence is deliberately absent.

Source facts live in
`test-support/src/main/resources/happyinvestor/ryczalt/happyinvestor-ryczalt-2026.json` and are
loaded by `HappyInvestorRyczaltFixtureLoader`. Expected calculation facts are stated separately in
`HappyInvestorRyczalt2026Expected`; they are not produced by the calculators under test.

The native database overlay is
`test-support/src/main/resources/db/snapshot/happyinvestor-ryczalt.sql`. Snapshot generation loads
it after the identity, whole-wealth, and broker overlays. Historical `accounting_reference_*` rows
and `modules/ryczalt/src/test/resources/certification/2026/expected-results.json` remain migration
certification evidence and are not operational expectations for this fixture.
