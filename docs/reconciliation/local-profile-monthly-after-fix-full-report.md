# Reconciliation / materialized-view report

Generated: 2026-08-13T19:30:44.057464200+02:00

Material discrepancy rule: `abs(diff) > max($20, 1% * max(abs(expected), abs(actual)))`. Rows below this rule are summarized as noise and not listed as findings.

## Intended dependency order

1. canonical prices and FX
2. reconstructed position facts
3. reconstructed cash facts
4. account/day reconciliation
5. monthly and portfolio reporting
6. trade-settlement and secondary diagnostics

## 1. Prices and FX

### FX conversion status

```text
conversion_status	count
ESTIMATED	6
```

### FX data-quality issues

```text
(0 rows)
```

### FX consistency issues

```text
issue_code	count
RECIPROCAL_MISMATCH	72
```

### Asset price contract issues

```text
issue_code	count
EXTREME_SAME_DATE_SOURCE_DISAGREEMENT	7
PRICE_CURRENCY_MISMATCH	2633
PRICE_SCALE_MAPPING_MISMATCH	2038
```

## 2. Reconstructed position facts

### Position MV row count

```text
count
42047
```

### Position reconstruction status

```text
reconstruction_status	count
PASS	37263
WARN	4784
```

### Position valuation material mismatches

```text
QUERY ERROR: ERROR: relation "investory.recon_v_position_valuation_validation" does not exist
  Position: 57
```

### Position identity issues

```text
(0 rows)
```

### Position currency validation issues

```text
(0 rows)
```

### Duplicate position lots

```text
count
0
```

## 3. Reconstructed cash facts

### Cash MV row count

```text
count
5869
```

### Cash reconstruction status

```text
status	count
PASS	5869
```

### Cash-flow material gaps

```text
count	coalesce
0	0
```

### Cash-flow internal-transfer scope reviews

```text
count	coalesce
282	10624
```

### Cash-flow incomplete rows

```text
count
0
```

## 4. Account/day reconciliation

### Account/day MV row count

```text
count
5869
```

### Account/day status

```text
status	severity	count
FAIL	INFO	15
FAIL	WARN	207
PASS	INFO	3792
WARN	WARN	1855
```

### Account/day material mismatches

```text
count	coalesce
78	6713
```

### Account statistics vs latest daily

```text
reconciliation_status	count
OK	6
VALUE_MISMATCH	5
```

## 5. Monthly and portfolio reporting

### Account monthly MV row count

```text
count
204
```

### Monthly profit reconciliation

```text
reconciliation_status	count
OK	204
```

### Monthly profit material mismatches

```text
count	coalesce
0	0
```

### Portfolio service fallback reconciliation

```text
fallback_reconciliation_status	count
REVIEW	1
```

### Portfolio validation summary

```text
status	count
FAIL	222
PASS	2309
WARN	1020
```

### Portfolio data quality

```text
total_accounts	reconciled_accounts	unreconciled_accounts	total_open_positions	priced_open_positions	missing_price_count	stale_price_count	proxy_price_count	estimated_price_count	missing_fx_count	ambiguous_cost_basis_currency_count	excluded_position_count	unclassified_cash_operation_count	latest_broker_reconciliation_at	latest_import_at	latest_price_date	latest_fx_date	latest_reporting_refresh_at	quality_state
11	7	4	37	37	0	0	0	0	0	0	0	0	2026-08-12 14:57:40.285393	2026-08-12 14:57:40.285393	2026-08-13	2026-07-31	2026-08-13 11:23:07.940664	CRITICAL
```

## 6. Settlement and secondary diagnostics

### Trade settlement status

```text
reconciliation_status	anomaly_code	count
INCOMPLETE	VALUATION_RECONSTRUCTION_FAILED	159
PASS	OK	2627
REVIEW	MIXED_SETTLEMENT_MODEL	2
REVIEW	RESULT_ONLY_CASH_MISMATCH	9
REVIEW	SALE_VS_CARRYING_VALUE_OUTLIER	6
REVIEW	UNCLASSIFIED_SETTLEMENT_MODEL	1
```

### Realized-result completeness

```text
is_complete	count
true	1574
```

### Non-USD closed-trade anomalies

```text
anomaly_code	count
MISSING_PREVIOUS_POSITION	47
OK	1219
```

### Unsupported transaction states

```text
(0 rows)
```

### Timezone-naive column count

```text
count
1
```

## Material findings for follow-up

### Position valuation rows

```text
QUERY ERROR: ERROR: relation "investory.recon_v_position_valuation_validation" does not exist
  Position: 57
```

### Cash-flow rows

```text
count	coalesce
0	0
```

### Account/day rows

```text
count	coalesce
78	6713
```

### Portfolio fallback rows

```text
portfolio_id	base_currency	canonical_realized_profit	fallback_realized_profit	canonical_unrealized_profit	fallback_unrealized_profit	fallback_position_fx_missing_count	canonical_dividends	fallback_dividends	canonical_interest	fallback_interest	realized_profit_difference	unrealized_profit_difference	dividends_difference	interest_difference	missing_fx_count	is_complete	fallback_reconciliation_status
1	USD	12747.435566165706287652669696364660397484731610000000	13195.756570285822427384364271372362513815397020000000	2251.275987352063905865983379843809823962194631000000	495.0272401100000000000000000000	0	4903.217168405876439732808701428035798200690960000000	4903.217168405876439732808701428035798200690960000000	671.046856208209234264299356473571141741915693000000	671.046856208209234264299356473571141741915693000000	448.321004120116139731694575007702116330665410000000	-1756.248747242063905865983379843809823962194631000000	0E-48	0E-48	0	true	REVIEW
```

### Material validation failures

```text
valuation_date	account_id	reconciliation_failures	maximum_market_value_difference	maximum_equity_difference	status
2025-11-03	51499241	1	4244	4244	FAIL
2025-11-04	51499241	1	4068	4068	FAIL
2025-11-04	51822121	1	4068	4068	FAIL
2025-11-19	51499241	1	3682	3682	FAIL
2025-11-20	51499241	1	3376	3376	FAIL
2025-10-22	51822121	1	3249	3249	FAIL
2025-10-25	51822121	1	3249	3249	FAIL
2025-10-23	51822121	1	3249	3249	FAIL
2025-10-24	51822121	1	3249	3249	FAIL
2025-11-23	51499241	1	2973	2973	FAIL
2025-11-21	51499241	1	2973	2973	FAIL
2025-11-22	51499241	1	2973	2973	FAIL
2025-11-18	51499241	1	2948	2948	FAIL
2025-11-24	51499241	1	2906	2906	FAIL
2025-10-16	51993106	1	2787	2787	FAIL
2025-11-25	51499241	1	2364	2364	FAIL
2025-11-05	51499241	1	2171	2171	FAIL
2025-11-03	51822121	1	2122	2122	FAIL
2025-11-08	51499241	1	2074	2074	FAIL
2025-11-11	51499241	1	2074	2074	FAIL
2025-11-10	51499241	1	2074	2074	FAIL
2025-11-09	51499241	1	2074	2074	FAIL
2025-11-06	51499241	1	2074	2074	FAIL
2025-11-07	51499241	1	2074	2074	FAIL
2025-10-25	51993106	1	2060	2060	FAIL
2025-10-24	51993106	1	2060	2060	FAIL
2025-10-26	51993106	1	2060	2060	FAIL
2025-10-15	51993106	1	2034	2034	FAIL
2025-10-20	51993106	1	1603	1603	FAIL
2025-10-23	51993106	1	1498	1498	FAIL
2025-10-21	51993106	1	1497	1497	FAIL
2025-10-22	51993106	1	1468	1468	FAIL
2025-10-15	51822121	1	1154	1154	FAIL
2025-10-13	51822121	1	1154	1154	FAIL
2025-10-20	51822121	1	1154	1154	FAIL
2025-10-17	51822121	1	1154	1154	FAIL
2025-10-14	51822121	1	1154	1154	FAIL
2025-10-19	51822121	1	1154	1154	FAIL
2025-10-18	51822121	1	1154	1154	FAIL
2025-10-16	51822121	1	1154	1154	FAIL
2025-02-26	51499241	1	1101	1101	FAIL
2025-10-21	51822121	1	1086	1086	FAIL
2025-10-13	51499241	1	888	888	FAIL
2025-10-28	51993106	1	825	825	FAIL
2025-10-18	51993106	1	800	800	FAIL
2025-10-17	51993106	1	800	800	FAIL
2025-10-19	51993106	1	800	800	FAIL
2025-10-27	51993106	1	761	761	FAIL
2025-02-28	51499241	1	746	746	FAIL
2025-10-10	51499241	1	745	745	FAIL
```

