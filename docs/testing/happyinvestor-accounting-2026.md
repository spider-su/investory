# HappyInvestor Accounting 2026 POC

This is an independent operational Accounting story. It is separate from the investment
HappyInvestor fixtures and from the historical Accounting reconstruction/golden tests.

The source file is
`test-support/src/main/resources/happyinvestor/accounting/happyinvestor-accounting-2026.json`.
It contains anonymized source/business facts only: profile context, effective periods, sales and
expense documents, bank evidence, and one authority-evidence example. It does not contain derived
monthly totals or readiness results.

The intended pipeline is:

```text
HappyInvestor Accounting fixture
        -> source/business facts
        -> normal Accounting ingestion
        -> canonical Accounting facts
        -> AccountingFactService
        -> filing / payments / reconciliation / lifecycle
```

The first fixture scope is February 2026 (clean baseline), June 2026 (EUR and document-level
purchase VAT), and July 2026 (the separate correction and visible 0.04 PLN ZUS payment difference).
The July correction is a known POC exception; it is not a generic correction engine.

The fixture provenance is `HAPPYINVESTOR_ACCOUNTING_2026`. It is test data, not production KSeF,
bank, or authority evidence.
