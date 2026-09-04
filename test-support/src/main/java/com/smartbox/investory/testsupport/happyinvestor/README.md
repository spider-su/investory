# Happy Investor test scenario

`HappyInvestorScenario.create()` is the complete cross-domain reference fixture. It composes the
existing portfolio builders and exposes semantic account, asset, position, and ledger handles.
F1-F4 layer their boundary facts over this story: `HappyInvestorImportFacts` is the source subset;
`HappyInvestorDailyFacts`, `HappyInvestorReportingFacts`, and `HappyInvestorDashboardFacts` are
independent expected checkpoints. F1 owns import/accounting, F2 owns `account_daily`, F3 owns
reporting/schema, and F4 owns dashboard rendering.

Single source of truth: the two overlays `db/snapshot/happyinvestor-common.sql` and
`db/snapshot/happyinvestor-broker.sql` (aggregated by `db/snapshot/canonical-data.sql`, generated
into `db/snapshot/schema.sql`, loaded by `FastDatabase`) are the authoritative persisted fixture for
every canonical FastDatabase IT test. Tests named as synthetic boundary tests are intentionally
outside this story and must not use the HappyInvestor name for their fixture identity.
`HappyInvestorScenario.create()` is the in-memory object-graph mirror of the same story; its ledger
reproduces the canonical `cash_operations` rows (funding, withdrawals, correlated FX transfers, and
the IBKR commission, dividend + withholding tax, and Treasury free-funds interest + tax rows
7017-7021). Keep the two in lockstep and add independently specified expected values only in the
`HappyInvestor*Facts` classes, never by calling production code.
F5 refreshes canonical market quotes through the provider port, F6 refreshes canonical USD/EUR/PLN
rates, and F7 reads the persisted canonical asset detail through service and REST. F15 checks the
same financial state after reconciliation rebuild; F16 executes a persisted due integration job at
the provider boundary; F17 dispatches a durable event through formatting to a test delivery adapter.
These flows use canonical financial data. Minimal synthetic rows remain allowed only for isolated
infrastructure error/lease/constraint contracts. `AccountDailyProjectionBoundaryIT` is the explicit
synthetic projection-math boundary and must stay clearly named and outside the story fixture.
Use it for reporting, reconciliation, import, UI, E2E, and regression tests. Keep focused unit
tests on the smaller `PortfolioScenarios` fixtures. Crypto, options, commodities, and malformed
price cases stay separate extensions.

## Snapshot layering (single story, three layers)

The persisted fixture is built in layers so every non-golden IT and the golden path share one story:

- **Base** = Flyway migrations only (structure + reference data: currencies, assets master, FX and
  price history, reconciliation params).
- **Common overlay** (`db/snapshot/happyinvestor-common.sql`) = broker-agnostic Happy Investor data:
  identity, long-term/whole-wealth assets, rental, tax, plan, planning, and the pinned price cache.
- **Broker overlay** (`db/snapshot/happyinvestor-broker.sql`) = the imported ledger
  (`cash_operations` + `positions`) for the four canonical accounts, including the MSFT open lot
  (unrealized P/L derived from market price, stored profit 0) and the NATGAS closed `RESULT_ONLY`
  CFD lot with its `CLOSE_TRADE` + `ROLLOVER` realized trade cash and `SWAP` fee.

Non-golden canonical ITs load `base + common + broker` (baked into `schema.sql` by
`scripts/update-test-db-snapshot.sh`). `canonical-data.sql` is now a thin `\ir` aggregator of the two
overlays. `GoldenRebuildIT` tells the same canonical broker story — the only difference is that it loads
the broker facts by importing the reduced broker fixture files instead of the SQL overlay, which is
what keeps the importer under regression (Treasury full-call lifecycle, business-date boundaries, C1
source-to-ledger conservation, VHYD subaccount rebooking allocated to an existing XTB account,
cash-only funding). Golden tests may add importer-only accounts and rows for those edge cases; they
are a separate golden layer, not extra canonical HappyInvestor accounts. `HappyInvestorSchemaCanonicalTest`
guards the overlays and the generated snapshot against drift.


Identity is migration-backed: `Happy Investor`, `Happy Investor Portfolio`, PLN, Europe/Warsaw,
2024-07-31 through 2025-12-31; account IDs are IBKR `17959259`, XTB USD `51499241`, XTB PLN
`51551301`, and cash-only XTB EUR `51548444`. The WIG20 ETF is `ETFBW20TR.PL`; the seeded
Treasury identities are `US91282CKB62` and `US91282CRC72`. Happy Investor must consume the migration FX and price
history rather than synthetic curves. Independent financial happy-path fixtures are prohibited in
F1-F4. Add source facts here and independently specified expected facts at the owning boundary;
never calculate expectations through production valuation, FX, projection, or reporting code.

F8-F14 use the same non-investment facts: IDs 9401-9404 are the PLN cash reserve, Apartment A,
Apartment B, and Family Car. The notes-only Family Car remains visible but is excluded from
financial calculations. The calculated subtotal is therefore 950000 including the reserve at the
2024-08-01 as-of date. The canonical Treasury and reserve deposit add 60000, so the calculated
profile total is 1010000.
Apartment A rents for
3200/month and Apartment B has 2800/month through 2025-06-30, then 3000/month. Calendar-2025
collected gross rent is 73200. The 2025-12-31 boundary-date annualized gross economics are 74400;
historical snapshots intentionally use that boundary measure. The persisted rental tax bases are
3200/month and 3000/month, supporting boundary-date tax 6324 at 8.5% and net income 68076.
Apartment A's own annual tax is 3264. These facts live in
`HappyInvestorLongTermFacts`. The persisted planning identity is `Happy Investor Plan`, with its
independent assumptions in `HappyInvestorPlanFacts`; F11 joins this state to the F1-F4 investment
facts, and F12-F14 consume the same plan identity. The scenario's tax assumptions are not a full
Polish tax-law model.

The 1010000 profile amount includes the 50000 reserve; the long-term capital subtotal excluding
that reserve is 960000. `schema.sql` is generated from the migrations and `canonical-data.sql`,
not hand-maintained. `HappyInvestorSchemaCanonicalTest` checks that the generated snapshot retains
the canonical asset, rental, tax, plan, and initial planning rows. Investment capital must be
defined from persisted F1-F4 ledger rows before a nonzero planning baseline is written; zero is
not a valid substitute for that calculation.

The persisted plan and in-memory `HappyInvestorSimulationSpec` represent the same reviewed
scenario. The `Happy Investor Plan` starts in 2024 at age 40, retires at 60, ends at age 85,
contributes 12000/year (1000/month), and has a 2025 baseline. The in-memory specification derives
its 45-year horizon, monthly contribution, inflation, and reserve from those canonical facts; keep
it aligned with the persisted plan and its frozen asset payload. For the current Conservative
overlay, expected nominal rates are inflation 3.5%, fixed income 3%, equity 5%, rental growth 4.5%,
and spending growth 5%, calculated from the persisted base rates and the overlay deltas in
`SimulationScenarioSettings`.

## Fact ownership and verification

Use `HappyInvestorTestData` for identity, account, asset, FX, tax-rate, and ledger input facts.
Use `HappyInvestorLongTermFacts` for long-term values and clearly scoped rental outputs; use
`HappyInvestorPlanFacts`, `HappyInvestorMarketDataFacts`, `HappyInvestorImportFacts`, and the
boundary fact classes for their owning flows. Expected values stay independent of production
valuation, FX, projection, and reporting code.

The alignment guard is `HappyInvestorSchemaCanonicalTest`; it checks the generated snapshot and
both SQL overlays. Run it together with `HappyInvestorScenarioTest`, then run the owning ITs.
The pinned `2025-01-01`/`STOOQ` price cache is the base snapshot. The `2026-08-20`/`TWELVE_DATA`
values belong to the provider-refresh flow and must not be confused with the initial cache.

## F1-F17 flow map

```text
F1  File -> accounting                 F5  Market-price refresh
F2  Accounting -> account_daily        F6  FX refresh
F3  account_daily -> reporting         F7  Asset detail
F4  Reporting -> dashboard                 | -> valuation/reporting inputs

F8  Long-term assets                    F15 Reconciliation <- canonical financial state
F9  Rental economics                    F16 Integration jobs -> F5/F6/provider updates
F10 Bond/deposit economics              F17 Notifications <- jobs/reconciliation/events
F11 Unified profile
F12 Plan persistence
F13 Retirement simulation
F14 Planning timeline
```
