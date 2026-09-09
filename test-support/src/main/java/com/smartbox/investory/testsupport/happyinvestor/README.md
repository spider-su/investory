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
  (`cash_operations` + `positions`) for the four canonical financial-story accounts, including the MSFT open lot
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


The four canonical financial-story accounts are IBKR USD, XTB USD, XTB PLN, and XTB EUR cash-only;
they participate in ledger, position, and reporting expectations. Additional seeded Happy Investor
accounts are allowed for UI/account-management realism, but must remain empty unless deliberately
added to the canonical story. They are not silently included in canonical parity assertions.

Identity is fixture-backed: user ID `2`, portfolio ID `2`, `Happy Investor`, `Happy Investor Portfolio`,
PLN, Europe/Warsaw, 2024-07-31 through 2025-12-31. Internal account IDs are IBKR `2017959259`,
XTB USD `2051499241`, XTB PLN `2051551301`, and cash-only XTB EUR `2051548444`; their broker
external IDs remain `17959259`, `51499241`, `51551301`, and `51548444`. The WIG20 ETF is `ETFBW20TR.PL`; the seeded
Treasury identities are `US91282CKB62` and `US91282CRC72`. The original `US91282CKB62` is owned from
`2024-07-31`, matures/redempts on `2026-02-28`, and returns principal `10000`. That principal is reinvested on
`2026-03-01` into `US91282CRC72` (`United States Treasury 4 3/8 07/31/33`, coupon `4.375%`, maturity `2033-07-31`).
The old bond remains historical and has zero forward income on and after maturity; the new bond contributes net
annual income `354.375` under the existing 19% tax rule. Happy Investor must consume the migration FX and price
history rather than synthetic curves. Independent financial happy-path fixtures are prohibited in
F1-F4. Add source facts here and independently specified expected facts at the owning boundary;
never calculate expectations through production valuation, FX, projection, or reporting code.

Dashboard and Profile **unit/IT test-fixture** facts intentionally use the fixed checkpoint
`2025-12-31`. This fixed date is correct for deterministic disposable tests. For a live browser,
when no trades, cash operations, market-price updates, or FX updates occur after the checkpoint,
the facts are observation-frozen and remain valid beyond that calendar date. Asset prices use
the latest canonical observation at or before that date from the pinned `2025-01-01` price cache;
the cache records `2024-12-31` market observations. FX uses the latest canonical rate at or before
the same checkpoint: USD/PLN `3.6016` and EUR/USD `1.173562`. A market-price update changes open
position value and return/yield metrics; an FX update also changes reporting-currency values. New
trades or operations change source/activity facts and period totals. The complete broker source inventory
and independent boundary arithmetic live in `HappyInvestorBrokerFacts`, not in rendered Dashboard
or Profile output. Treasury prices are percent-of-par, so `10000 * 98.81 / 100 * FX` is required.

Dashboard account-scope invariant: whole-portfolio Dashboard balance/equity and cash include all
four accounts, including the cash-only EUR account `2051548444`. The Dashboard Accounts popup
intentionally lists only visible non-cash-only investment accounts and its `Total` is therefore a
three-account investment subtotal, not whole-portfolio equity. The expected difference is the
cash-only account's remaining `-2000 EUR` converted to PLN; do not classify this scope difference
as a reporting or calculation defect.

Profile income-base rule: validate `Income base` against
the HappyInvestor story and the canonical [`portfolio accounting`](../../../../../../../../../docs/domain/portfolio-accounting.md)
and [`reporting pipeline`](../../../../../../../../../docs/architecture/reporting-pipeline.md) contracts, including
applicable external deposits and withdrawals. The owning reporting contract uses start-of-year
market value plus month-weighted external flows. The fixed story fact and any derived reporting
value must come from the owning independent fact/test contract; never replace that contract with
the current balance or UI label interpretation.

F8-F14 use the same non-investment facts: IDs 9401-9404 are the PLN cash reserve, Apartment A,
Apartment B, and Family Car. The notes-only Family Car remains visible but is excluded from
financial calculations. The calculated subtotal is therefore 950000 including the reserve at the
2024-08-01 as-of date. The canonical Treasury and interest-bearing cash reserve add 60000, so the calculated
profile total is 1010000.
Apartment A rents for
3200/month and Apartment B has 2800/month through 2025-06-30, then 3000/month. Calendar-2025
collected gross rent is 73200; historical snapshots use this calendar measure. The 2025-12-31
boundary-date annualized gross economics are 74400. The persisted annual rental-tax bases
are 3200 and 3000, supporting annual tax 527 and boundary-date net annual income 73873 at 8.5%.
Apartment A's own annual tax is 272. These facts live in
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
The pinned `2025-01-01`/`STOOQ` price cache is the base snapshot. The `2026-08-20`/`YAHOO_FINANCE`
values belong to the provider-refresh flow and must not be confused with the initial cache.

## F1-F17 flow map

```text
F1  File -> accounting                 F5  Market-price refresh
F2  Accounting -> account_daily        F6  FX refresh
F3  account_daily -> reporting         F7  Asset detail
F4  Reporting -> dashboard                 | -> valuation/reporting inputs

F8  Long-term assets                    F15 Reconciliation <- canonical financial state
F9  Rental economics                    F16 Integration jobs -> F5/F6/provider updates
F10 Bond/cash-reserve economics         F17 Notifications <- jobs/reconciliation/events
F11 Unified profile
F12 Plan persistence
F13 Retirement simulation
F14 Planning timeline
```
