# FX normalization contract

Canonical conversion direction used by SQL and Java:

- `fx_rate_to_base` means `amount_in_base = amount_in_source * fx_rate_to_base`.
- `resolve_fx_rate(valuation_date, source_currency, target_currency)` returns
  `fx_rate_to_target` with the same direction:
  `amount_in_target = amount_in_source * fx_rate_to_target`.

Resolution order:

1. same-currency (`rate = 1`),
2. latest `VALUATION` observation in `exchange_rates` on or before the requested date,
3. inverse of the latest such observation when only the reverse direction is stored.

An exact-date observation returns `OK` or `ESTIMATED`. An earlier observation returns
`CARRY_FORWARD`, retaining `source_rate_date` and `age_days`. There is no age cutoff. If no earlier
valuation observation exists, resolution returns `MISSING_RATE`; execution FX cannot rescue
valuation.

`VALUATION` and `TRANSACTION` are separate resolver purposes. Valuation uses neutral
market/reference rates. Transaction accounting uses an exact `XTB_EXECUTION` or
`IBKR_EXECUTION` observation when the broker supplied one, then falls back to the same latest
valuation observation and `CARRY_FORWARD` policy. Execution spreads are not used as neutral
portfolio valuation rates.

## FX data sources and refresh

All FX observations are stored in `exchange_rates` with one `rate` and an explicit `purpose`:

- `VALUATION` contains neutral market observations. The updater requests USD -> EUR and USD -> PLN,
  validates the complete response, and derives the other directed pairs through USD. Only actual
  provider dates are stored; carry-forward is resolved, never materialized as copied rows.
- `EXECUTION` contains `XTB_EXECUTION` and `IBKR_EXECUTION` observations. These rows retain broker
  timing and spread semantics for transaction accounting and are never used for portfolio valuation.

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
  Transaction rates prefer execution observations and fall back to the valuation resolver.
- FX observation refresh is application-driven. Both purposes use `CurrencyRateRepository`; the
  service writes the purpose explicitly.

Statuses:

- `OK`, `ESTIMATED`, `SAME_CURRENCY`, and `CARRY_FORWARD` are usable, with estimated or
  carried-forward provenance visible,
- when the requested date has no valuation observation on or before it, the result is `MISSING_RATE`,
- `MISSING_RATE` is not silently accepted.

`investory.fx_configuration.daily_history_start` remains the refresh coverage boundary, but it does
not limit resolver carry-forward or source selection.

The database value `investory.fx_configuration.daily_history_start` is the authoritative
runtime value for this boundary; application code must update it through the repository
contract, after the database coverage check succeeds. The database function
`fx_status_usable(status)` is defined in `V01.001__functions.sql` and is the SQL-side
status contract; Java mirrors the same usable statuses in
`CurrencyRateService.isUsableStatus(...)`.
Execution observations are transaction-only. Their `rate_date` must equal the
transaction's Europe/Warsaw local date, and `observed_at` must be no later than the
transaction timestamp. The Java transaction path passes the explicit Warsaw date to
the repository so the result does not depend on the database session timezone.

Fail-closed policy:

- authoritative reporting totals become `NULL` when any required FX row is not usable,
- diagnostic subtotals may still be exposed,
- aggregates expose `missing_fx_count` and `is_complete` where applicable.
