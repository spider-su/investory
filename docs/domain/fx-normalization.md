# FX normalization contract

Canonical conversion direction used by SQL and Java:

- `fx_rate_to_base` means `amount_in_base = amount_in_source * fx_rate_to_base`.
- `resolve_fx_rate(valuation_date, source_currency, target_currency)` returns
  `fx_rate_to_target` with the same direction:
  `amount_in_target = amount_in_source * fx_rate_to_target`.

Resolution order:

1. same-currency (`rate = 1`),
2. exact `(rate_date, base, to_currency)` row in `fx_daily_rates`.

There is no valuation fallback outside `fx_daily_rates`. An absent row returns `MISSING_RATE` with
a null rate; execution FX cannot rescue valuation.

`VALUATION` and `TRANSACTION` are separate resolver purposes. Valuation uses neutral
market/reference rates. Transaction accounting uses an exact `XTB_EXECUTION` or
`IBKR_EXECUTION` observation when the broker supplied one; execution spreads are not
used as neutral portfolio valuation rates.

## FX data sources and refresh

Neutral daily FX data is fetched through the configured NBP integration and stored only in
`fx_daily_rates`. The updater requests USD -> EUR and USD -> PLN, validates the complete response,
and derives the other directed pairs through USD. The provider's date is stored as `rate_date`.

`exchange_rates` is execution-only: it stores `XTB_EXECUTION` and `IBKR_EXECUTION` observations.
Those rows retain broker timing and spread semantics for transaction accounting and are never used
for neutral portfolio valuation.

Before persistence, a neutral provider response must be non-empty and contain all
required quotes. Each quote must have a USD base, a non-USD target, positive finite
rate, effective date equal to the requested date, a non-future provider date, and
complete metadata. Provider dates must agree across the response, and conflicting
duplicate targets reject the complete refresh. Invalid input does not partially write
FX rows and does not advance `daily_history_start`.

The refresh is application-driven: after valid observations are persisted, the
application flushes them, advances `daily_history_start` only when neutral daily
coverage is supported, refreshes dependent application views, and invalidates
calculation caches.

Ownership and execution:

- PostgreSQL `resolve_fx_rate(...)` remains the canonical resolver for SQL reporting
  and reconciliation. It returns provenance and status; SQL callers do not rebuild
  the currency graph themselves.
- Java `CurrencyRateService` explicitly loads the resolved valuation matrix through
  `CurrencyRateRepository` once per valuation date, caches it, converts with
  `BigDecimal`, and throws `FxRateUnavailableException` when the result is not usable.
  Transaction execution rates are resolved separately from execution observations.
- FX observation refresh is application-driven. The updater persists neutral observations through
  `DailyFxRateRepository`; execution observations use `CurrencyRateRepository`.

Statuses:

- `OK`, `ESTIMATED`, `SAME_CURRENCY`, and `CARRY_FORWARD` are usable, with estimated or
  carried-forward provenance visible,
- when the requested date has no canonical daily row, the result is `MISSING_RATE`,
- `MISSING_RATE` is not silently accepted.

`investory.fx_configuration.daily_history_start` remains the refresh coverage boundary, but it no longer
changes resolver source selection. Missing exact daily coverage is always `MISSING_RATE`; it is never
hidden by an old monthly checkpoint.

The database value `investory.fx_configuration.daily_history_start` is the authoritative
runtime value for this boundary; application code must update it through the repository
contract, after the database coverage check succeeds. The database function
`fx_status_usable(status)` is defined in `V01.001__functions.sql` and is the SQL-side
status contract; Java mirrors the same three usable statuses in
`CurrencyRateService.isUsableStatus(...)`.
Execution observations are transaction-only. Their `rate_date` must equal the
transaction's Europe/Warsaw local date, and `observed_at` must be no later than the
transaction timestamp. The Java transaction path passes the explicit Warsaw date to
the repository so the result does not depend on the database session timezone.

Fail-closed policy:

- authoritative reporting totals become `NULL` when any required FX row is not usable,
- diagnostic subtotals may still be exposed,
- aggregates expose `missing_fx_count` and `is_complete` where applicable.
