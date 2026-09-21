# `.60` migration certification

This is a bounded certification summary for the `.60` dataset/run. It is evidence for the data
migration path, not a claim that arbitrary future datasets or the native calculators are certified.

## Data migration certification

```text
Periods                  PASS
Income invoices          PASS
Cost invoices            PASS
Bank transactions        PASS
Calculations             PASS
Obligations              PASS
Provenance               PASS

missing source links     0
value mismatches         0
duplicate native refs    0

calculations             24
calculation provenance   24
obligations              24
obligation provenance    24
transactions             316
transaction provenance   316
```

The certified bank migration grew native rows from 49 to 316. It had 316 provenance links, zero
missing source links, zero value mismatches, and zero duplicate native references. Repeated legacy
bank references use the deterministic suffix rule `#legacy-bank-<source-id>`.

No payment matches existed in the certified run. None were fabricated.

## Calculation oracle

The Jan–Aug expected values are preserved in
`src/test/resources/certification/2026/expected-results.json`. The full independently verified
calculator input fixture is not currently available in this repository, so an executable
calculation-certification test is intentionally not claimed. The existing
`AccountingToRyczaltMigrationReconciliationIT` remains the synthetic migration/idempotency test;
it must not be replaced by this oracle.
