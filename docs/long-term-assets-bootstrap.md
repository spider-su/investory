# Long-term asset bootstrap

This is an explicit developer/admin import for manual long-term assets. It is not a startup seed and it never writes accounting or portfolio history.

## Run

Use the sanitized example as a template:

```powershell
./mvnw -pl app -am spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--bootstrap-long-term-assets=app/src/main/resources/bootstrap/example-long-term-assets.json --dry-run"
./mvnw -pl app -am spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--bootstrap-long-term-assets=app/src/main/resources/bootstrap/example-long-term-assets.json"
```

The process exits after the command. `--dry-run` validates and reports counts/totals without writing.
Each real-estate total uses the cash-flow rows effective on that asset's `effectiveFrom` date; dated
historical and future rows are not added together.

## Format and behavior

The JSON document contains `portfolioId`, rental-tax policies, and assets. Legacy `cashFlows` input
is accepted only for real-estate assets and is converted transactionally into dated rental contracts
with contract terms. Other asset types cannot import cash-flow rows. Real estate supports historical
valuation periods. A cash reserve accepts zero or one current return period, and a bond requires one
current rate period plus its bond details. Deposit details include a required maturity date. See
`app/src/main/resources/bootstrap/example-long-term-assets.json`.

Assets use `externalKey` as a stable identity scoped to a portfolio. Real-estate assets may provide
`taxBase` and `rentalTaxPaidByTenant`; expense cash-flow entries may provide `paidByTenant`. Omitted
ownership flags use the compatibility defaults: administration fees and utilities are tenant-paid;
other expenses and rental tax are landlord-paid. Import behavior is **upsert**:

* matching assets are updated;
* new assets are created;
* matching real-estate valuation periods are updated by start date and omitted periods are retained;
* cash-reserve and bond rate rows are replaced by their single imported current assumption;
* the same file can be run repeatedly without duplicate rows.

Real-estate cash-flow boundaries are converted to bootstrap-owned rental contracts. A later import
fully replaces those contracts, including deleting them when `cashFlows` is empty. It never overwrites
manual or pre-ownership contracts. Asset type and currency are immutable after initial import.

Validation runs before writes in one transaction. Invalid portfolio, key, date, rate, currency, type,
amount, maturity, ownership, or overlapping-period data rolls back the complete import. Rental
economics are `gross income - landlord-paid expenses - effective rental tax`; a missing tax base means
zero rental tax. The effective policy is selected by portfolio and calculation date, with an 8.5%
fallback when no policy applies.

Asset type is immutable after creation. Bootstrap updates preserve the existing lifecycle history;
they do not silently convert an archived asset or discard archive/reactivation periods. Rental input
and real-estate valuation periods are checked for overlap. Cash and bond rates follow the single
current-assumption replacement rule described above.

Lifecycle history uses inclusive date periods and the application `Clock`. If several lifecycle
transitions occur on one date, the date-only model collapses them to that date and never writes an
invalid range; the current active flag remains authoritative for current lists, while historical
lookups use the persisted lifecycle periods.

## Reconciliation

The example contains five sanitized properties and two bonds. The property totals under current semantics are:

* value: `3,650,000 PLN`
* gross annual rent/parking: `172,200 PLN`
* landlord-paid operating expenses and rental tax are calculated from each property's effective cash-flow ownership, tax base, and policy date;
* tenant-paid administration fees and utilities are excluded from landlord operating expenses;
* the persisted import and dry-run use the same canonical economics.

The Investment Profile converts native asset values and income to the portfolio base currency through the existing `CurrencyRateService`. No bootstrap-specific FX implementation is used.

Do not put credentials, addresses, tenant information, account identifiers, or URLs in bootstrap files. Treat input files as sensitive financial data and avoid logging their contents.
