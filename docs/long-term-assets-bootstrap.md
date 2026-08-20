# Long-term asset bootstrap

This is an explicit developer/admin import for manual long-term assets. It is not a startup seed and it never writes accounting or portfolio history.

## Run

Use the sanitized example as a template:

```powershell
./mvnw -pl app -am spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--bootstrap-long-term-assets=app/src/main/resources/bootstrap/example-long-term-assets.json --dry-run"
./mvnw -pl app -am spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--bootstrap-long-term-assets=app/src/main/resources/bootstrap/example-long-term-assets.json"
```

The process exits after the command. `--dry-run` validates and reports counts/totals without writing.

## Format and behavior

The JSON document contains `portfolioId`, rental-tax policies, and assets. Assets use the existing domain enums and support cash-flow periods, valuation periods, bond-rate periods, bond details, and deposit details. See `app/src/main/resources/bootstrap/example-long-term-assets.json`.

Assets use `externalKey` as a stable identity scoped to a portfolio. Import behavior is **upsert**:

* matching assets are updated;
* new assets are created;
* matching child periods are updated by type and start date;
* omitted existing periods are retained, preserving history;
* the same file can be run repeatedly without duplicate rows.

Validation runs before writes in one transaction. Invalid portfolio, key, date, rate, currency, type, amount, maturity, or overlapping-period data rolls back the complete import.

## Reconciliation

The example contains five sanitized properties and two bonds. The property totals under current semantics are:

* value: `3,650,000 PLN`
* gross annual rent/parking: `172,200 PLN`
* operating expenses: `34,054 PLN`
* rental tax at `8.5%`: `14,637 PLN`
* net after tax: `123,509 PLN`

The Investment Profile converts native asset values and income to the portfolio base currency through the existing `CurrencyRateService`. No bootstrap-specific FX implementation is used.

Do not put credentials, addresses, tenant information, account identifiers, or URLs in bootstrap files. Treat input files as sensitive financial data and avoid logging their contents.
