# FX normalization contract

Canonical conversion direction used by SQL and Java:

- `fx_rate_to_base` means `amount_in_base = amount_in_source * fx_rate_to_base`.
- `resolve_fx_rate(valuation_date, source_currency, target_currency)` returns
  `fx_rate_to_target` with the same direction:
  `amount_in_target = amount_in_source * fx_rate_to_target`.

Resolution order:

1. same-currency (`rate = 1`),
2. exact-date `MARKET_DAILY`,
3. exact-date `IBKR_DAILY_REFERENCE`,
4. direct/inverse daily edge,
5. one-hop triangulation,
6. recent carry-forward,
7. pre-deployment historical interpolation.

`VALUATION` and `TRANSACTION` are separate resolver purposes. Valuation uses neutral
market/reference rates. Transaction accounting uses an exact `XTB_EXECUTION` or
`IBKR_EXECUTION` observation when the broker supplied one; execution spreads are not
used as neutral portfolio valuation rates.

Statuses:

- `OK`, `ESTIMATED`, and `SAME_CURRENCY` are usable, with estimated provenance visible,
- `CARRY_FORWARD` is exposed as a method and remains `OK` within the safety window,
- `STALE` and `MISSING_RATE` are not silently accepted.

Daily history begins at `app.fx.daily-history-start` (default `2026-08-01`). Before
that boundary, gaps between historical checkpoints may be linearly interpolated and
are marked `ESTIMATED`. After it, missing coverage is stale or missing rather than
being hidden by an old monthly checkpoint.

Fail-closed policy:

- authoritative reporting totals become `NULL` when any required FX row is not usable,
- diagnostic subtotals may still be exposed,
- aggregates expose `missing_fx_count` and `is_complete` where applicable.

